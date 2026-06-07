package guru.interlis.transformer.engine;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.EndBasketEvent;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartBasketEvent;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.CoordValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.PolylineValue;
import guru.interlis.transformer.expr.SurfaceValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.geometry.NoOpGeometryAdapter;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.RoleResolver;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.BasketStrategy;
import guru.interlis.transformer.state.DeferredRef;
import guru.interlis.transformer.state.OidStrategy;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.state.SourceRefKey;
import guru.interlis.transformer.state.StateStore;
import guru.interlis.transformer.state.TargetRefValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class TransformationEngine {
    private final ExpressionEngine expressionEngine;
    private final StateStore stateStore;
    private final DiagnosticCollector diagnostics;
    private final GeometryAdapter geometryAdapter;

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore, DiagnosticCollector diagnostics) {
        this(expressionEngine, stateStore, diagnostics, new NoOpGeometryAdapter());
    }

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore,
                                 DiagnosticCollector diagnostics, GeometryAdapter geometryAdapter) {
        this.expressionEngine = expressionEngine;
        this.stateStore = stateStore;
        this.diagnostics = diagnostics;
        this.geometryAdapter = geometryAdapter;
    }

    // Counters for summary
    private long sourceRecordsRead;
    private long sourceRecordsFiltered;
    private long targetsCreated;
    private long targetsWritten;
    private long engineErrors;
    private long engineWarnings;

    // Expanded BAG targets (Phase 12 reverse)
    private Map<String, Map<String, List<IomObject>>> expandedTargets;

    // -- Legacy API (Phase 1-2) --------------------------------------------

    public TransformResult run(JobConfig config, Function<JobConfig.InputSpec, IoxReader> readerFactory,
                                Map<String, IoxWriter> writersByOutputId) throws Exception {
        resetCounters();
        pass1IndexLegacy(config, readerFactory);
        Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket = pass2BuildTargetsLegacy(config);
        resolveDeferredRefs(null);
        targetsWritten = writeOutputsLegacy(config, writersByOutputId, objectsByOutputAndBasket);
        return buildResult(null);
    }

    // -- Typed API (Phase 3+) -----------------------------------------------

    public TransformResult runTyped(TransformPlan plan,
                                     Function<String, IoxReader> readerFactoryById,
                                     Map<String, IoxWriter> writersByOutputId) throws Exception {
        resetCounters();
        pass1Index(plan, readerFactoryById);
        Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket = pass2BuildTargets(plan);
        resolveDeferredRefs(plan);
        checkRequiredRefs(plan);
        targetsWritten = writeOutputs(plan, writersByOutputId, objectsByOutputAndBasket);
        return buildResult(plan);
    }

    private void resetCounters() {
        sourceRecordsRead = 0;
        sourceRecordsFiltered = 0;
        targetsCreated = 0;
        targetsWritten = 0;
        engineErrors = 0;
        engineWarnings = 0;
    }

    private TransformResult buildResult(TransformPlan plan) {
        for (Diagnostic d : diagnostics.all()) {
            if (d.severity() == Severity.ERROR) engineErrors++;
            else if (d.severity() == Severity.WARNING) engineWarnings++;
        }
        return new TransformResult(sourceRecordsRead, sourceRecordsFiltered,
                targetsCreated, targetsWritten, engineErrors, engineWarnings,
                plan != null ? plan.oidStrategy().name() : "integer",
                plan != null ? plan.basketStrategy().name() : "preserve");
    }

    // -- Pass 1: Source Scan / Indexing ------------------------------------

    private void pass1Index(TransformPlan plan, Function<String, IoxReader> readerFactoryById) throws Exception {
        // Collect all source plans (rule-level + bag-level)
        List<SourcePlan> allSourcePlans = new ArrayList<>();
        for (RulePlan rule : plan.rules()) {
            allSourcePlans.addAll(rule.sources());
            for (BagPlan bag : rule.bags()) {
                allSourcePlans.add(bag.fromSource());
            }
        }
        // Deduplicate by (inputId, sourceClass)
        Set<String> seen = new HashSet<>();
        List<SourcePlan> uniqueSourcePlans = new ArrayList<>();
        for (SourcePlan sp : allSourcePlans) {
            for (String inputId : sp.inputIds()) {
                String key = inputId + "::" + getScopedName(sp.sourceClass());
                if (seen.add(key)) {
                    uniqueSourcePlans.add(sp);
                }
            }
        }

        for (var sp : uniqueSourcePlans) {
            for (String inputId : sp.inputIds()) {
                IoxReader reader = readerFactoryById.apply(inputId);
                try {
                    String basketId = null;
                    IoxEvent event;
                    while ((event = reader.read()) != null) {
                        if (event instanceof StartBasketEvent basket) {
                            basketId = basket.getBid();
                            continue;
                        }
                        if (event instanceof EndBasketEvent) {
                            basketId = null;
                            continue;
                        }
                        if (event instanceof EndTransferEvent) {
                            break;
                        }
                        if (event instanceof ObjectEvent obj) {
                            IomObject source = obj.getIomObject();
                            stateStore.indexSourceObject(source.getobjecttag(), inputId, basketId, source);
                            stateStore.addSourceRecord(new SourceRecord(inputId, basketId, source.getobjecttag(), source));
                            sourceRecordsRead++;

                            // Expand BAG structures into synthetic source records (Phase 12 reverse)
                            expandBagStructures(source, inputId, basketId, plan);
                        }
                    }
                } finally {
                    reader.close();
                }
            }
        }
    }

    private void pass1IndexLegacy(JobConfig config, Function<JobConfig.InputSpec, IoxReader> readerFactory) throws Exception {
        for (JobConfig.InputSpec input : config.job.inputs) {
            IoxReader reader = readerFactory.apply(input);
            try {
                String basketId = null;
                IoxEvent event;
                while ((event = reader.read()) != null) {
                    if (event instanceof StartBasketEvent basket) {
                        basketId = basket.getBid();
                        continue;
                    }
                    if (event instanceof EndBasketEvent) {
                        basketId = null;
                        continue;
                    }
                    if (event instanceof EndTransferEvent) {
                        break;
                    }
                    if (event instanceof ObjectEvent obj) {
                        IomObject source = obj.getIomObject();
                        stateStore.indexSourceObject(source.getobjecttag(), input.id, basketId, source);
                        stateStore.addSourceRecord(new SourceRecord(input.id, basketId, source.getobjecttag(), source));
                        sourceRecordsRead++;
                    }
                }
            } finally {
                reader.close();
            }
        }
    }

    // -- Pass 2: Target Object Creation ------------------------------------

    private Map<String, Map<String, List<IomObject>>> pass2BuildTargets(TransformPlan plan) {
        Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket = new LinkedHashMap<>();
        expandedTargets = new LinkedHashMap<>();

        // Build source attribute type info for geometry detection
        Map<String, Map<String, TypeInfo>> sourceAttrTypes = buildSourceAttributeTypeMap(plan);

        for (SourceRecord record : stateStore.sourceRecords()) {
            for (RulePlan rule : plan.rules()) {
                SourcePlan matchedSource = findMatchingSource(rule, record);
                if (matchedSource == null) continue;

                Map<String, IomObject> sources = Map.of(matchedSource.alias(), record.sourceObject());
                EvalContext evalCtx = new EvalContext(sources, diagnostics, rule.ruleId(), plan.enumMaps(),
                        geometryAdapter, sourceAttrTypes);

                // Evaluate where filter
                if (matchedSource.where() != null && !matchedSource.where().isBlank()) {
                    Value whereResult = expressionEngine.evaluate(matchedSource.where(), evalCtx);
                    if (!isFilterTruthy(whereResult)) {
                        sourceRecordsFiltered++;
                        continue;
                    }
                }

                Map<String, String> identityKeyValues = buildIdentityKeyValues(
                        rule.identitySourceKeys(), record.sourceObject(), matchedSource.alias());
                String sourceOid = record.sourceObject().getobjectoid();
                String targetOid = stateStore.nextOid(
                        plan.oidStrategy(), plan.oidNamespace(), rule.ruleId(),
                        sourceOid, identityKeyValues);
                if (targetOid == null) {
                    targetOid = Long.toString(oldNextOid());
                }

                Iom_jObject target = new Iom_jObject(
                        getScopedName(rule.targetClass()),
                        targetOid);
                targetsCreated++;

                TypeSystemFacade targetTs = plan.targetTypeSystems().get(rule.outputId());

                for (AssignmentPlan ap : rule.assignments()) {
                    Value value = expressionEngine.evaluate(ap.expression(), evalCtx);
                    if (value.isDefined()) {
                        setTargetAttribute(target, ap, value, targetTs);
                    }
                }

                RoleResolver roleResolver = targetTs != null ? new RoleResolver(targetTs) : null;

                for (var ref : rule.refs()) {
                    if (ref.sourceRef() == null) continue;
                    String attrName = ref.sourceRef();
                    int dotIdx = attrName.indexOf('.');
                    if (dotIdx >= 0) {
                        attrName = attrName.substring(dotIdx + 1);
                    }
                    String sourceRefOid = readSourceReferenceOid(record.sourceObject(), attrName);
                    if (sourceRefOid != null && !sourceRefOid.isBlank()) {
                        String expectedTargetClass = null;
                        if (roleResolver != null) {
                            expectedTargetClass = roleResolver.resolveExpectedTargetClass(
                                    ref, getScopedName(rule.targetClass()));
                        }
                        stateStore.addDeferredRef(new DeferredRef(
                                getScopedName(rule.targetClass()),
                                target.getobjectoid(),
                                ref.targetRoleName(),
                                record.sourceClass(),
                                sourceRefOid,
                                record.sourceFileId(),
                                record.sourceBasketId(),
                                expectedTargetClass
                        ));
                    }
                }

                // Process BAG OF STRUCTURE (Phase 12)
                for (BagPlan bag : rule.bags()) {
                    processBag(bag, rule, matchedSource, record, target, plan);
                }

                stateStore.putIdMapping(
                        new SourceRefKey(record.sourceClass(), record.sourceObject().getobjectoid(),
                                record.sourceFileId(), record.sourceBasketId()),
                        new TargetRefValue(getScopedName(rule.targetClass()), target.getobjectoid(),
                                rule.outputId(), record.sourceBasketId())
                );
                stateStore.putIdMapping(
                        new SourceRefKey(null, record.sourceObject().getobjectoid(),
                                null, null),
                        new TargetRefValue(getScopedName(rule.targetClass()), target.getobjectoid(),
                                rule.outputId(), record.sourceBasketId())
                );
                stateStore.indexTargetObject(getScopedName(rule.targetClass()), target.getobjectoid(), target);

                String targetTopic = extractTopic(getScopedName(rule.targetClass()));
                String targetBasketId = BasketRouter.determineTargetBasket(
                        plan.basketStrategy(), record.sourceBasketId(), targetTopic,
                        getScopedName(rule.targetClass()));
                String basketKey = basketKey(targetTopic, targetBasketId);
                objectsByOutputAndBasket
                        .computeIfAbsent(rule.outputId(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(basketKey, ignored -> new ArrayList<>())
                        .add(target);
            }
        }

        // Merge expanded BAG targets (Phase 12 reverse)
        for (var entry : expandedTargets.entrySet()) {
            String outputId = entry.getKey();
            for (var basketEntry : entry.getValue().entrySet()) {
                objectsByOutputAndBasket
                        .computeIfAbsent(outputId, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(basketEntry.getKey(), ignored -> new ArrayList<>())
                        .addAll(basketEntry.getValue());
            }
        }

        return objectsByOutputAndBasket;
    }

    // Legacy pass2 for backward compatibility
    private Map<String, Map<String, List<IomObject>>> pass2BuildTargetsLegacy(JobConfig config) {
        Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket = new LinkedHashMap<>();
        for (SourceRecord record : stateStore.sourceRecords()) {
            for (JobConfig.RuleSpec rule : config.mapping.rules) {
                JobConfig.SourceSpec sourceSpec = rule.sources.stream()
                        .filter(spec -> spec.getInputIds().contains(record.sourceFileId())
                                && spec.clazz.equals(record.sourceClass()))
                        .findFirst().orElse(null);
                if (sourceSpec == null) continue;

                Iom_jObject target = new Iom_jObject(rule.getEffectiveTargetClass(),
                        Long.toString(stateStore.nextOid()));
                targetsCreated++;
                Map<String, IomObject> sources = Map.of(sourceSpec.alias, record.sourceObject());
                EvalContext evalCtxLegacy = new EvalContext(sources, diagnostics, rule.id);
                for (JobConfig.AttributeMapping attr : rule.getAllAttributes()) {
                    Value value = expressionEngine.evaluate(attr.expr, evalCtxLegacy);
                    if (value.isDefined()) {
                        Object nativeValue = value.toNative();
                        if (nativeValue != null) {
                            target.setattrvalue(attr.target, nativeValue.toString());
                        }
                    }
                }
                for (JobConfig.RefMapping ref : rule.getEffectiveRefs()) {
                    RefCall call = parseRefCall(ref.expr);
                    if (call == null || !sourceSpec.alias.equals(call.alias())) continue;
                    String sourceRefOid = readSourceReferenceOid(record.sourceObject(), call.roleName());
                    if (sourceRefOid != null && !sourceRefOid.isBlank()) {
                        stateStore.addDeferredRef(new DeferredRef(
                                rule.getEffectiveTargetClass(),
                                target.getobjectoid(),
                                ref.target,
                                record.sourceClass(),
                                sourceRefOid,
                                record.sourceFileId(),
                                record.sourceBasketId(),
                                null
                        ));
                    }
                }

                stateStore.putIdMapping(
                        new SourceRefKey(record.sourceClass(), record.sourceObject().getobjectoid(),
                                record.sourceFileId(), record.sourceBasketId()),
                        new TargetRefValue(rule.getEffectiveTargetClass(), target.getobjectoid(),
                                rule.getEffectiveTargetOutput(), record.sourceBasketId())
                );
                stateStore.putIdMapping(
                        new SourceRefKey(null, record.sourceObject().getobjectoid(),
                                null, null),
                        new TargetRefValue(rule.getEffectiveTargetClass(), target.getobjectoid(),
                                rule.getEffectiveTargetOutput(), record.sourceBasketId())
                );
                stateStore.indexTargetObject(rule.getEffectiveTargetClass(), target.getobjectoid(), target);

                String basketKey = basketKey(extractTopic(rule.getEffectiveTargetClass()), record.sourceBasketId());
                objectsByOutputAndBasket
                        .computeIfAbsent(rule.getEffectiveTargetOutput(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(basketKey, ignored -> new ArrayList<>())
                        .add(target);
            }
        }
        return objectsByOutputAndBasket;
    }

    // -- Writer ------------------------------------------------------------

    private long writeOutputs(TransformPlan plan, Map<String, IoxWriter> writersByOutputId,
                               Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket) throws Exception {
        long written = 0;
        for (var entry : writersByOutputId.entrySet()) {
            String outputId = entry.getKey();
            IoxWriter writer = entry.getValue();
            writer.write(new ch.interlis.iox_j.StartTransferEvent("ili-transformer", null, null));
            Map<String, List<IomObject>> byBasket = objectsByOutputAndBasket.getOrDefault(outputId, Map.of());
            for (var basketEntry : byBasket.entrySet()) {
                String[] parts = basketEntry.getKey().split("::", 2);
                String topic = parts[0];
                String basketId = parts.length > 1 ? parts[1] : null;
                writer.write(new ch.interlis.iox_j.StartBasketEvent(topic, basketId));
                List<IomObject> sorted = new ArrayList<>(basketEntry.getValue());
                sorted.sort(Comparator.comparing(IomObject::getobjecttag)
                        .thenComparing(IomObject::getobjectoid));
                for (IomObject target : sorted) {
                    writer.write(new ch.interlis.iox_j.ObjectEvent(target));
                    written++;
                }
                writer.write(new ch.interlis.iox_j.EndBasketEvent());
            }
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();
        }
        return written;
    }

    private long writeOutputsLegacy(JobConfig config, Map<String, IoxWriter> writersByOutputId,
                                     Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket) throws Exception {
        long written = 0;
        for (JobConfig.OutputSpec output : config.job.outputs) {
            IoxWriter writer = writersByOutputId.get(output.id);
            writer.write(new ch.interlis.iox_j.StartTransferEvent("ilinexus", null, null));
            Map<String, List<IomObject>> byBasket = objectsByOutputAndBasket.getOrDefault(output.id, Map.of());
            for (var basketEntry : byBasket.entrySet()) {
                String[] parts = basketEntry.getKey().split("::", 2);
                String topic = parts[0];
                String basketId = parts.length > 1 ? parts[1] : null;
                writer.write(new ch.interlis.iox_j.StartBasketEvent(topic, basketId));
                List<IomObject> sorted = new ArrayList<>(basketEntry.getValue());
                sorted.sort(Comparator.comparing(IomObject::getobjecttag)
                        .thenComparing(IomObject::getobjectoid));
                for (IomObject target : sorted) {
                    writer.write(new ch.interlis.iox_j.ObjectEvent(target));
                    written++;
                }
                writer.write(new ch.interlis.iox_j.EndBasketEvent());
            }
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();
        }
        return written;
    }

    // -- Shared helpers ----------------------------------------------------

    private SourcePlan findMatchingSource(RulePlan rule, SourceRecord record) {
        for (SourcePlan sp : rule.sources()) {
            if (sp.sourceClass() == null) continue;
            if (!sp.inputIds().contains(record.sourceFileId())) continue;
            if (getScopedName(sp.sourceClass()).equals(record.sourceClass())) {
                return sp;
            }
        }
        return null;
    }

    private void resolveDeferredRefs(TransformPlan plan) {
        for (DeferredRef deferredRef : stateStore.deferredRefs()) {
            List<TargetRefValue> candidates = stateStore.findIdMappings(
                    null,
                    deferredRef.sourceReferencedOid(),
                    deferredRef.sourceFileId(),
                    deferredRef.sourceBasketId());
            if (candidates.isEmpty()) {
                Severity severity = isRefRequired(plan, deferredRef)
                        ? failPolicySeverity(plan, Severity.ERROR)
                        : failPolicySeverity(plan, Severity.WARNING);
                String code = isRefRequired(plan, deferredRef)
                        ? DiagnosticCode.RUN_REF_MISSING_MANDATORY
                        : DiagnosticCode.RUN_REF_UNRESOLVED;
                diagnostics.add(new Diagnostic(code, severity,
                        "Could not resolve reference " + deferredRef.sourceReferencedOid(),
                        deferredRef.ownerTargetClass() + "/" + deferredRef.ownerTargetOid(),
                        "Check source OID / basket routing"));
                continue;
            }
            if (candidates.size() > 1) {
                diagnostics.add(new Diagnostic(DiagnosticCode.RUN_REF_AMBIGUOUS, Severity.ERROR,
                        "Ambiguous reference " + deferredRef.sourceReferencedOid(),
                        deferredRef.ownerTargetClass() + "/" + deferredRef.ownerTargetOid(),
                        "Constrain mapping by file or basket"));
                continue;
            }
            TargetRefValue resolved = candidates.get(0);

            if (deferredRef.expectedTargetClass() != null
                    && !deferredRef.expectedTargetClass().isEmpty()
                    && !deferredRef.expectedTargetClass().equals(resolved.targetClass())) {
                Severity severity = failPolicySeverity(plan, Severity.ERROR);
                diagnostics.add(new Diagnostic(DiagnosticCode.RUN_REF_TYPE_MISMATCH, severity,
                        "Type mismatch for reference " + deferredRef.sourceReferencedOid()
                                + ": expected " + deferredRef.expectedTargetClass()
                                + " but resolved " + resolved.targetClass(),
                        deferredRef.ownerTargetClass() + "/" + deferredRef.ownerTargetOid(),
                        "Check target class of resolved object"));
                continue;
            }

            stateStore.findTargetObject(deferredRef.ownerTargetClass(), deferredRef.ownerTargetOid()).ifPresent(owner -> {
                IomObject ref = owner.addattrobj(deferredRef.ownerAttribute(), Iom_jObject.REF);
                ref.setobjectrefoid(resolved.targetOid());
            });
        }
    }

    private void checkRequiredRefs(TransformPlan plan) {
        if (plan == null) return;
        TypeSystemFacade targetTs = plan.targetTypeSystems().values().stream().findFirst().orElse(null);
        for (RulePlan rule : plan.rules()) {
            String targetClassScoped = getScopedName(rule.targetClass());
            for (var ref : rule.refs()) {
                if (!ref.required()) continue;
                if (targetTs == null) continue;
                RoleResolver roleResolver = new RoleResolver(targetTs);
                long minCardinality = roleResolver.getTargetRoleCardinality(ref, targetClassScoped).min();
                if (minCardinality <= 0) continue;

                boolean hasResolved = stateStore.deferredRefs().stream()
                        .anyMatch(dr -> dr.ownerTargetClass().equals(targetClassScoped)
                                && dr.ownerAttribute().equals(ref.targetRoleName())
                                && stateStore.findIdMappings(
                                        null, dr.sourceReferencedOid(),
                                        null, null).size() == 1);
                if (!hasResolved) {
                    Severity severity = failPolicySeverity(plan, Severity.ERROR);
                    diagnostics.add(new Diagnostic(DiagnosticCode.RUN_REF_MISSING_MANDATORY, severity,
                            "Required reference missing for role " + ref.targetRoleName(),
                            targetClassScoped,
                            "Ensure source objects have the required reference"));
                }
            }
        }
    }

    // -- BAG OF STRUCTURE processing (Phase 12) -------------------------

    private void processBag(BagPlan bag, RulePlan rule, SourcePlan parentSource,
                             SourceRecord parentRecord, Iom_jObject target, TransformPlan plan) {
        if (bag.mode() == BagPlan.BagMode.EXPAND) {
            processExpandBag(bag, rule, parentSource, parentRecord, target, plan);
            return;
        }

        // EMBED mode: create embedded structures from separate source records
        processEmbedBag(bag, rule, parentSource, parentRecord, target, plan);
    }

    private void processEmbedBag(BagPlan bag, RulePlan rule, SourcePlan parentSource,
                                  SourceRecord parentRecord, Iom_jObject target, TransformPlan plan) {
        String bagSourceClass = getScopedName(bag.fromSource().sourceClass());
        List<IomObject> bagSourceObjects = new ArrayList<>();

        for (SourceRecord sr : stateStore.sourceRecords()) {
            if (bagSourceClass.equals(sr.sourceClass())
                    && bag.fromSource().inputIds().contains(sr.sourceFileId())) {
                bagSourceObjects.add(sr.sourceObject());
            }
        }

        if (bagSourceObjects.isEmpty()) {
            return;
        }

        List<IomObject> structures = new ArrayList<>();
        Map<String, IomObject> parentSources = Map.of(parentSource.alias(), parentRecord.sourceObject());

        for (IomObject bagSource : bagSourceObjects) {
            Map<String, IomObject> allSources = new java.util.LinkedHashMap<>();
            allSources.put(bag.fromSource().alias(), bagSource);
            allSources.putAll(parentSources);

            EvalContext bagCtx = new EvalContext(allSources, diagnostics, rule.ruleId(), plan.enumMaps());

            // Evaluate bag where filter
            if (bag.whereExpression() != null && !bag.whereExpression().isBlank()) {
                Value whereResult = expressionEngine.evaluate(bag.whereExpression(), bagCtx);
                if (!isFilterTruthy(whereResult)) {
                    continue;
                }
            }

            // Create structure object
            Iom_jObject struct = new Iom_jObject(bag.structureName(), null);

            Map<String, IomObject> assignSources = Map.of(bag.fromSource().alias(), bagSource);
            EvalContext assignCtx = new EvalContext(assignSources, diagnostics, rule.ruleId(), plan.enumMaps());

            for (AssignmentPlan ap : bag.assignments()) {
                Value value = expressionEngine.evaluate(ap.expression(), assignCtx);
                if (value.isDefined()) {
                    Object nativeValue = value.toNative();
                    if (nativeValue != null) {
                        struct.setattrvalue(ap.targetAttrName(), nativeValue.toString());
                    }
                }
            }
            structures.add(struct);
        }

        // Add structures to the target object's BAG attribute
        for (IomObject struct : structures) {
            target.addattrobj(bag.bagAttrName(), struct);
        }
    }

    private void processExpandBag(BagPlan bag, RulePlan rule, SourcePlan parentSource,
                                    SourceRecord parentRecord, Iom_jObject target, TransformPlan plan) {
        IomObject parentObj = parentRecord.sourceObject();
        String bagAttrName = bag.bagAttrName();

        int count = parentObj.getattrvaluecount(bagAttrName);
        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            IomObject structure = parentObj.getattrobj(bagAttrName, i);
            if (structure == null) continue;

            String targetOid = stateStore.nextOid(
                    plan.oidStrategy(), plan.oidNamespace(), rule.ruleId(),
                    parentObj.getobjectoid() + "-" + i,
                    Map.of());
            if (targetOid == null) {
                targetOid = Long.toString(oldNextOid());
            }

            Iom_jObject bagTarget = new Iom_jObject(bag.structureName(), targetOid);
            targetsCreated++;

            Map<String, IomObject> assignSources = Map.of(bag.fromSource().alias(), structure);
            EvalContext assignCtx = new EvalContext(assignSources, diagnostics, rule.ruleId(), plan.enumMaps());

            for (AssignmentPlan ap : bag.assignments()) {
                Value value = expressionEngine.evaluate(ap.expression(), assignCtx);
                if (value.isDefined()) {
                    Object nativeValue = value.toNative();
                    if (nativeValue != null) {
                        bagTarget.setattrvalue(ap.targetAttrName(), nativeValue.toString());
                    }
                }
            }

            stateStore.indexTargetObject(bag.structureName(), bagTarget.getobjectoid(), bagTarget);

            String targetTopic = extractTopic(bag.structureName());
            String targetBasketId = BasketRouter.determineTargetBasket(
                    plan.basketStrategy(), parentRecord.sourceBasketId(), targetTopic,
                    bag.structureName());
            String basketKey = basketKey(targetTopic, targetBasketId);
            expandedTargets
                    .computeIfAbsent(rule.outputId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(basketKey, ignored -> new ArrayList<>())
                    .add(bagTarget);
        }
    }

    // -- BAG Structure Expansion (Phase 12 reverse) ---------------------

    private void expandBagStructures(IomObject source, String inputId, String basketId, TransformPlan plan) {
        for (RulePlan rule : plan.rules()) {
            for (BagPlan bag : rule.bags()) {
                if (bag.mode() != BagPlan.BagMode.EXPAND) continue;
                // In EXPAND mode, bagAttrName is the source BAG attribute name
                // fromSource.class is the parent class, fromSource.inputIds has the input
                if (!bag.fromSource().inputIds().contains(inputId)) continue;

                String parentClassName = getScopedName(bag.fromSource().sourceClass());
                if (!parentClassName.equals(source.getobjecttag())) continue;

                String bagAttrName = bag.bagAttrName();
                int count = source.getattrvaluecount(bagAttrName);
                if (count <= 0) continue;

                for (int i = 0; i < count; i++) {
                    IomObject structure = source.getattrobj(bagAttrName, i);
                    if (structure == null) continue;
                    // The structure's tag is the structure type name from the target model
                    // Create a synthetic source record with the parent's OID stored
                    stateStore.addSourceRecord(new SourceRecord(
                            inputId, basketId,
                            structure.getobjecttag(),
                            structure));
                }
            }
        }
    }

    private boolean isRefRequired(TransformPlan plan, DeferredRef deferredRef) {
        if (plan == null) return false;
        for (RulePlan rule : plan.rules()) {
            if (!getScopedName(rule.targetClass()).equals(deferredRef.ownerTargetClass())) continue;
            for (var ref : rule.refs()) {
                if (ref.targetRoleName().equals(deferredRef.ownerAttribute()) && ref.required()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Severity failPolicySeverity(TransformPlan plan, Severity defaultSeverity) {
        if (plan == null) return defaultSeverity;
        return "lenient".equals(plan.failPolicy()) ? Severity.WARNING : defaultSeverity;
    }

    private String readSourceReferenceOid(IomObject source, String roleName) {
        if (roleName == null || roleName.isBlank()) return null;
        if (source.getattrvaluecount(roleName) > 0) {
            IomObject refObj = source.getattrobj(roleName, 0);
            if (refObj != null && refObj.getobjectrefoid() != null) {
                return refObj.getobjectrefoid();
            }
        }
        return source.getattrvalue(roleName);
    }

    private RefCall parseRefCall(String expr) {
        if (expr == null) return null;
        String trimmed = expr.trim();
        if (!trimmed.startsWith("ref(") || !trimmed.endsWith(")")) return null;
        String argsPart = trimmed.substring(4, trimmed.length() - 1);
        String[] args = argsPart.split(",", 2);
        if (args.length != 2) return null;
        return new RefCall(stripQuotes(args[0].trim()), stripQuotes(args[1].trim()));
    }

    private String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String extractTopic(String qualifiedClassName) {
        String[] parts = qualifiedClassName.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return qualifiedClassName;
    }

    private String basketKey(String topic, String basketId) {
        return topic + "::" + (basketId == null ? "" : basketId);
    }

    private static boolean isFilterTruthy(Value value) {
        if (value == null || value.isNull()) return false;
        if (value instanceof BooleanValue bv) return bv.value();
        if (value instanceof guru.interlis.transformer.expr.TextValue tv) return !tv.value().isEmpty();
        if (value instanceof guru.interlis.transformer.expr.NumberValue nv) return nv.value() != 0;
        return true;
    }

    private void setTargetAttribute(Iom_jObject target, AssignmentPlan ap, Value value,
                                     TypeSystemFacade targetTs) {
        TypeInfo targetType = ap.expectedType();
        if (isGeometryType(targetType) && value instanceof Value) {
            IomObject geomObj = geometryAdapter.denormalize(value, targetType);
            if (geomObj != null) {
                target.addattrobj(ap.targetAttrName(), geomObj);
                return;
            }
        }
        Object nativeValue = value.toNative();
        if (nativeValue != null) {
            target.setattrvalue(ap.targetAttrName(), nativeValue.toString());
        }
    }

    private static boolean isGeometryType(TypeInfo type) {
        return type == TypeInfo.COORD || type == TypeInfo.POLYLINE
                || type == TypeInfo.SURFACE || type == TypeInfo.AREA;
    }

    private static Map<String, Map<String, TypeInfo>> buildSourceAttributeTypeMap(TransformPlan plan) {
        Map<String, Map<String, TypeInfo>> result = new LinkedHashMap<>();
        for (RulePlan rule : plan.rules()) {
            for (SourcePlan sp : rule.sources()) {
                if (sp.sourceClass() == null) continue;
                String alias = sp.alias();
                String sourceModel = null;
                for (String inputId : sp.inputIds()) {
                    for (var entry : plan.sourceTypeSystems().entrySet()) {
                        if (entry.getKey() != null) {
                            sourceModel = entry.getKey();
                            break;
                        }
                    }
                }
                TypeSystemFacade sourceTs = sourceModel != null ? plan.sourceTypeSystems().get(sourceModel) : null;
                if (sourceTs == null) continue;

                Map<String, TypeInfo> aliasTypes = new LinkedHashMap<>();
                Iterator<Extendable> it = sp.sourceClass().getAttributes();
                while (it.hasNext()) {
                    Extendable ext = it.next();
                    if (ext instanceof AttributeDef attr) {
                        TypeInfo ti = classifyAttributeType(attr);
                        aliasTypes.put(attr.getName(), ti);
                    }
                }
                result.put(alias, aliasTypes);
            }
        }
        return result;
    }

    private static TypeInfo classifyAttributeType(AttributeDef attr) {
        ch.interlis.ili2c.metamodel.Type type = attr.getDomainResolvingAliases();
        if (type == null) type = attr.getDomain();
        if (type == null) return TypeInfo.UNKNOWN;
        if (type instanceof ch.interlis.ili2c.metamodel.CoordType) return TypeInfo.COORD;
        if (type instanceof ch.interlis.ili2c.metamodel.PolylineType) return TypeInfo.POLYLINE;
        if (type instanceof ch.interlis.ili2c.metamodel.SurfaceOrAreaType) return TypeInfo.SURFACE;
        if (type instanceof ch.interlis.ili2c.metamodel.AreaType) return TypeInfo.AREA;
        if (type instanceof ch.interlis.ili2c.metamodel.SurfaceType) return TypeInfo.SURFACE;
        if (type.isBoolean()) return TypeInfo.BOOLEAN;
        if (type instanceof ch.interlis.ili2c.metamodel.NumericType || type instanceof ch.interlis.ili2c.metamodel.NumericalType) return TypeInfo.NUMERIC;
        if (type instanceof ch.interlis.ili2c.metamodel.EnumerationType) return TypeInfo.ENUM;
        if (type instanceof ch.interlis.ili2c.metamodel.TextType) return TypeInfo.TEXT;
        if (type instanceof ch.interlis.ili2c.metamodel.CompositionType) return TypeInfo.STRUCTURE;
        if (type instanceof ch.interlis.ili2c.metamodel.ReferenceType) return TypeInfo.REFERENCE;
        return TypeInfo.UNKNOWN;
    }

    private static String getScopedName(Table table) {
        Container container = table.getContainer();
        if (container instanceof Topic topic) {
            Container modelContainer = topic.getContainer();
            if (modelContainer instanceof Model model) {
                return model.getName() + "." + topic.getName() + "." + table.getName();
            }
        }
        return table.getName();
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

    private long oldNextOid() {
        return stateStore.nextOid();
    }

    private record RefCall(String alias, String roleName) {}
}
