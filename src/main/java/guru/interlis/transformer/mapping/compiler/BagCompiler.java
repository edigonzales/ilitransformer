package guru.interlis.transformer.mapping.compiler;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Table;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.ExpressionCompiler;
import guru.interlis.transformer.expr.FunctionCallExpr;
import guru.interlis.transformer.expr.PathExpr;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.JobConfigNormalizer;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.IdentityPlan;
import guru.interlis.transformer.mapping.plan.RefPlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.OidStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BagCompiler {

    List<BagPlan> compileBags(JobConfig.RuleSpec rule, List<SourcePlan> sourcePlans,
                               Map<String, SourcePlan> sourcesByAlias,
                               Table targetClass, TypeSystemFacade targetTs,
                               String ruleId, CompilerContext ctx,
                               String fallbackParentAlias) {
        if (rule.bags == null || rule.bags.isEmpty()) {
            return List.of();
        }

        DiagnosticCollector diag = ctx.diagnostics();
        List<BagPlan> bagPlans = new ArrayList<>();
        for (var entry : rule.bags.entrySet()) {
            String bagAttrName = entry.getKey();
            JobConfig.BagSpec bagSpec = entry.getValue();

            BagPlan.BagMode mode = "expand".equalsIgnoreCase(bagSpec.mode)
                    ? BagPlan.BagMode.EXPAND : BagPlan.BagMode.EMBED;

            Table componentTable;
            String effectiveStructureName;

            if (mode == BagPlan.BagMode.EXPAND) {
                String structClass = bagSpec.structure;
                if (structClass == null || structClass.isBlank()) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_TARGET_CLASS, Severity.ERROR,
                            "EXPAND bag requires structure to specify the target class",
                            ruleId, "Specify the target class in bags.<name>.structure"));
                    continue;
                }
                String structTsOutput = JobConfigNormalizer.getEffectiveTargetOutput(rule);
                TypeSystemFacade structTs = structTsOutput != null && !structTsOutput.isEmpty()
                        ? ctx.modelRegistry().requireTargetTypeSystem(structTsOutput) : null;
                componentTable = structTs != null ? structTs.resolveClass(structClass) : null;
                if (componentTable == null) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_TARGET_CLASS, Severity.ERROR,
                            "EXPAND target class not found for structure: " + structClass,
                            ruleId, "Check the target class in bags.<name>.structure"));
                    continue;
                }
                effectiveStructureName = CompileUtils.getScopedName(componentTable);
            } else {
                AttributeDef bagAttr = targetTs != null ? targetTs.findAttribute(targetClass, bagAttrName) : null;
                if (bagAttr == null) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE, Severity.ERROR,
                            "Bag target attribute not found: " + bagAttrName + " in class " + targetClass.getName(),
                            ruleId, "Check the bag attribute name in the target class"));
                    continue;
                }

                ch.interlis.ili2c.metamodel.Type domain = bagAttr.getDomain();
                if (!(domain instanceof CompositionType compositionType)) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.ERROR,
                            "Bag attribute " + bagAttrName + " is not a structure type",
                            ruleId, "Bag attributes must be BAG OF structures"));
                    continue;
                }

                componentTable = compositionType.getComponentType();
                if (componentTable == null) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.ERROR,
                            "Bag attribute " + bagAttrName + " has no component type",
                            ruleId, "Structure type must have a component"));
                    continue;
                }

                String structureName = bagSpec.structure;
                if (structureName != null && !structureName.isBlank()) {
                    String componentName = CompileUtils.getScopedName(componentTable);
                    if (!componentTable.getName().equals(structureName)
                            && !componentName.equals(structureName)
                            && !componentName.endsWith("." + structureName)) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.WARNING,
                                "Structure name mismatch: YAML specifies '" + structureName
                                        + "' but target attribute uses '" + componentName + "'",
                                ruleId, "The structure is determined by the target model"));
                    }
                }

                effectiveStructureName = CompileUtils.getScopedName(componentTable);
            }

            JobConfig.BagFrom from = bagSpec.from;
            if (from == null || from.clazz == null || from.clazz.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_SOURCE_CLASS, Severity.ERROR,
                        "Bag '" + bagAttrName + "' is missing from.class",
                        ruleId, "Specify the source class in bags.<name>.from.class"));
                continue;
            }
            if (from.alias == null || from.alias.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_ALIAS, Severity.ERROR,
                        "Bag '" + bagAttrName + "' is missing from.alias",
                        ruleId, "Specify an alias in bags.<name>.from.alias"));
                continue;
            }
            if (from.input == null || from.input.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_INPUT, Severity.ERROR,
                        "Bag '" + bagAttrName + "' is missing from.input",
                        ruleId, "Specify the input in bags.<name>.from.input"));
                continue;
            }

            TypeSystemFacade sourceTs = ctx.modelRegistry().requireSourceTypeSystem(from.input);
            Table bagSourceClass = null;
            if (sourceTs != null) {
                bagSourceClass = sourceTs.resolveClass(from.clazz);
            }
            if (bagSourceClass == null) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_SOURCE_CLASS, Severity.ERROR,
                        "Bag source class not found: " + from.clazz,
                        ruleId, "Check the class name in bags.<name>.from.class"));
                continue;
            }

            SourcePlan bagSourcePlan = new SourcePlan(from.alias, bagSourceClass,
                    List.of(from.input), null);

            CompiledExpression bagWhere = null;
            if (from.where != null && !from.where.isBlank()) {
                Map<String, SourcePlan> bagSourcesByAlias = new HashMap<>(sourcesByAlias);
                bagSourcesByAlias.put(from.alias, bagSourcePlan);
                ExpressionCompileContext whereCtx = new ExpressionCompileContext(ruleId,
                        bagSourcesByAlias, TypeInfo.BOOLEAN, ctx.functionRegistry(), ctx.enumMaps());
                bagWhere = ctx.expressionCompiler().compile(from.where, whereCtx, diag);
            }

            bagSourcePlan = new SourcePlan(from.alias, bagSourceClass,
                    List.of(from.input), bagWhere);

            List<AssignmentPlan> bagAssignments = new ArrayList<>();
            Set<String> assignedBagAttrs = new HashSet<>();
            if (bagSpec.assign != null) {
                for (var assignEntry : bagSpec.assign.entrySet()) {
                    String structAttrName = assignEntry.getKey();
                    String expr = assignEntry.getValue();

                    if (!assignedBagAttrs.add(structAttrName)) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_DUPLICATE_TARGET_ASSIGN, Severity.ERROR,
                                "Structure attribute assigned multiple times: " + structAttrName
                                        + " in bag " + bagAttrName,
                                ruleId, "Remove duplicate assignment"));
                    }

                    AttributeDef structAttr = targetTs != null
                            ? targetTs.findAttribute(componentTable, structAttrName) : null;
                    if (structAttr == null) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE, Severity.ERROR,
                                "Structure attribute not found: " + structAttrName
                                        + " in structure " + effectiveStructureName,
                                ruleId, "Check the attribute name in the structure type"));
                    }

                    TypeInfo expectedType = structAttr != null
                            ? ExpressionCompiler.classifyIliAttr(structAttr) : TypeInfo.UNKNOWN;

                    Map<String, SourcePlan> bagCtxSources = new HashMap<>();
                    bagCtxSources.put(from.alias, bagSourcePlan);
                    bagCtxSources.putAll(sourcesByAlias);

                    ExpressionCompileContext compileCtx = new ExpressionCompileContext(ruleId,
                            bagCtxSources, expectedType, ctx.functionRegistry(), ctx.enumMaps());
                    CompiledExpression compiledExpr = ctx.expressionCompiler().compile(expr, compileCtx, diag);

                    AssignmentPlan ap = new AssignmentPlan(structAttrName,
                            structAttr, compiledExpr);
                    bagAssignments.add(ap);
                }
            }

            checkStructureMandatoryCoverage(componentTable, bagAssignments, bagAttrName, ruleId, diag);

            String parentRefAttribute = null;
            String parentAlias = null;
            if (bagSpec.parentRef != null) {
                if (bagSpec.parentRef.attribute != null && !bagSpec.parentRef.attribute.isBlank()) {
                    parentRefAttribute = bagSpec.parentRef.attribute;
                }
                if (bagSpec.parentRef.parentAlias != null && !bagSpec.parentRef.parentAlias.isBlank()) {
                    parentAlias = bagSpec.parentRef.parentAlias;
                }
            }
            if (parentRefAttribute == null && bagWhere != null
                    && bagWhere.ast() instanceof FunctionCallExpr fce
                    && "refEquals".equals(fce.functionName())
                    && fce.arguments().size() == 2
                    && fce.arguments().get(0) instanceof PathExpr first
                    && fce.arguments().get(1) instanceof PathExpr second) {
                parentRefAttribute = first.attributeName();
                if (parentAlias == null) {
                    parentAlias = second.alias();
                }
            }
            if (parentAlias == null && fallbackParentAlias != null && !fallbackParentAlias.isBlank()) {
                parentAlias = fallbackParentAlias;
            }
            if (parentAlias == null && !sourcePlans.isEmpty()) {
                parentAlias = sourcePlans.get(0).alias();
            }
            if (parentRefAttribute == null && parentAlias == null) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_BAG_PARENT_REF_MISSING, Severity.WARNING,
                        "Bag '" + bagAttrName + "' has no parent reference attribute. "
                                + "This may cause O(n\u00B2) full scans at runtime.",
                        ruleId, "Set bags.<name>.parentRef.attribute for indexed lookup"));
            }

            Integer cardinalityMin = null;
            Integer cardinalityMax = null;
            if (mode == BagPlan.BagMode.EMBED
                    && targetTs != null
                    && targetTs.findAttribute(targetClass, bagAttrName) != null) {
                var bagAttr2 = targetTs.findAttribute(targetClass, bagAttrName);
                if (bagAttr2 != null && bagAttr2.getDomain() instanceof CompositionType ct
                        && ct.getCardinality() instanceof Cardinality card) {
                    cardinalityMin = card.getMinimum() >= 0 ? Math.toIntExact(card.getMinimum()) : 0;
                    cardinalityMax = card.getMaximum() < Long.MAX_VALUE
                            ? Math.toIntExact(card.getMaximum()) : null;
                }
            }

            IdentityPlan bagIdentityPlan = null;
            if (mode == BagPlan.BagMode.EXPAND) {
                OidStrategy expandOidStrategy = OidStrategy.INTEGER;
                String expandNamespace = null;
                List<String> bagIdentitySourceKeys = new IdentityCompiler().compileIdentityKeys(rule, sourcePlans, ctx);
                if (bagIdentitySourceKeys.isEmpty()) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_BAG_EXPAND_IDENTITY_MISSING, Severity.WARNING,
                            "EXPAND bag '" + bagAttrName + "' has no identity source keys; "
                                    + "OIDs will be generated from parent + index.",
                            ruleId, "Consider setting identity keys via rule identity.sourceKey"));
                }
                bagIdentityPlan = new IdentityPlan(expandOidStrategy, expandNamespace, bagIdentitySourceKeys);
            }

            RefPlan parentRefPlan = null;
            if (mode == BagPlan.BagMode.EXPAND && bagSpec.parentRef != null
                    && bagSpec.parentRef.role != null && !bagSpec.parentRef.role.isBlank()) {
                parentRefPlan = new RefPlan(bagSpec.parentRef.role, bagSpec.parentRef.association,
                        bagSpec.parentRef.parentAlias != null ? bagSpec.parentRef.parentAlias : parentAlias,
                        ruleId, true);
            }

            List<BagPlan> nestedBagPlans = List.of();
            if (bagSpec.nestedBags != null && !bagSpec.nestedBags.isEmpty()) {
                JobConfig.RuleSpec nestedRule = new JobConfig.RuleSpec();
                nestedRule.id = ruleId + "-nested-" + bagAttrName;
                nestedRule.output = JobConfigNormalizer.getEffectiveTargetOutput(rule);
                nestedRule.identity = rule.identity;
                nestedRule.bags = bagSpec.nestedBags;
                Map<String, SourcePlan> nestedSourcesByAlias = new HashMap<>(sourcesByAlias);
                nestedSourcesByAlias.put(bagSourcePlan.alias(), bagSourcePlan);
                nestedBagPlans = compileBags(nestedRule, sourcePlans, nestedSourcesByAlias,
                        componentTable, targetTs, ruleId, ctx, bagSourcePlan.alias());
            }

            BagPlan bp = new BagPlan(bagAttrName, bagSourcePlan, effectiveStructureName,
                    bagAssignments, bagWhere, mode, parentRefAttribute, parentAlias,
                    cardinalityMin, cardinalityMax, bagSpec.maxItems, bagIdentityPlan, parentRefPlan,
                    nestedBagPlans);
            bagPlans.add(bp);
        }
        return bagPlans;
    }

    private void checkStructureMandatoryCoverage(Table componentTable,
                                                  List<AssignmentPlan> assignments,
                                                  String bagAttrName, String ruleId,
                                                  DiagnosticCollector diag) {
        Set<String> assigned = new HashSet<>();
        for (AssignmentPlan ap : assignments) {
            assigned.add(ap.targetAttrName());
        }

        Iterator<Extendable> it = componentTable.getAttributes();
        while (it.hasNext()) {
            Extendable ext = it.next();
            if (ext instanceof AttributeDef attr) {
                var card = attr.getCardinality();
                if (card != null && card.getMinimum() > 0) {
                    if (!assigned.contains(attr.getName())) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_MANDATORY_MISSING, Severity.WARNING,
                                "Mandatory structure attribute not assigned: " + attr.getName()
                                        + " in structure " + componentTable.getName()
                                        + " (bag " + bagAttrName + ")",
                                ruleId, "Add an assignment or specify a default value"));
                    }
                }
            }
        }
    }
}
