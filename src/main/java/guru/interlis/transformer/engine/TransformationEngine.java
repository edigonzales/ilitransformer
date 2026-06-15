package guru.interlis.transformer.engine;

import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Model;
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
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.geometry.IoxGeometryAdapter;
import guru.interlis.transformer.loss.LossinessCollector;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.CreatePlan;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.JoinCardinality;
import guru.interlis.transformer.mapping.plan.JoinPlan;
import guru.interlis.transformer.mapping.plan.JoinType;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.RuleDependencyGraph;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.RoleResolver;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.BasketStrategy;
import guru.interlis.transformer.state.CanonicalValue;
import guru.interlis.transformer.state.DefaultOidGenerationService;
import guru.interlis.transformer.state.DeferredRef;
import guru.interlis.transformer.state.DeferredReference;
import guru.interlis.transformer.state.DuplicateTargetOidException;
import guru.interlis.transformer.state.InMemoryParentChildIndex;
import guru.interlis.transformer.state.InMemorySourceLookupIndex;
import guru.interlis.transformer.state.LookupKey;
import guru.interlis.transformer.state.OidGenerationRequest;
import guru.interlis.transformer.state.OidGenerationService;
import guru.interlis.transformer.state.OidStrategy;
import guru.interlis.transformer.state.ParentChildIndex;
import guru.interlis.transformer.state.ReferenceIndex;
import guru.interlis.transformer.state.SourceObjectKey;
import guru.interlis.transformer.state.SourceLookupIndex;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.state.SourceRefKey;
import guru.interlis.transformer.state.SourceReferenceSelector;
import guru.interlis.transformer.state.StateStore;
import guru.interlis.transformer.state.TargetObjectKey;
import guru.interlis.transformer.state.TargetReference;
import guru.interlis.transformer.state.TargetRefValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class TransformationEngine {

    // -- Injected services --------------------------------------------------
    private final ExpressionEngine expressionEngine;
    private final StateStore stateStore;
    private final DiagnosticCollector diagnostics;
    private final GeometryAdapter geometryAdapter;
    private final OidGenerationService oidGenerationService;

    // Phase 19 / Phase 26 services
    private final ReferenceIndex referenceIndex;
    private final ReferenceResolutionService referenceResolutionService;
    private final SourceLookupIndex sourceLookupIndex;
    private final ParentChildIndex parentChildIndex;

    // Phase 26 extracted services
    private final SourceIndexingService sourceIndexingService;
    private final RuleExecutionService ruleExecutionService;
    private final OutputWritingService outputWritingService;
    private final BagTransformationService bagTransformationService;

    // Phase 26 metrics
    private final ExecutionMetrics metrics;
    private final LossinessCollector lossinessCollector;

    // Phase 26 pre-computed index (built per-run from plan)
    private RuleDispatchIndex dispatchIndex;

    private Map<String, Map<String, List<IomObject>>> expandedTargets;

    // -- Constructor chain (backward compatible) ----------------------------

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore,
                                 DiagnosticCollector diagnostics) {
        this(expressionEngine, stateStore, diagnostics, new IoxGeometryAdapter(),
                new DefaultOidGenerationService(), null);
    }

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore,
                                 DiagnosticCollector diagnostics, GeometryAdapter geometryAdapter) {
        this(expressionEngine, stateStore, diagnostics, geometryAdapter,
                new DefaultOidGenerationService(), null);
    }

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore,
                                 DiagnosticCollector diagnostics, GeometryAdapter geometryAdapter,
                                 OidGenerationService oidGenerationService) {
        this(expressionEngine, stateStore, diagnostics, geometryAdapter,
                oidGenerationService, null);
    }

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore,
                                 DiagnosticCollector diagnostics, GeometryAdapter geometryAdapter,
                                 OidGenerationService oidGenerationService,
                                 ReferenceIndex referenceIndex) {
        this(expressionEngine, stateStore, diagnostics, geometryAdapter,
                oidGenerationService, referenceIndex, new InMemorySourceLookupIndex());
    }

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore,
                                 DiagnosticCollector diagnostics, GeometryAdapter geometryAdapter,
                                 OidGenerationService oidGenerationService,
                                 ReferenceIndex referenceIndex,
                                 SourceLookupIndex sourceLookupIndex) {
        this(expressionEngine, stateStore, diagnostics, geometryAdapter,
                oidGenerationService, referenceIndex, sourceLookupIndex,
                new InMemoryParentChildIndex());
    }

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore,
                                 DiagnosticCollector diagnostics, GeometryAdapter geometryAdapter,
                                 OidGenerationService oidGenerationService,
                                 ReferenceIndex referenceIndex,
                                 SourceLookupIndex sourceLookupIndex,
                                 ParentChildIndex parentChildIndex) {
        this(expressionEngine, stateStore, diagnostics, geometryAdapter,
                oidGenerationService, referenceIndex, sourceLookupIndex,
                parentChildIndex, new ExecutionMetrics());
    }

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore,
                                 DiagnosticCollector diagnostics, GeometryAdapter geometryAdapter,
                                 OidGenerationService oidGenerationService,
                                 ReferenceIndex referenceIndex,
                                 SourceLookupIndex sourceLookupIndex,
                                 ParentChildIndex parentChildIndex,
                                 ExecutionMetrics metrics) {
        this.expressionEngine = expressionEngine;
        this.stateStore = stateStore;
        this.diagnostics = diagnostics;
        this.geometryAdapter = geometryAdapter;
        this.oidGenerationService = oidGenerationService;
        this.referenceIndex = referenceIndex;
        this.referenceResolutionService = referenceIndex != null
                ? new ReferenceResolutionService() : null;
        this.sourceLookupIndex = sourceLookupIndex;
        this.parentChildIndex = parentChildIndex;
        this.metrics = metrics != null ? metrics : new ExecutionMetrics();
        this.lossinessCollector = new LossinessCollector();

        this.bagTransformationService = new BagTransformationService(
                expressionEngine, oidGenerationService, lossinessCollector);
        this.sourceIndexingService = new SourceIndexingService();

        AssignmentExecutionService assignmentExec = new AssignmentExecutionService(
                expressionEngine, geometryAdapter);
        TargetObjectFactory targetFactory = new TargetObjectFactory(
                expressionEngine, oidGenerationService, assignmentExec, bagTransformationService);
        this.ruleExecutionService = new RuleExecutionService(
                expressionEngine, targetFactory, geometryAdapter, referenceIndex, lossinessCollector);
        this.outputWritingService = new OutputWritingService();
    }

    // -- Public API ---------------------------------------------------------

    public TransformResult run(JobConfig config, Function<JobConfig.InputSpec, IoxReader> readerFactory,
                                Map<String, IoxWriter> writersByOutputId) throws Exception {
        return runTypedLegacy(config, readerFactory, writersByOutputId);
    }

    public TransformResult runTyped(TransformPlan plan,
                                     Function<String, IoxReader> readerFactoryById,
                                     Map<String, IoxWriter> writersByOutputId) throws Exception {
        dispatchIndex = RuleDispatchIndex.build(plan);

        sourceIndexingService.indexSources(plan, readerFactoryById, dispatchIndex,
                stateStore, sourceLookupIndex, parentChildIndex, diagnostics, metrics);

        expandedTargets = new LinkedHashMap<>();
        RuleExecutionService.RuleExecutionResult execResult = ruleExecutionService.executeRules(
                plan, dispatchIndex, stateStore, sourceLookupIndex, parentChildIndex, diagnostics, metrics);

        if (referenceResolutionService != null && referenceIndex != null) {
            referenceResolutionService.resolveAll(plan, stateStore, referenceIndex, diagnostics);
        } else {
            resolveDeferredRefs(plan);
            checkRequiredRefs(plan);
        }

        long written = outputWritingService.writeOutputs(writersByOutputId,
                execResult.objectsByOutputAndBasket());

        ExecutionMetricsSnapshot snapshot = metrics.snapshot();
        return buildResult(plan, written, snapshot);
    }

    public ExecutionMetricsSnapshot getMetricsSnapshot() {
        return metrics.snapshot();
    }

    public LossinessCollector getLossinessCollector() {
        return lossinessCollector;
    }

    // -- Legacy APIs (kept for backward compatibility) ----------------------

    private TransformResult runTypedLegacy(JobConfig config,
                                            Function<JobConfig.InputSpec, IoxReader> readerFactory,
                                            Map<String, IoxWriter> writersByOutputId) throws Exception {
        pass1IndexLegacy(config, readerFactory);
        Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket = pass2BuildTargetsLegacy(config);
        resolveDeferredRefs(null);
        long written = writeOutputsLegacy(config, writersByOutputId, objectsByOutputAndBasket);
        return buildResultLegacy(written);
    }

    private void pass1IndexLegacy(JobConfig config,
                                   Function<JobConfig.InputSpec, IoxReader> readerFactory) throws Exception {
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
                    if (event instanceof EndTransferEvent) break;
                    if (event instanceof ObjectEvent obj) {
                        IomObject source = obj.getIomObject();
                        stateStore.indexSourceObject(source.getobjecttag(), input.id, basketId, source);
                        stateStore.addSourceRecord(new SourceRecord(input.id, basketId,
                                source.getobjecttag(), source));
                    }
                }
            } finally {
                reader.close();
            }
        }
    }

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
                                null,
                                null));
                    }
                }

                stateStore.putIdMapping(
                        new SourceRefKey(record.sourceClass(), record.sourceObject().getobjectoid(),
                                record.sourceFileId(), record.sourceBasketId()),
                        new TargetRefValue(rule.getEffectiveTargetClass(), target.getobjectoid(),
                                rule.getEffectiveTargetOutput(), record.sourceBasketId()));
                stateStore.putIdMapping(
                        new SourceRefKey(null, record.sourceObject().getobjectoid(), null, null),
                        new TargetRefValue(rule.getEffectiveTargetClass(), target.getobjectoid(),
                                rule.getEffectiveTargetOutput(), record.sourceBasketId()));
                stateStore.indexTargetObject(rule.getEffectiveTargetClass(), target.getobjectoid(), target);

                String basketKey = basketKey(extractTopic(rule.getEffectiveTargetClass()),
                        record.sourceBasketId());
                objectsByOutputAndBasket
                        .computeIfAbsent(rule.getEffectiveTargetOutput(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(basketKey, ignored -> new ArrayList<>())
                        .add(target);
            }
        }
        return objectsByOutputAndBasket;
    }

    private long writeOutputsLegacy(JobConfig config, Map<String, IoxWriter> writersByOutputId,
                                     Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket)
            throws Exception {
        long written = 0;
        for (JobConfig.OutputSpec output : config.job.outputs) {
            IoxWriter writer = writersByOutputId.get(output.id);
            writer.write(new ch.interlis.iox_j.StartTransferEvent("ilitransformer", null, null));
            Map<String, List<IomObject>> byBasket = objectsByOutputAndBasket.getOrDefault(output.id, Map.of());
            for (var basketEntry : byBasket.entrySet()) {
                String[] parts = basketEntry.getKey().split("::", 2);
                String topic = parts[0];
                String basketId = parts.length > 1 ? parts[1] : null;
                writer.write(new ch.interlis.iox_j.StartBasketEvent(topic, basketId));
                List<IomObject> sorted = new ArrayList<>(basketEntry.getValue());
                sorted.sort(OutputWritingService.targetObjectComparator());
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

    // -- Reference resolution (legacy) --------------------------------------

    private void resolveDeferredRefs(TransformPlan plan) {
        for (DeferredRef deferredRef : stateStore.deferredRefs()) {
            String refOid = deferredRef.sourceReferencedOid();
            if (refOid != null && refOid.startsWith("#")) {
                resolveSingletonRef(deferredRef, plan);
                continue;
            }
            List<TargetRefValue> candidates = stateStore.findIdMappings(
                    null, deferredRef.sourceReferencedOid(),
                    deferredRef.sourceFileId(), deferredRef.sourceBasketId());
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
            if (deferredRef.expectedTargetClass() != null && !deferredRef.expectedTargetClass().isEmpty()
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
            stateStore.findTargetObject(deferredRef.ownerTargetClass(), deferredRef.ownerTargetOid())
                    .ifPresent(owner -> {
                        IomObject ref = owner.addattrobj(deferredRef.ownerAttribute(), Iom_jObject.REF);
                        ref.setobjectrefoid(resolved.targetOid());
                    });
        }
    }

    private void resolveSingletonRef(DeferredRef deferredRef, TransformPlan plan) {
        diagnostics.add(new Diagnostic(DiagnosticCode.RUN_REF_UNRESOLVED,
                failPolicySeverity(plan, Severity.WARNING),
                "Singleton reference " + deferredRef.sourceReferencedOid()
                        + " cannot be resolved in legacy mode (use reference index)",
                deferredRef.ownerTargetClass() + "/" + deferredRef.ownerTargetOid(),
                "Ensure reference index is enabled"));
    }

    private void checkRequiredRefs(TransformPlan plan) {
        if (plan == null) return;
        for (RulePlan rule : plan.rules()) {
            OutputBinding outputBinding = plan.outputsById().get(rule.outputId());
            TypeSystemFacade targetTs = outputBinding != null ? outputBinding.typeSystem() : null;
            if (targetTs == null) continue;
            String targetClassScoped = TypeSystemFacade.getScopedName(rule.targetClass());
            // Skip rules that produced no targets (no deferred refs for this class)
            boolean ruleExecuted = stateStore.deferredRefs().stream()
                    .anyMatch(dr -> dr.ownerTargetClass().equals(targetClassScoped));
            if (!ruleExecuted) continue;
            for (var ref : rule.refs()) {
                if (!ref.required()) continue;
                RoleResolver roleResolver = new RoleResolver(targetTs);
                long minCardinality = roleResolver.getTargetRoleCardinality(ref, targetClassScoped).min();
                if (minCardinality <= 0) continue;
                boolean hasResolved = stateStore.deferredRefs().stream()
                        .anyMatch(dr -> dr.ownerTargetClass().equals(targetClassScoped)
                                && dr.ownerAttribute().equals(ref.targetRoleName())
                                && stateStore.findIdMappings(
                                        null, dr.sourceReferencedOid(), null, null).size() == 1);
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

    // -- Helper methods shared between legacy and new paths ------------------

    private TransformResult buildResult(TransformPlan plan, long written,
                                         ExecutionMetricsSnapshot snapshot) {
        long errors = 0;
        long warnings = 0;
        for (Diagnostic d : diagnostics.all()) {
            if (d.severity() == Severity.ERROR) errors++;
            else if (d.severity() == Severity.WARNING) warnings++;
        }
        return new TransformResult(snapshot.sourceRecordsRead(), snapshot.sourceRecordsFiltered(),
                snapshot.targetsCreated(), written, errors, warnings,
                plan != null ? plan.oidPlan().defaultStrategy().name() : "integer",
                plan != null ? plan.basketPlan().defaultStrategy().name() : "preserve");
    }

    private TransformResult buildResultLegacy(long written) {
        long errors = 0;
        long warnings = 0;
        for (Diagnostic d : diagnostics.all()) {
            if (d.severity() == Severity.ERROR) errors++;
            else if (d.severity() == Severity.WARNING) warnings++;
        }
        return new TransformResult(metrics.getSourceRecordsRead(),
                metrics.getSourceRecordsFiltered(),
                metrics.getTargetsCreated(), written,
                errors, warnings, "integer", "preserve");
    }

    private boolean isRefRequired(TransformPlan plan, DeferredRef deferredRef) {
        if (plan == null) return false;
        for (RulePlan rule : plan.rules()) {
            if (!TypeSystemFacade.getScopedName(rule.targetClass()).equals(deferredRef.ownerTargetClass()))
                continue;
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
        return plan.failPolicy() == FailPolicy.LENIENT ? Severity.WARNING : defaultSeverity;
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

    private record RefCall(String alias, String roleName) {}

    private RefCall parseRefCall(String expr) {
        if (expr == null) return null;
        String trimmed = expr.trim();
        if (!trimmed.startsWith("ref(") || !trimmed.endsWith(")")) return null;
        String argsPart = trimmed.substring(4, trimmed.length() - 1);
        String[] args = argsPart.split(",", 2);
        if (args.length != 2) return null;
        return new RefCall(stripQuotes(args[0].trim()), stripQuotes(args[1].trim()));
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String extractTopic(String qualifiedClassName) {
        String[] parts = qualifiedClassName.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return qualifiedClassName;
    }

    private static String basketKey(String topic, String basketId) {
        return topic + "::" + (basketId == null ? "" : basketId);
    }
}
