package guru.interlis.transformer.engine;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.expr.ReferenceValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.CreatePlan;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.RefPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.CanonicalValue;
import guru.interlis.transformer.state.DeferredReference;
import guru.interlis.transformer.state.DuplicateTargetOidException;
import guru.interlis.transformer.state.OidGenerationRequest;
import guru.interlis.transformer.state.OidGenerationService;
import guru.interlis.transformer.state.OidStrategy;
import guru.interlis.transformer.state.ParentChildIndex;
import guru.interlis.transformer.state.ReferenceIndex;
import guru.interlis.transformer.state.SourceObjectKey;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.state.SourceReferenceSelector;
import guru.interlis.transformer.state.StateStore;
import guru.interlis.transformer.state.TargetObjectKey;
import guru.interlis.transformer.state.TargetReference;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TargetObjectFactory {

    private final ExpressionEngine expressionEngine;
    private final OidGenerationService oidGenerationService;
    private final AssignmentExecutionService assignmentExecutionService;
    private final BagTransformationService bagTransformationService;

    public TargetObjectFactory(
            ExpressionEngine expressionEngine,
            OidGenerationService oidGenerationService,
            AssignmentExecutionService assignmentExecutionService,
            BagTransformationService bagTransformationService) {
        this.expressionEngine = expressionEngine;
        this.oidGenerationService = oidGenerationService;
        this.assignmentExecutionService = assignmentExecutionService;
        this.bagTransformationService = bagTransformationService;
    }

    public int createTarget(
            RulePlan rule,
            SourcePlan matchedSource,
            SourceRecord driverRecord,
            EvalContext evalCtx,
            TransformPlan plan,
            ObjectCreationContext ctx) {
        Iom_jObject target = buildTargetObject(rule, matchedSource, driverRecord, plan, ctx);
        OutputBinding output = plan.outputsById().get(rule.outputId());
        TypeSystemFacade targetTs = output != null ? output.typeSystem() : null;
        assignmentExecutionService.execute(rule.assignments(), evalCtx, target, targetTs);

        String targetBasket = addToBasket(rule.outputId(), driverRecord, target, plan, ctx);
        registerMapping(rule.outputId(), rule.ruleId(), driverRecord, target, targetBasket, ctx);
        deferReferences(
                rule.refs(),
                rule.outputId(),
                rule.ruleId(),
                driverRecord,
                evalCtx,
                target,
                targetBasket,
                targetTs,
                plan,
                ctx);
        processBags(rule, matchedSource, driverRecord, target, plan, ctx);
        return 1;
    }

    private Iom_jObject buildTargetObject(
            RulePlan rule,
            SourcePlan matchedSource,
            SourceRecord driverRecord,
            TransformPlan plan,
            ObjectCreationContext ctx) {
        Map<String, String> rawIdentityValues =
                buildIdentityKeyValues(rule.identitySourceKeys(), driverRecord.sourceObject(), matchedSource.alias());
        String sourceOid = driverRecord.sourceObject().getobjectoid();

        LinkedHashMap<String, CanonicalValue> identityValues = toCanonicalValues(rawIdentityValues);
        String targetOidType = resolveTargetOidType(plan, rule);

        OidGenerationRequest oidRequest = new OidGenerationRequest(
                plan.oidPlan().defaultStrategy(),
                plan.oidPlan().namespace(),
                rule.ruleId(),
                driverRecord.sourceFileId(),
                driverRecord.sourceBasketId(),
                driverRecord.sourceClass(),
                sourceOid,
                identityValues,
                targetOidType);

        String targetOid;
        try {
            targetOid = oidGenerationService.generate(oidRequest);
        } catch (UnsupportedOperationException e) {
            ctx.diagnostics()
                    .add(new Diagnostic(
                            DiagnosticCode.MAP_EXTERNAL_STRATEGY_UNSUPPORTED,
                            Severity.ERROR,
                            e.getMessage(),
                            rule.ruleId(),
                            "Use one of: preserve, integer, uuid, deterministicUuid"));
            targetOid = oidGenerationService.generate(new OidGenerationRequest(
                    OidStrategy.INTEGER,
                    plan.oidPlan().namespace(),
                    rule.ruleId(),
                    driverRecord.sourceFileId(),
                    driverRecord.sourceBasketId(),
                    driverRecord.sourceClass(),
                    sourceOid,
                    identityValues,
                    targetOidType));
        }

        return new Iom_jObject(getScopedName(rule.targetClass()), targetOid);
    }

    private void deferReferences(
            List<RefPlan> references,
            String outputId,
            String ruleId,
            SourceRecord driverRecord,
            EvalContext evalCtx,
            Iom_jObject target,
            String targetBasket,
            TypeSystemFacade targetTs,
            TransformPlan plan,
            ObjectCreationContext ctx) {
        Map<String, SourceRecord> boundRecords = evalCtx.sourceRecords();
        for (RefPlan ref : references) {
            var role = targetTs == null ? null : targetTs.resolveReference(target.getobjecttag(), ref.targetRoleName());
            String expression = ref.sourceRef();
            boolean singleton = expression != null && expression.trim().startsWith("#");
            Value value = expression == null || singleton ? null : expressionEngine.evaluate(expression, evalCtx);
            ReferenceValue reference = value instanceof ReferenceValue refValue ? refValue : null;
            if (reference == null && value != null && value.isDefined()) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.RUN_REF_TYPE_MISMATCH,
                                Severity.ERROR,
                                "sourceRef must yield a reference for role '" + ref.targetRoleName() + "'",
                                ruleId + " / " + target.getobjecttag() + "/" + target.getobjectoid(),
                                "Use the complete reference value or a bound source object"));
                continue;
            }
            if (reference != null
                    && reference.sourceAttribute() != null
                    && boundRecords
                                    .get(reference.sourceAlias())
                                    .sourceObject()
                                    .getattrvaluecount(reference.sourceAttribute())
                            > 1) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.RUN_REF_AMBIGUOUS,
                                Severity.ERROR,
                                "Multiple source reference values in " + expression + " for role "
                                        + ref.targetRoleName(),
                                ruleId + " / " + target.getobjecttag() + "/" + target.getobjectoid(),
                                "Use a source reference with a single value"));
                continue;
            }
            var selectors = sourceSelectors(ref, driverRecord, boundRecords, reference, singleton, plan);
            ctx.stateStore()
                    .addDeferredReference(new DeferredReference(
                            new TargetObjectKey(outputId, target.getobjecttag(), target.getobjectoid()),
                            ruleId,
                            targetBasket,
                            ref.targetRoleName(),
                            ref.association() != null
                                    ? ref.association()
                                    : role != null && role.association() != null
                                            ? role.association().getName()
                                            : null,
                            selectors,
                            ref.targetRuleId(),
                            singleton,
                            role != null ? role.targetClass() : null,
                            role != null
                                    ? new DeferredReference.Cardinality(role.minCardinality(), role.maxCardinality())
                                    : new DeferredReference.Cardinality(0, DeferredReference.Cardinality.UNBOUND),
                            ref.required()));
        }
    }

    private List<SourceReferenceSelector> sourceSelectors(
            RefPlan ref,
            SourceRecord driver,
            Map<String, SourceRecord> boundRecords,
            ReferenceValue reference,
            boolean singleton,
            TransformPlan plan) {
        if (singleton) {
            return List.of(new SourceReferenceSelector(
                    driver.sourceFileId(), driver.sourceBasketId(), null, ref.sourceRef(), false));
        }
        if (reference == null) {
            return List.of(
                    new SourceReferenceSelector(driver.sourceFileId(), driver.sourceBasketId(), null, null, false));
        }
        String rawOid = reference.oid();
        String oid = rawOid == null || rawOid.isBlank() ? null : rawOid;
        SourceRecord origin = reference.sourceAlias() == null ? null : boundRecords.get(reference.sourceAlias());
        if (origin == null) {
            return List.of(new SourceReferenceSelector(null, driver.sourceBasketId(), null, oid, false));
        }

        String explicitBid = reference.basketId();
        String sourceClass = reference.targetClass();
        var input = plan.inputsById().get(origin.sourceFileId());
        var sourceTs = input != null ? input.typeSystem() : null;
        var sourceRole = sourceTs != null && reference.sourceAttribute() != null
                ? sourceTs.resolveReference(origin.sourceClass(), reference.sourceAttribute())
                : null;
        if (sourceRole != null) sourceClass = sourceRole.targetClass();
        // XTF 2.4 EXTERNAL references carry a transfer ID without a BID.
        boolean transferScoped = reference.isTransferScoped(input, sourceRole);

        String basket = explicitBid != null ? explicitBid : origin.sourceBasketId();
        String originInput = origin.sourceFileId();
        List<SourcePlan> ruleSources = plan.rules().stream()
                .filter(rule -> rule.ruleId().equals(ref.targetRuleId()))
                .findFirst()
                .map(RulePlan::sources)
                .orElse(List.of());
        // A bound object identifies its own input. A target rule can supply the input for
        // a reference value, including a BID carried explicitly by an XTF 2.3 reference.
        var targetInputs = ruleSources.stream()
                .flatMap(source -> source.inputIds().stream())
                .distinct()
                .toList();
        List<String> inputs = List.of(originInput);
        if (reference.targetClass() == null && !targetInputs.isEmpty()) {
            if (explicitBid != null || transferScoped || !targetInputs.contains(originInput)) {
                inputs = targetInputs;
            }
        }

        List<SourceReferenceSelector> selectors = new ArrayList<>();
        for (String inputId : inputs) {
            // Include producing rules on source subclasses when the input model permits them.
            if (sourceRole != null && sourceClass != null) {
                String expected = sourceClass;
                selectors.add(new SourceReferenceSelector(inputId, basket, expected, oid, transferScoped));
                plan.rules().stream()
                        .flatMap(rule -> rule.sources().stream())
                        .filter(source -> source.inputIds().contains(inputId) && source.sourceClass() != null)
                        .map(source -> getScopedName(source.sourceClass()))
                        .distinct()
                        .filter(actual -> !expected.equals(actual) && sourceTs.isTypeCompatible(expected, actual))
                        .map(actual -> new SourceReferenceSelector(inputId, basket, actual, oid, transferScoped))
                        .forEach(selectors::add);
            } else {
                selectors.add(new SourceReferenceSelector(inputId, basket, sourceClass, oid, transferScoped));
            }
        }
        return selectors;
    }

    private void processBags(
            RulePlan rule,
            SourcePlan matchedSource,
            SourceRecord driverRecord,
            Iom_jObject target,
            TransformPlan plan,
            ObjectCreationContext ctx) {
        for (BagPlan bag : rule.bags()) {
            BoundSourceObject parent = new BoundSourceObject(matchedSource, driverRecord);
            BagExecutionContext bagCtx = new BagExecutionContext(
                    bag,
                    parent,
                    target,
                    plan,
                    ctx.stateStore(),
                    ctx.parentChildIndex(),
                    ctx.diagnostics(),
                    rule,
                    ctx.expandedTargets(),
                    ctx.geometryAdapter(),
                    ctx.sourceAttributeTypes(),
                    ctx.sourceLookupIndex(),
                    ctx.referenceIndex(),
                    ctx.basketRouter());
            if (bag.isEmbed()) {
                bagTransformationService.embed(bagCtx);
            } else {
                int beforeCount = countExpandedTargets(ctx.expandedTargets());
                bagTransformationService.expand(bagCtx);
                int afterCount = countExpandedTargets(ctx.expandedTargets());
                ctx.recordTargetsCreated(afterCount - beforeCount);
            }
        }
    }

    private static int countExpandedTargets(Map<String, Map<String, List<IomObject>>> expandedTargets) {
        int count = 0;
        for (var baskets : expandedTargets.values()) {
            for (var targets : baskets.values()) {
                count += targets.size();
            }
        }
        return count;
    }

    private void registerMapping(
            String outputId,
            String ruleId,
            SourceRecord driverRecord,
            Iom_jObject target,
            String targetBasket,
            ObjectCreationContext ctx) {
        ctx.referenceIndex()
                .add(
                        new SourceObjectKey(
                                driverRecord.sourceFileId(),
                                driverRecord.sourceBasketId(),
                                driverRecord.sourceClass(),
                                driverRecord.sourceObject().getobjectoid()),
                        new TargetReference(
                                outputId, target.getobjecttag(), target.getobjectoid(), targetBasket, ruleId));
        registerTarget(outputId, ruleId, target, ctx);
    }

    private static void registerTarget(String outputId, String ruleId, IomObject target, ObjectCreationContext ctx) {
        try {
            ctx.stateStore()
                    .registerTarget(
                            new TargetObjectKey(outputId, target.getobjecttag(), target.getobjectoid()), target);
        } catch (DuplicateTargetOidException e) {
            ctx.diagnostics()
                    .add(new Diagnostic(
                            DiagnosticCode.RUN_DUPLICATE_TARGET_OID,
                            Severity.ERROR,
                            e.getMessage(),
                            ruleId,
                            "This indicates a bug in OID generation"));
        }
    }

    private static String addToBasket(
            String outputId,
            SourceRecord driverRecord,
            Iom_jObject target,
            TransformPlan plan,
            ObjectCreationContext ctx) {
        String targetTopic = extractTopic(target.getobjecttag());
        String targetBasketId = ctx.basketRouter()
                .determineTargetBasket(
                        plan.basketPlan().defaultStrategy(),
                        driverRecord.sourceFileId(),
                        driverRecord.sourceBasketId(),
                        targetTopic,
                        plan.outputsById().get(outputId));
        ctx.objectsByOutputAndBasket()
                .computeIfAbsent(outputId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(basketKey(targetTopic, targetBasketId), ignored -> new ArrayList<>())
                .add(target);
        return targetBasketId;
    }

    public int createTargetForCreatePlan(
            CreatePlan create,
            SourceRecord record,
            EvalContext evalCtx,
            TransformPlan plan,
            ObjectCreationContext ctx) {
        String targetOid;
        try {
            targetOid = oidGenerationService.generate(new OidGenerationRequest(
                    create.identity().oidStrategy(),
                    create.identity().namespace(),
                    create.id(),
                    record.sourceFileId(),
                    record.sourceBasketId(),
                    record.sourceClass(),
                    record.sourceObject().getobjectoid(),
                    new LinkedHashMap<>(),
                    "uuid"));
        } catch (UnsupportedOperationException e) {
            targetOid = oidGenerationService.generate(new OidGenerationRequest(
                    OidStrategy.INTEGER,
                    create.identity().namespace(),
                    create.id(),
                    record.sourceFileId(),
                    record.sourceBasketId(),
                    record.sourceClass(),
                    record.sourceObject().getobjectoid(),
                    new LinkedHashMap<>(),
                    "uuid"));
        }

        Iom_jObject target = new Iom_jObject(getScopedName(create.targetClass()), targetOid);

        OutputBinding outputBinding = plan.outputsById().get(create.outputId());
        TypeSystemFacade targetTs = outputBinding != null ? outputBinding.typeSystem() : null;

        assignmentExecutionService.execute(create.assignments(), evalCtx, target, targetTs);

        String targetBasket = addToBasket(create.outputId(), record, target, plan, ctx);
        if (!create.references().isEmpty()) {
            registerTarget(create.outputId(), create.id(), target, ctx);
            deferReferences(
                    create.references(),
                    create.outputId(),
                    create.id(),
                    record,
                    evalCtx,
                    target,
                    targetBasket,
                    targetTs,
                    plan,
                    ctx);
        }
        return 1;
    }

    private static Map<String, String> buildIdentityKeyValues(
            List<String> identitySourceKeys, IomObject sourceObject, String alias) {
        if (identitySourceKeys == null || identitySourceKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : identitySourceKeys) {
            if (key == null || key.isBlank()) continue;
            String attrName = key;
            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                if (!parts[0].equals(alias)) continue;
                attrName = parts[1];
            }
            String val = sourceObject.getattrvalue(attrName);
            result.put(key, val != null ? val : "");
        }
        return result;
    }

    private static LinkedHashMap<String, CanonicalValue> toCanonicalValues(Map<String, String> rawValues) {
        LinkedHashMap<String, CanonicalValue> result = new LinkedHashMap<>();
        if (rawValues == null) return result;
        for (var entry : rawValues.entrySet()) {
            String value = entry.getValue();
            boolean defined = value != null && !value.isEmpty();
            result.put(entry.getKey(), new CanonicalValue("text", defined ? value : "", defined));
        }
        return result;
    }

    private static String resolveTargetOidType(TransformPlan plan, RulePlan rule) {
        if (plan == null || rule == null) return null;
        OutputBinding binding = plan.outputsById().get(rule.outputId());
        if (binding == null || binding.typeSystem() == null) return null;
        return binding.typeSystem().getOidType(getScopedName(rule.targetClass()));
    }

    static String getScopedName(ch.interlis.ili2c.metamodel.Table table) {
        return TypeSystemFacade.getScopedName(table);
    }

    static String extractTopic(String qualifiedClassName) {
        String[] parts = qualifiedClassName.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return qualifiedClassName;
    }

    static String basketKey(String topic, String basketId) {
        return topic + "::" + (basketId != null ? basketId : "");
    }

    public record ObjectCreationContext(
            Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket,
            Map<String, Map<String, List<IomObject>>> expandedTargets,
            DiagnosticCollector diagnostics,
            StateStore stateStore,
            ReferenceIndex referenceIndex,
            ParentChildIndex parentChildIndex,
            ExecutionMetrics metrics,
            GeometryAdapter geometryAdapter,
            Map<String, Map<String, TypeInfo>> sourceAttributeTypes,
            guru.interlis.transformer.state.SourceLookupIndex sourceLookupIndex,
            BasketRouter basketRouter) {
        public void recordTargetsCreated(int count) {
            if (metrics != null) {
                for (int i = 0; i < count; i++) {
                    metrics.recordTarget("(expanded)");
                }
            }
        }
    }
}
