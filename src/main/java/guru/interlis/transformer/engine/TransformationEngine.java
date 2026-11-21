package guru.interlis.transformer.engine;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.geometry.IoxGeometryAdapter;
import guru.interlis.transformer.loss.LossinessCollector;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.state.DefaultOidGenerationService;
import guru.interlis.transformer.state.InMemoryParentChildIndex;
import guru.interlis.transformer.state.InMemoryReferenceIndex;
import guru.interlis.transformer.state.InMemorySourceLookupIndex;
import guru.interlis.transformer.state.OidGenerationService;
import guru.interlis.transformer.state.ParentChildIndex;
import guru.interlis.transformer.state.ReferenceIndex;
import guru.interlis.transformer.state.SourceLookupIndex;
import guru.interlis.transformer.state.StateStore;

import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;

import java.util.Map;
import java.util.function.Function;

public final class TransformationEngine {

    // -- Injected services --------------------------------------------------
    private final StateStore stateStore;
    private final DiagnosticCollector diagnostics;

    // Phase 19 / Phase 26 services
    private final ReferenceIndex referenceIndex;
    private final ReferenceResolutionService referenceResolutionService;
    private final SourceLookupIndex sourceLookupIndex;
    private final ParentChildIndex parentChildIndex;

    // Phase 26 extracted services
    private final SourceIndexingService sourceIndexingService;
    private final RuleExecutionService ruleExecutionService;
    private final OutputWritingService outputWritingService;

    // Phase 26 metrics
    private final ExecutionMetrics metrics;
    private final LossinessCollector lossinessCollector;

    public TransformationEngine(
            ExpressionEngine expressionEngine, StateStore stateStore, DiagnosticCollector diagnostics) {
        this(
                expressionEngine,
                stateStore,
                diagnostics,
                new IoxGeometryAdapter(),
                new DefaultOidGenerationService(),
                new InMemoryReferenceIndex());
    }

    public TransformationEngine(
            ExpressionEngine expressionEngine,
            StateStore stateStore,
            DiagnosticCollector diagnostics,
            GeometryAdapter geometryAdapter) {
        this(
                expressionEngine,
                stateStore,
                diagnostics,
                geometryAdapter,
                new DefaultOidGenerationService(),
                new InMemoryReferenceIndex());
    }

    public TransformationEngine(
            ExpressionEngine expressionEngine,
            StateStore stateStore,
            DiagnosticCollector diagnostics,
            GeometryAdapter geometryAdapter,
            OidGenerationService oidGenerationService) {
        this(
                expressionEngine,
                stateStore,
                diagnostics,
                geometryAdapter,
                oidGenerationService,
                new InMemoryReferenceIndex());
    }

    public TransformationEngine(
            ExpressionEngine expressionEngine,
            StateStore stateStore,
            DiagnosticCollector diagnostics,
            GeometryAdapter geometryAdapter,
            OidGenerationService oidGenerationService,
            ReferenceIndex referenceIndex) {
        this(
                expressionEngine,
                stateStore,
                diagnostics,
                geometryAdapter,
                oidGenerationService,
                referenceIndex,
                new InMemorySourceLookupIndex());
    }

    public TransformationEngine(
            ExpressionEngine expressionEngine,
            StateStore stateStore,
            DiagnosticCollector diagnostics,
            GeometryAdapter geometryAdapter,
            OidGenerationService oidGenerationService,
            ReferenceIndex referenceIndex,
            SourceLookupIndex sourceLookupIndex) {
        this(
                expressionEngine,
                stateStore,
                diagnostics,
                geometryAdapter,
                oidGenerationService,
                referenceIndex,
                sourceLookupIndex,
                new InMemoryParentChildIndex());
    }

    public TransformationEngine(
            ExpressionEngine expressionEngine,
            StateStore stateStore,
            DiagnosticCollector diagnostics,
            GeometryAdapter geometryAdapter,
            OidGenerationService oidGenerationService,
            ReferenceIndex referenceIndex,
            SourceLookupIndex sourceLookupIndex,
            ParentChildIndex parentChildIndex) {
        this(
                expressionEngine,
                stateStore,
                diagnostics,
                geometryAdapter,
                oidGenerationService,
                referenceIndex,
                sourceLookupIndex,
                parentChildIndex,
                new ExecutionMetrics());
    }

    public TransformationEngine(
            ExpressionEngine expressionEngine,
            StateStore stateStore,
            DiagnosticCollector diagnostics,
            GeometryAdapter geometryAdapter,
            OidGenerationService oidGenerationService,
            ReferenceIndex referenceIndex,
            SourceLookupIndex sourceLookupIndex,
            ParentChildIndex parentChildIndex,
            ExecutionMetrics metrics) {
        this.stateStore = stateStore;
        this.diagnostics = diagnostics;
        this.referenceIndex = java.util.Objects.requireNonNull(referenceIndex);
        this.referenceResolutionService = new ReferenceResolutionService();
        this.sourceLookupIndex = sourceLookupIndex;
        this.parentChildIndex = parentChildIndex;
        this.metrics = metrics != null ? metrics : new ExecutionMetrics();
        this.lossinessCollector = new LossinessCollector();

        BagTransformationService bagTransformationService =
                new BagTransformationService(expressionEngine, oidGenerationService, lossinessCollector);
        this.sourceIndexingService = new SourceIndexingService();

        AssignmentExecutionService assignmentExec = new AssignmentExecutionService(expressionEngine, geometryAdapter);
        TargetObjectFactory targetFactory = new TargetObjectFactory(
                expressionEngine, oidGenerationService, assignmentExec, bagTransformationService);
        this.ruleExecutionService = new RuleExecutionService(
                expressionEngine, targetFactory, geometryAdapter, referenceIndex, lossinessCollector);
        this.outputWritingService = new OutputWritingService();
    }

    // -- Public API ---------------------------------------------------------

    private java.util.function.Consumer<String> progress = phase -> {};

    public void setProgressListener(java.util.function.Consumer<String> listener) {
        progress = java.util.Objects.requireNonNull(listener);
    }

    public TransformResult runTyped(
            TransformPlan plan, Function<String, IoxReader> readerFactoryById, Map<String, IoxWriter> writersByOutputId)
            throws Exception {
        RuleDispatchIndex dispatchIndex = RuleDispatchIndex.build(plan);

        ExecutionCancellation.check();
        progress.accept("Indexing sources");
        long sourceIndexStart = System.nanoTime();
        sourceIndexingService.indexSources(
                plan,
                readerFactoryById,
                dispatchIndex,
                stateStore,
                sourceLookupIndex,
                parentChildIndex,
                diagnostics,
                metrics);
        metrics.recordSourceIndexDuration(System.nanoTime() - sourceIndexStart);

        ExecutionCancellation.check();
        progress.accept("Transforming objects");
        long ruleExecutionStart = System.nanoTime();
        RuleExecutionService.RuleExecutionResult execResult = ruleExecutionService.executeRules(
                plan, dispatchIndex, stateStore, sourceLookupIndex, parentChildIndex, diagnostics, metrics);
        metrics.recordRuleExecutionDuration(System.nanoTime() - ruleExecutionStart);

        ExecutionCancellation.check();
        progress.accept("Resolving references");
        long referenceResolutionStart = System.nanoTime();
        referenceResolutionService.resolveAll(
                plan, stateStore, referenceIndex, execResult.objectsByOutputAndBasket(), diagnostics);
        metrics.recordReferenceResolutionDuration(System.nanoTime() - referenceResolutionStart);

        ExecutionCancellation.check();
        progress.accept("Writing transfer");
        long outputWriteStart = System.nanoTime();
        long written = outputWritingService.writeOutputs(writersByOutputId, execResult.objectsByOutputAndBasket());
        metrics.recordOutputWriteDuration(System.nanoTime() - outputWriteStart);
        metrics.recordWritten(written);

        ExecutionMetricsSnapshot snapshot = metrics.snapshot();
        return buildResult(plan, written, snapshot);
    }

    public ExecutionMetricsSnapshot getMetricsSnapshot() {
        return metrics.snapshot();
    }

    public LossinessCollector getLossinessCollector() {
        return lossinessCollector;
    }

    private TransformResult buildResult(TransformPlan plan, long written, ExecutionMetricsSnapshot snapshot) {
        long errors = 0;
        long warnings = 0;
        for (Diagnostic d : diagnostics.all()) {
            if (d.severity() == Severity.ERROR) errors++;
            else if (d.severity() == Severity.WARNING) warnings++;
        }
        return new TransformResult(
                snapshot.sourceRecordsRead(),
                snapshot.sourceRecordsFiltered(),
                snapshot.targetsCreated(),
                written,
                errors,
                warnings,
                plan != null ? plan.oidPlan().defaultStrategy().name() : "integer",
                plan != null ? plan.basketPlan().defaultStrategy().name() : "preserve");
    }
}
