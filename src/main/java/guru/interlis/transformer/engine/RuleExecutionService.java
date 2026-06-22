package guru.interlis.transformer.engine;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.expr.FunctionCallExpr;
import guru.interlis.transformer.expr.PathExpr;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.loss.LossEvent;
import guru.interlis.transformer.loss.LossinessCollector;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.CreatePlan;
import guru.interlis.transformer.mapping.plan.JoinCardinality;
import guru.interlis.transformer.mapping.plan.JoinPlan;
import guru.interlis.transformer.mapping.plan.JoinType;
import guru.interlis.transformer.mapping.plan.RuleDependencyGraph;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.CanonicalValue;
import guru.interlis.transformer.state.LookupKey;
import guru.interlis.transformer.state.ParentChildIndex;
import guru.interlis.transformer.state.ReferenceIndex;
import guru.interlis.transformer.state.SourceLookupIndex;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.state.StateStore;

import ch.interlis.iom.IomObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuleExecutionService {

    private final ExpressionEngine expressionEngine;
    private final TargetObjectFactory targetObjectFactory;
    private final GeometryAdapter geometryAdapter;
    private final ReferenceIndex referenceIndex;
    private final LossinessCollector lossinessCollector;

    public RuleExecutionService(
            ExpressionEngine expressionEngine,
            TargetObjectFactory targetObjectFactory,
            GeometryAdapter geometryAdapter) {
        this(expressionEngine, targetObjectFactory, geometryAdapter, null);
    }

    public RuleExecutionService(
            ExpressionEngine expressionEngine,
            TargetObjectFactory targetObjectFactory,
            GeometryAdapter geometryAdapter,
            ReferenceIndex referenceIndex) {
        this(expressionEngine, targetObjectFactory, geometryAdapter, referenceIndex, null);
    }

    public RuleExecutionService(
            ExpressionEngine expressionEngine,
            TargetObjectFactory targetObjectFactory,
            GeometryAdapter geometryAdapter,
            ReferenceIndex referenceIndex,
            LossinessCollector lossinessCollector) {
        this.expressionEngine = expressionEngine;
        this.targetObjectFactory = targetObjectFactory;
        this.geometryAdapter = geometryAdapter;
        this.referenceIndex = referenceIndex;
        this.lossinessCollector = lossinessCollector;
    }

    public RuleExecutionResult executeRules(
            TransformPlan plan,
            RuleDispatchIndex dispatchIndex,
            StateStore stateStore,
            SourceLookupIndex sourceLookupIndex,
            ParentChildIndex parentChildIndex,
            DiagnosticCollector diagnostics,
            ExecutionMetrics metrics) {

        Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket = new LinkedHashMap<>();
        Map<String, Map<String, List<IomObject>>> expandedTargets = new LinkedHashMap<>();

        Map<String, Map<String, TypeInfo>> sourceAttrTypes = buildSourceAttributeTypeMap(plan);

        RuleDependencyGraph depGraph = new RuleDependencyGraph(plan.rules());
        List<String> orderedRuleIds = depGraph.topologicalOrder();
        Map<String, RulePlan> rulesById = new HashMap<>();
        for (RulePlan rp : plan.rules()) {
            rulesById.put(rp.ruleId(), rp);
        }

        List<List<String>> cycles = depGraph.cycles();
        if (!cycles.isEmpty()) {
            for (List<String> cycle : cycles) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_CYCLIC_DEPENDENCY,
                        Severity.ERROR,
                        "Cyclic rule dependency detected: " + cycle,
                        null,
                        "Break the cycle by restructuring refs or create directives"));
            }
        }

        for (String ruleId : orderedRuleIds) {
            RulePlan rule = rulesById.get(ruleId);
            if (rule == null) continue;

            if (!rule.joins().isEmpty()) {
                processJoinedRule(
                        rule,
                        plan,
                        dispatchIndex,
                        stateStore,
                        sourceLookupIndex,
                        parentChildIndex,
                        objectsByOutputAndBasket,
                        sourceAttrTypes,
                        diagnostics,
                        metrics);
            } else {
                processSingleSourceRule(
                        rule,
                        plan,
                        dispatchIndex,
                        stateStore,
                        objectsByOutputAndBasket,
                        expandedTargets,
                        sourceAttrTypes,
                        diagnostics,
                        metrics,
                        parentChildIndex,
                        sourceLookupIndex);
            }

            for (CreatePlan create : rule.creates()) {
                processCreatePlan(
                        create,
                        rule,
                        plan,
                        stateStore,
                        objectsByOutputAndBasket,
                        sourceAttrTypes,
                        diagnostics,
                        metrics,
                        parentChildIndex,
                        sourceLookupIndex);
            }
        }

        for (var entry : expandedTargets.entrySet()) {
            String outputId = entry.getKey();
            for (var basketEntry : entry.getValue().entrySet()) {
                objectsByOutputAndBasket
                        .computeIfAbsent(outputId, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(basketEntry.getKey(), ignored -> new java.util.ArrayList<>())
                        .addAll(basketEntry.getValue());
            }
        }

        return new RuleExecutionResult(objectsByOutputAndBasket);
    }

    private void processSingleSourceRule(
            RulePlan rule,
            TransformPlan plan,
            RuleDispatchIndex dispatchIndex,
            StateStore stateStore,
            Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket,
            Map<String, Map<String, List<IomObject>>> expandedTargets,
            Map<String, Map<String, TypeInfo>> sourceAttrTypes,
            DiagnosticCollector diagnostics,
            ExecutionMetrics metrics,
            ParentChildIndex parentChildIndex,
            SourceLookupIndex sourceLookupIndex) {
        TargetObjectFactory.ObjectCreationContext ctx = new TargetObjectFactory.ObjectCreationContext(
                objectsByOutputAndBasket,
                expandedTargets,
                diagnostics,
                stateStore,
                referenceIndex,
                parentChildIndex,
                metrics,
                geometryAdapter,
                sourceAttrTypes,
                sourceLookupIndex);

        for (SourcePlan source : rule.sources()) {
            if (source.sourceClass() == null) continue;
            String scopedClass = TargetObjectFactory.getScopedName(source.sourceClass());
            for (String inputId : source.inputIds()) {
                for (SourceRecord record : stateStore.sourceRecords(inputId, scopedClass)) {
                    Map<String, IomObject> sources = Map.of(source.alias(), record.sourceObject());
                    EvalContext evalCtx = new EvalContext(
                                    sources,
                                    diagnostics,
                                    rule.ruleId(),
                                    plan.enumMaps(),
                                    geometryAdapter,
                                    sourceAttrTypes)
                            .withLookupIndex(sourceLookupIndex);

                    if (!evaluateWhereAndPredicate(source, rule, evalCtx, expressionEngine, metrics)) {
                        recordLosses(rule, record, evalCtx);
                        continue;
                    }

                    metrics.recordRuleMatch(rule.ruleId());
                    recordLosses(rule, record, evalCtx);
                    int created = targetObjectFactory.createTarget(rule, source, record, evalCtx, plan, ctx);
                    metrics.recordTarget(TargetObjectFactory.getScopedName(rule.targetClass()));
                }
            }
        }
    }

    private void processJoinedRule(
            RulePlan rule,
            TransformPlan plan,
            RuleDispatchIndex dispatchIndex,
            StateStore stateStore,
            SourceLookupIndex sourceLookupIndex,
            ParentChildIndex parentChildIndex,
            Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket,
            Map<String, Map<String, TypeInfo>> sourceAttrTypes,
            DiagnosticCollector diagnostics,
            ExecutionMetrics metrics) {
        TargetObjectFactory.ObjectCreationContext ctx = new TargetObjectFactory.ObjectCreationContext(
                objectsByOutputAndBasket,
                new LinkedHashMap<>(),
                diagnostics,
                stateStore,
                referenceIndex,
                parentChildIndex,
                metrics,
                geometryAdapter,
                sourceAttrTypes,
                sourceLookupIndex);

        List<JoinPlan> joins = rule.joins();
        SourcePlan driverPlan = joins.get(0).left();
        for (SourceRecord driverRecord : stateStore.sourceRecords()) {
            if (!sourceMatchesPlan(driverRecord, driverPlan)) continue;

            Map<String, SourceRecord> boundRecords = new LinkedHashMap<>();
            boundRecords.put(driverPlan.alias(), driverRecord);

            processJoinChain(
                    rule,
                    plan,
                    joins,
                    0,
                    driverPlan,
                    driverRecord,
                    boundRecords,
                    stateStore,
                    sourceLookupIndex,
                    sourceAttrTypes,
                    diagnostics,
                    metrics,
                    ctx);
        }
    }

    private void processJoinChain(
            RulePlan rule,
            TransformPlan plan,
            List<JoinPlan> joins,
            int joinIndex,
            SourcePlan driverPlan,
            SourceRecord driverRecord,
            Map<String, SourceRecord> boundRecords,
            StateStore stateStore,
            SourceLookupIndex sourceLookupIndex,
            Map<String, Map<String, TypeInfo>> sourceAttrTypes,
            DiagnosticCollector diagnostics,
            ExecutionMetrics metrics,
            TargetObjectFactory.ObjectCreationContext ctx) {
        if (joinIndex >= joins.size()) {
            EvalContext evalCtx = new EvalContext(
                            toSourceObjects(boundRecords),
                            diagnostics,
                            rule.ruleId(),
                            plan.enumMaps(),
                            geometryAdapter,
                            sourceAttrTypes)
                    .withLookupIndex(sourceLookupIndex);
            if (!evaluateWhereAndPredicate(driverPlan, rule, evalCtx, expressionEngine, metrics)) {
                return;
            }

            metrics.recordRuleMatch(rule.ruleId());
            recordLosses(rule, driverRecord, evalCtx);
            targetObjectFactory.createTarget(rule, driverPlan, driverRecord, evalCtx, plan, ctx);
            metrics.recordTarget(TargetObjectFactory.getScopedName(rule.targetClass()));
            return;
        }

        JoinPlan join = joins.get(joinIndex);
        JoinConditionParts joinParts = resolveJoinConditionParts(join);
        SourceRecord leftRecord = boundRecords.get(join.left().alias());
        if (leftRecord == null) {
            metrics.recordFiltered();
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.RUN_JOIN_MISSING,
                    Severity.WARNING,
                    "Join source alias '" + join.left().alias() + "' is not bound in join chain. Rule: "
                            + rule.ruleId(),
                    rule.ruleId(),
                    "Order joins so that each left alias is bound by an earlier join"));
            return;
        }

        String leftAttrValue = readAttributeOrRefOid(leftRecord.sourceObject(), joinParts.leftAttr());
        if (leftAttrValue == null) {
            if (join.type() == JoinType.INNER) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.RUN_JOIN_MISSING,
                        Severity.WARNING,
                        "Join key attribute '" + joinParts.leftAttr() + "' is null in left source. Rule: "
                                + rule.ruleId(),
                        rule.ruleId(),
                        "Left join or provide non-null join keys"));
                metrics.recordFiltered();
                return;
            }
            processJoinChain(
                    rule,
                    plan,
                    joins,
                    joinIndex + 1,
                    driverPlan,
                    driverRecord,
                    boundRecords,
                    stateStore,
                    sourceLookupIndex,
                    sourceAttrTypes,
                    diagnostics,
                    metrics,
                    ctx);
            return;
        }

        List<SourceRecord> rightMatches =
                findRightMatches(join, joinParts, leftAttrValue, boundRecords, stateStore, sourceLookupIndex, metrics);

        if (rightMatches.isEmpty()) {
            if (join.type() == JoinType.INNER) {
                metrics.recordFiltered();
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.RUN_JOIN_MISSING,
                        Severity.WARNING,
                        "No matching right source record for join key " + joinParts.leftAttr() + " = " + leftAttrValue
                                + ". Rule: " + rule.ruleId(),
                        rule.ruleId(),
                        "Ensure matching records exist or use LEFT join"));
                return;
            }
            processJoinChain(
                    rule,
                    plan,
                    joins,
                    joinIndex + 1,
                    driverPlan,
                    driverRecord,
                    boundRecords,
                    stateStore,
                    sourceLookupIndex,
                    sourceAttrTypes,
                    diagnostics,
                    metrics,
                    ctx);
            return;
        }

        if (rightMatches.size() > 1
                && (join.expectedCardinality() == JoinCardinality.ONE_TO_ONE
                        || join.expectedCardinality() == JoinCardinality.MANY_TO_ONE)) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.RUN_JOIN_AMBIGUOUS,
                    Severity.WARNING,
                    "Expected " + join.expectedCardinality() + " but found " + rightMatches.size()
                            + " matching right records for join key. Rule: " + rule.ruleId(),
                    rule.ruleId(),
                    "Use MANY_TO_MANY or ONE_TO_MANY cardinality"));
        }

        for (SourceRecord rightRecord : rightMatches) {
            Map<String, SourceRecord> joinedRecords = new LinkedHashMap<>(boundRecords);
            joinedRecords.put(join.right().alias(), rightRecord);

            EvalContext joinCtx = new EvalContext(
                            toSourceObjects(joinedRecords),
                            diagnostics,
                            rule.ruleId(),
                            plan.enumMaps(),
                            geometryAdapter,
                            sourceAttrTypes)
                    .withLookupIndex(sourceLookupIndex);

            Value joinResult = expressionEngine.evaluate(join.condition(), joinCtx);
            if (!isFilterTruthy(joinResult)) {
                metrics.recordFiltered();
                continue;
            }

            processJoinChain(
                    rule,
                    plan,
                    joins,
                    joinIndex + 1,
                    driverPlan,
                    driverRecord,
                    joinedRecords,
                    stateStore,
                    sourceLookupIndex,
                    sourceAttrTypes,
                    diagnostics,
                    metrics,
                    ctx);
        }
    }

    private List<SourceRecord> findRightMatches(
            JoinPlan join,
            JoinConditionParts joinParts,
            String leftAttrValue,
            Map<String, SourceRecord> boundRecords,
            StateStore stateStore,
            SourceLookupIndex sourceLookupIndex,
            ExecutionMetrics metrics) {
        SourceRecord alreadyBound = boundRecords.get(join.right().alias());
        if (alreadyBound != null) {
            return List.of(alreadyBound);
        }

        metrics.recordJoinLookup();
        if (joinParts.rightIsObject()) {
            LookupKey lookupKey = new LookupKey(
                    null,
                    TargetObjectFactory.getScopedName(join.right().sourceClass()),
                    SourceLookupIndex.OBJECT_OID_ATTRIBUTE,
                    new CanonicalValue("text", leftAttrValue, true));
            return sourceLookupIndex.lookup(lookupKey).stream()
                    .filter(candidate -> sourceMatchesPlan(candidate, join.right()))
                    .toList();
        }

        LookupKey lookupKey = new LookupKey(
                null,
                TargetObjectFactory.getScopedName(join.right().sourceClass()),
                joinParts.rightAttr(),
                new CanonicalValue("text", leftAttrValue, true));
        return sourceLookupIndex.lookup(lookupKey).stream()
                .filter(candidate -> join.right().inputIds().isEmpty()
                        || join.right().inputIds().contains(candidate.sourceFileId()))
                .toList();
    }

    private static JoinConditionParts resolveJoinConditionParts(JoinPlan join) {
        FunctionCallExpr call = (FunctionCallExpr) join.condition().ast();
        PathExpr first = (PathExpr) call.arguments().get(0);
        PathExpr second = (PathExpr) call.arguments().get(1);

        PathExpr leftPath = join.left().alias().equals(first.alias()) ? first : second;
        PathExpr rightPath = join.right().alias().equals(first.alias()) ? first : second;
        return new JoinConditionParts(
                leftPath.attributeName(), rightPath.attributeName(), rightPath.attributeName() == null);
    }

    private static Map<String, IomObject> toSourceObjects(Map<String, SourceRecord> boundRecords) {
        Map<String, IomObject> sources = new LinkedHashMap<>();
        for (Map.Entry<String, SourceRecord> entry : boundRecords.entrySet()) {
            sources.put(entry.getKey(), entry.getValue().sourceObject());
        }
        return sources;
    }

    private record JoinConditionParts(String leftAttr, String rightAttr, boolean rightIsObject) {}

    private static String readAttributeOrRefOid(IomObject obj, String attrName) {
        if (attrName == null || attrName.isBlank()) return obj.getobjectoid();
        String scalar = obj.getattrvalue(attrName);
        if (scalar != null) return scalar;
        if (obj.getattrvaluecount(attrName) > 0) {
            IomObject refObj = obj.getattrobj(attrName, 0);
            if (refObj != null && refObj.getobjectrefoid() != null) {
                return refObj.getobjectrefoid();
            }
        }
        return null;
    }

    private void processCreatePlan(
            CreatePlan create,
            RulePlan parentRule,
            TransformPlan plan,
            StateStore stateStore,
            Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket,
            Map<String, Map<String, TypeInfo>> sourceAttrTypes,
            DiagnosticCollector diagnostics,
            ExecutionMetrics metrics,
            ParentChildIndex parentChildIndex,
            SourceLookupIndex sourceLookupIndex) {
        TargetObjectFactory.ObjectCreationContext ctx = new TargetObjectFactory.ObjectCreationContext(
                objectsByOutputAndBasket,
                new LinkedHashMap<>(),
                diagnostics,
                stateStore,
                referenceIndex,
                parentChildIndex,
                metrics,
                geometryAdapter,
                sourceAttrTypes,
                sourceLookupIndex);

        for (SourceRecord record : stateStore.sourceRecords()) {
            SourcePlan sp = findSourcePlan(parentRule, record);
            if (sp == null) continue;

            Map<String, IomObject> sources = Map.of(sp.alias(), record.sourceObject());
            EvalContext evalCtx = new EvalContext(
                            sources, diagnostics, parentRule.ruleId(), plan.enumMaps(), null, sourceAttrTypes)
                    .withLookupIndex(sourceLookupIndex);

            targetObjectFactory.createTargetForCreatePlan(create, parentRule, record, evalCtx, plan, ctx);
            metrics.recordTarget(TargetObjectFactory.getScopedName(create.targetClass()));
        }
    }

    private static SourcePlan findSourcePlan(RulePlan rule, SourceRecord record) {
        for (SourcePlan sp : rule.sources()) {
            if (sp.sourceClass() == null) continue;
            if (!sp.inputIds().contains(record.sourceFileId())) continue;
            if (TypeSystemFacade.getScopedName(sp.sourceClass()).equals(record.sourceClass())) {
                return sp;
            }
        }
        return null;
    }

    private static boolean sourceMatchesPlan(SourceRecord record, SourcePlan plan) {
        if (plan.sourceClass() == null) return false;
        if (!plan.inputIds().isEmpty() && !plan.inputIds().contains(record.sourceFileId())) return false;
        return TypeSystemFacade.getScopedName(plan.sourceClass()).equals(record.sourceClass());
    }

    private static boolean evaluateWhereAndPredicate(
            SourcePlan matchedSource,
            RulePlan rule,
            EvalContext evalCtx,
            ExpressionEngine engine,
            ExecutionMetrics metrics) {
        if (matchedSource.where() != null
                && matchedSource.where().sourceText() != null
                && !matchedSource.where().sourceText().isBlank()) {
            Value whereResult = engine.evaluate(matchedSource.where(), evalCtx);
            if (!isFilterTruthy(whereResult)) {
                metrics.recordFiltered();
                return false;
            }
        }
        if (rule.predicate() != null && !rule.predicate().sourceText().isBlank()) {
            Value predResult = engine.evaluate(rule.predicate(), evalCtx);
            if (!isFilterTruthy(predResult)) {
                metrics.recordFiltered();
                return false;
            }
        }
        return true;
    }

    static boolean isFilterTruthy(Value value) {
        if (value == null || value.isNull()) return false;
        if (value instanceof BooleanValue bv) return bv.value();
        if (value instanceof guru.interlis.transformer.expr.TextValue tv)
            return !tv.value().isEmpty();
        if (value instanceof guru.interlis.transformer.expr.NumberValue nv)
            return nv.value().compareTo(java.math.BigDecimal.ZERO) != 0;
        return true;
    }

    private void recordLosses(RulePlan rule, SourceRecord record, EvalContext evalCtx) {
        if (lossinessCollector == null || rule.losses().isEmpty()) {
            return;
        }
        for (var loss : rule.losses()) {
            if (loss.whenExpression() != null
                    && !isFilterTruthy(expressionEngine.evaluate(loss.whenExpression(), evalCtx))) {
                continue;
            }
            lossinessCollector.record(new LossEvent(
                    rule.ruleId(),
                    record.sourceClass(),
                    record.sourceObject().getobjectoid(),
                    loss.sourcePath(),
                    loss.reasonCode(),
                    loss.description()));
        }
    }

    private static Map<String, Map<String, TypeInfo>> buildSourceAttributeTypeMap(TransformPlan plan) {
        Map<String, Map<String, TypeInfo>> result = new LinkedHashMap<>();
        for (RulePlan rule : plan.rules()) {
            for (SourcePlan sp : rule.sources()) {
                if (sp.sourceClass() == null) continue;
                addSourceAttributeTypes(result, sp.alias(), sp.sourceClass());
            }
            for (BagPlan bag : rule.bags()) {
                addBagSourceAttributeTypes(result, bag);
            }
        }
        return result;
    }

    private static void addBagSourceAttributeTypes(Map<String, Map<String, TypeInfo>> result, BagPlan bag) {
        if (bag == null) {
            return;
        }
        if (bag.fromSource() != null && bag.fromSource().sourceClass() != null) {
            addSourceAttributeTypes(
                    result, bag.fromSource().alias(), bag.fromSource().sourceClass());
        }
        for (BagPlan nestedBag : bag.nestedBags()) {
            addBagSourceAttributeTypes(result, nestedBag);
        }
    }

    private static void addSourceAttributeTypes(
            Map<String, Map<String, TypeInfo>> result, String alias, ch.interlis.ili2c.metamodel.Table sourceClass) {
        if (alias == null || sourceClass == null) return;
        Map<String, TypeInfo> aliasTypes = result.computeIfAbsent(alias, ignored -> new LinkedHashMap<>());
        Iterator<ch.interlis.ili2c.metamodel.ViewableTransferElement> it = sourceClass.getAttributesAndRoles2();
        while (it.hasNext()) {
            ch.interlis.ili2c.metamodel.ViewableTransferElement element = it.next();
            if (element.obj instanceof ch.interlis.ili2c.metamodel.AttributeDef attr) {
                if (attr.getName() != null) {
                    aliasTypes.put(attr.getName(), classifyAttributeType(attr));
                }
            } else if (element.obj instanceof ch.interlis.ili2c.metamodel.RoleDef role) {
                if (role.getName() != null) {
                    aliasTypes.put(role.getName(), TypeInfo.REFERENCE);
                }
            }
        }
    }

    private static TypeInfo classifyAttributeType(ch.interlis.ili2c.metamodel.AttributeDef attr) {
        ch.interlis.ili2c.metamodel.Type type = attr.getDomainResolvingAliases();
        if (type == null) type = attr.getDomain();
        if (type == null) return TypeInfo.UNKNOWN;
        if (type instanceof ch.interlis.ili2c.metamodel.CoordType) return TypeInfo.COORD;
        if (type instanceof ch.interlis.ili2c.metamodel.PolylineType) return TypeInfo.POLYLINE;
        if (type instanceof ch.interlis.ili2c.metamodel.AreaType) return TypeInfo.AREA;
        if (type instanceof ch.interlis.ili2c.metamodel.SurfaceOrAreaType) return TypeInfo.SURFACE;
        if (type instanceof ch.interlis.ili2c.metamodel.SurfaceType) return TypeInfo.SURFACE;
        if (type.isBoolean()) return TypeInfo.BOOLEAN;
        if (type instanceof ch.interlis.ili2c.metamodel.NumericType
                || type instanceof ch.interlis.ili2c.metamodel.NumericalType) return TypeInfo.NUMERIC;
        if (type instanceof ch.interlis.ili2c.metamodel.EnumerationType) return TypeInfo.ENUM;
        if (type instanceof ch.interlis.ili2c.metamodel.TextType) return TypeInfo.TEXT;
        if (type instanceof ch.interlis.ili2c.metamodel.CompositionType) return TypeInfo.STRUCTURE;
        if (type instanceof ch.interlis.ili2c.metamodel.ReferenceType) return TypeInfo.REFERENCE;
        return TypeInfo.UNKNOWN;
    }

    public record RuleExecutionResult(Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket) {}
}
