package guru.interlis.transformer.engine;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.CreatePlan;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.RoleResolver;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.CanonicalValue;
import guru.interlis.transformer.state.DeferredRef;
import guru.interlis.transformer.state.DeferredReference;
import guru.interlis.transformer.state.DuplicateTargetOidException;
import guru.interlis.transformer.state.OidGenerationRequest;
import guru.interlis.transformer.state.OidGenerationService;
import guru.interlis.transformer.state.OidStrategy;
import guru.interlis.transformer.state.ParentChildIndex;
import guru.interlis.transformer.state.ReferenceIndex;
import guru.interlis.transformer.state.SourceObjectKey;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.state.SourceRefKey;
import guru.interlis.transformer.state.SourceReferenceSelector;
import guru.interlis.transformer.state.StateStore;
import guru.interlis.transformer.state.TargetObjectKey;
import guru.interlis.transformer.state.TargetReference;
import guru.interlis.transformer.state.TargetRefValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TargetObjectFactory {

    private final ExpressionEngine expressionEngine;
    private final OidGenerationService oidGenerationService;
    private final AssignmentExecutionService assignmentExecutionService;
    private final BagTransformationService bagTransformationService;

    public TargetObjectFactory(ExpressionEngine expressionEngine,
                                OidGenerationService oidGenerationService,
                                AssignmentExecutionService assignmentExecutionService,
                                BagTransformationService bagTransformationService) {
        this.expressionEngine = expressionEngine;
        this.oidGenerationService = oidGenerationService;
        this.assignmentExecutionService = assignmentExecutionService;
        this.bagTransformationService = bagTransformationService;
    }

    public int createTarget(RulePlan rule, SourcePlan matchedSource, SourceRecord driverRecord,
                             EvalContext evalCtx, TransformPlan plan,
                             ObjectCreationContext ctx) {
        int created = 0;

        Iom_jObject target = buildTargetObject(rule, matchedSource, driverRecord, plan, ctx);
        if (target == null) return 0;
        created++;

        OutputBinding outputBinding = plan.outputsById().get(rule.outputId());
        TypeSystemFacade targetTs = outputBinding != null ? outputBinding.typeSystem() : null;

        assignmentExecutionService.execute(rule.assignments(), evalCtx, target, targetTs);

        deferReferences(rule, matchedSource, driverRecord, target, targetTs, plan, ctx);

        processBags(rule, matchedSource, driverRecord, target, plan, ctx);

        registerMapping(rule, driverRecord, target, plan, ctx);

        addToBasket(rule, driverRecord, target, plan, ctx);

        return created;
    }

    private Iom_jObject buildTargetObject(RulePlan rule, SourcePlan matchedSource, SourceRecord driverRecord,
                                           TransformPlan plan, ObjectCreationContext ctx) {
        Map<String, String> rawIdentityValues = buildIdentityKeyValues(
                rule.identitySourceKeys(), driverRecord.sourceObject(), matchedSource.alias());
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
            ctx.diagnostics().add(new Diagnostic(DiagnosticCode.MAP_EXTERNAL_STRATEGY_UNSUPPORTED, Severity.ERROR,
                    e.getMessage(), rule.ruleId(),
                    "Use one of: preserve, integer, uuid, deterministicUuid"));
            targetOid = oidGenerationService.generate(new OidGenerationRequest(
                    OidStrategy.INTEGER, plan.oidPlan().namespace(), rule.ruleId(),
                    driverRecord.sourceFileId(), driverRecord.sourceBasketId(), driverRecord.sourceClass(),
                    sourceOid, identityValues, targetOidType));
        }

        return new Iom_jObject(getScopedName(rule.targetClass()), targetOid);
    }

    private void deferReferences(RulePlan rule, SourcePlan matchedSource, SourceRecord driverRecord, Iom_jObject target,
                                  TypeSystemFacade targetTs, TransformPlan plan,
                                  ObjectCreationContext ctx) {
        RoleResolver roleResolver = targetTs != null ? new RoleResolver(targetTs) : null;

        for (var ref : rule.refs()) {
            if (ref.sourceRef() == null) continue;
            String sourceRefOid = resolveSourceReferenceOid(ref.sourceRef(), matchedSource, driverRecord);
            if (sourceRefOid == null || sourceRefOid.isBlank()) {
                if (ref.required()) {
                    ctx.diagnostics().add(new Diagnostic(DiagnosticCode.RUN_REF_MISSING_MANDATORY,
                            plan.failPolicy() == FailPolicy.LENIENT ? Severity.WARNING : Severity.ERROR,
                            "Required reference missing for role " + ref.targetRoleName(),
                            getScopedName(rule.targetClass()) + "/" + target.getobjectoid(),
                            "Ensure source object has the required reference"));
                }
                continue;
            }

            String expectedTargetClass = null;
            if (roleResolver != null) {
                expectedTargetClass = roleResolver.resolveExpectedTargetClass(
                        ref, getScopedName(rule.targetClass()));
            }
            ctx.stateStore().addDeferredRef(new DeferredRef(
                    getScopedName(rule.targetClass()),
                    target.getobjectoid(),
                    ref.targetRoleName(),
                    driverRecord.sourceClass(),
                    sourceRefOid,
                    driverRecord.sourceFileId(),
                    driverRecord.sourceBasketId(),
                    expectedTargetClass));

            ReferenceIndex refIndex = ctx.referenceIndex();
            if (refIndex != null) {
                var roleCard = roleResolver != null
                        ? roleResolver.getTargetRoleCardinality(ref, getScopedName(rule.targetClass()))
                        : null;
                String associationName = roleResolver != null
                        ? roleResolver.getAssociationName(ref, getScopedName(rule.targetClass()))
                        : null;
                var refInfo = resolveReferencedSourceInfo(ref.targetRuleId(), plan);
                ctx.stateStore().addDeferredReference(new DeferredReference(
                        new TargetObjectKey(rule.outputId(), getScopedName(rule.targetClass()),
                                target.getobjectoid()),
                        ref.targetRoleName(),
                        associationName,
                        new SourceReferenceSelector(
                                refInfo.inputId(),
                                refInfo.basketId() != null ? refInfo.basketId()
                                        : driverRecord.sourceBasketId(),
                                refInfo.expectedSourceClass(),
                                sourceRefOid),
                        ref.targetRuleId(),
                        expectedTargetClass,
                        roleCard != null
                                ? new DeferredReference.Cardinality(roleCard.min(), roleCard.max())
                                : new DeferredReference.Cardinality(0, DeferredReference.Cardinality.UNBOUND),
                        ref.required()));
            }
        }
    }

    private void processBags(RulePlan rule, SourcePlan matchedSource, SourceRecord driverRecord,
                              Iom_jObject target, TransformPlan plan, ObjectCreationContext ctx) {
        for (BagPlan bag : rule.bags()) {
            BoundSourceObject parent = new BoundSourceObject(matchedSource, driverRecord);
            BagExecutionContext bagCtx = new BagExecutionContext(bag, parent, target, plan,
                    ctx.stateStore(), ctx.parentChildIndex(), ctx.diagnostics(), rule, ctx.expandedTargets(),
                    ctx.geometryAdapter(), ctx.sourceAttributeTypes(), ctx.referenceIndex());
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

    private void registerMapping(RulePlan rule, SourceRecord driverRecord, Iom_jObject target,
                                  TransformPlan plan, ObjectCreationContext ctx) {
        ctx.stateStore().putIdMapping(
                new SourceRefKey(driverRecord.sourceClass(), driverRecord.sourceObject().getobjectoid(),
                        driverRecord.sourceFileId(), driverRecord.sourceBasketId()),
                new TargetRefValue(getScopedName(rule.targetClass()), target.getobjectoid(),
                        rule.outputId(), driverRecord.sourceBasketId()));
        ctx.stateStore().putIdMapping(
                new SourceRefKey(null, driverRecord.sourceObject().getobjectoid(), null, null),
                new TargetRefValue(getScopedName(rule.targetClass()), target.getobjectoid(),
                        rule.outputId(), driverRecord.sourceBasketId()));

        ReferenceIndex refIndex = ctx.referenceIndex();
        if (refIndex != null) {
            refIndex.add(
                    new SourceObjectKey(
                            driverRecord.sourceFileId(),
                            driverRecord.sourceBasketId(),
                            driverRecord.sourceClass(),
                            driverRecord.sourceObject().getobjectoid()),
                    new TargetReference(
                            rule.outputId(),
                            getScopedName(rule.targetClass()),
                            target.getobjectoid(),
                            rule.ruleId()));
        }
        try {
            ctx.stateStore().registerTarget(
                    new TargetObjectKey(rule.outputId(), getScopedName(rule.targetClass()), target.getobjectoid()),
                    target);
        } catch (DuplicateTargetOidException e) {
            ctx.diagnostics().add(new Diagnostic(DiagnosticCode.RUN_DUPLICATE_TARGET_OID, Severity.ERROR,
                    e.getMessage(), rule.ruleId(), "This indicates a bug in OID generation"));
        }
    }

    private void addToBasket(RulePlan rule, SourceRecord driverRecord, Iom_jObject target,
                              TransformPlan plan, ObjectCreationContext ctx) {
        String targetTopic = extractTopic(getScopedName(rule.targetClass()));
        String targetBasketId = BasketRouter.determineTargetBasket(
                plan.basketPlan().defaultStrategy(), driverRecord.sourceBasketId(), targetTopic,
                getScopedName(rule.targetClass()));
        String basketKey = basketKey(targetTopic, targetBasketId);
        ctx.objectsByOutputAndBasket()
                .computeIfAbsent(rule.outputId(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(basketKey, ignored -> new ArrayList<>())
                .add(target);
    }

    public int createTargetForCreatePlan(CreatePlan create, RulePlan parentRule, SourceRecord record,
                                          EvalContext evalCtx, TransformPlan plan,
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

        for (var ref : create.references()) {
            if (ref.sourceRef() == null) continue;
            String attrName = ref.sourceRef();
            int dotIdx = attrName.indexOf('.');
            if (dotIdx >= 0) {
                attrName = attrName.substring(dotIdx + 1);
            }
            String sourceRefOid = readSourceReferenceOid(record.sourceObject(), attrName);
            if (sourceRefOid != null && !sourceRefOid.isBlank()) {
                ctx.stateStore().addDeferredRef(new DeferredRef(
                        getScopedName(create.targetClass()),
                        target.getobjectoid(),
                        ref.targetRoleName(),
                        record.sourceClass(),
                        sourceRefOid,
                        record.sourceFileId(),
                        record.sourceBasketId(),
                        null));
            }
        }

        String targetTopic = extractTopic(getScopedName(create.targetClass()));
        String targetBasketId = BasketRouter.determineTargetBasket(
                plan.basketPlan().defaultStrategy(), record.sourceBasketId(), targetTopic,
                getScopedName(create.targetClass()));
        String basketKey = basketKey(targetTopic, targetBasketId);
        ctx.objectsByOutputAndBasket()
                .computeIfAbsent(create.outputId(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(basketKey, ignored -> new ArrayList<>())
                .add(target);

        return 1;
    }

    private static String readSourceReferenceOid(IomObject source, String roleName) {
        if (roleName == null || roleName.isBlank()) return null;
        if (source.getattrvaluecount(roleName) > 0) {
            IomObject refObj = source.getattrobj(roleName, 0);
            if (refObj != null && refObj.getobjectrefoid() != null) {
                return refObj.getobjectrefoid();
            }
        }
        return source.getattrvalue(roleName);
    }

    private static String resolveSourceReferenceOid(String sourceRef, SourcePlan matchedSource,
                                                     SourceRecord driverRecord) {
        if (sourceRef == null || sourceRef.isBlank()) return null;
        String trimmed = sourceRef.trim();
        if (matchedSource != null && trimmed.equals(matchedSource.alias())) {
            return driverRecord.sourceObject().getobjectoid();
        }
        String attrName = trimmed;
        int dotIdx = attrName.indexOf('.');
        if (dotIdx >= 0) {
            String alias = attrName.substring(0, dotIdx);
            if (matchedSource != null && !alias.equals(matchedSource.alias())) {
                return null;
            }
            attrName = attrName.substring(dotIdx + 1);
        }
        return readSourceReferenceOid(driverRecord.sourceObject(), attrName);
    }

    private static Map<String, String> buildIdentityKeyValues(List<String> identitySourceKeys,
                                                               IomObject sourceObject, String alias) {
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

    private ReferenceSourceInfo resolveReferencedSourceInfo(String targetRuleId, TransformPlan plan) {
        if (targetRuleId == null || plan == null) return new ReferenceSourceInfo(null, null, null);
        for (RulePlan rp : plan.rules()) {
            if (targetRuleId.equals(rp.ruleId())) {
                if (!rp.sources().isEmpty()) {
                    SourcePlan sp = rp.sources().get(0);
                    String inputId = !sp.inputIds().isEmpty() ? sp.inputIds().iterator().next() : null;
                    String sourceClass = sp.sourceClass() != null
                            ? TypeSystemFacade.getScopedName(sp.sourceClass()) : null;
                    return new ReferenceSourceInfo(inputId, null, sourceClass);
                }
            }
        }
        return new ReferenceSourceInfo(null, null, null);
    }

    static String getScopedName(ch.interlis.ili2c.metamodel.Table table) {
        return TypeSystemFacade.getScopedName(table);
    }

    private static String extractTopic(String qualifiedClassName) {
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
            Map<String, Map<String, TypeInfo>> sourceAttributeTypes
    ) {
        public void recordTargetsCreated(int count) {
            if (metrics != null) {
                for (int i = 0; i < count; i++) {
                    metrics.recordTarget("(expanded)");
                }
            }
        }
    }

    private record ReferenceSourceInfo(String inputId, String basketId, String expectedSourceClass) {}
}
