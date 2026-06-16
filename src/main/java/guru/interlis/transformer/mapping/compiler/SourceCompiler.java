package guru.interlis.transformer.mapping.compiler;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Table;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.ModelRegistry;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class SourceCompiler {

    SourcePlan compileSource(JobConfig.SourceSpec src, String ruleId, CompilerContext ctx) {
        if (src.alias == null || src.alias.isBlank()) return null;
        if (src.clazz == null || src.clazz.isBlank()) return null;

        ModelRegistry modelRegistry = ctx.modelRegistry();
        DiagnosticCollector diag = ctx.diagnostics();

        TypeSystemFacade sourceTs = null;
        for (String inputId : src.getInputIds()) {
            try {
                sourceTs = modelRegistry.requireSourceTypeSystem(inputId);
                break;
            } catch (IllegalArgumentException e) {
                // try next input
            }
        }

        Table sourceClass = null;
        if (sourceTs != null) {
            sourceClass = sourceTs.resolveClass(src.clazz);
        }
        if (sourceClass == null) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_SOURCE_CLASS, Severity.ERROR,
                    "Source class not found in any registered model: " + src.clazz,
                    ruleId, "Check the source class name and ensure its model is listed in inputs"));
        }

        return new SourcePlan(src.alias, sourceClass, src.getInputIds(), null);
    }

    List<SourcePlan> compileWhereFilters(List<SourcePlan> sourcePlans,
                                          Map<String, SourcePlan> sourcesByAlias,
                                          String ruleId,
                                          CompilerContext ctx,
                                          List<JobConfig.SourceSpec> sourceSpecs) {
        Map<String, String> whereByAlias = new HashMap<>();
        for (JobConfig.SourceSpec src : sourceSpecs) {
            if (src.where != null && !src.where.isBlank() && src.alias != null) {
                whereByAlias.put(src.alias, src.where);
            }
        }
        if (whereByAlias.isEmpty()) {
            return sourcePlans;
        }

        DiagnosticCollector diag = ctx.diagnostics();
        List<SourcePlan> result = new ArrayList<>();
        for (SourcePlan sp : sourcePlans) {
            String rawWhere = whereByAlias.get(sp.alias());
            CompiledExpression compiledWhere = null;
            if (rawWhere != null) {
                ExpressionCompileContext exprCtx = new ExpressionCompileContext(
                        ruleId, sourcesByAlias, TypeInfo.BOOLEAN, ctx.functionRegistry(), ctx.enumMaps());
                compiledWhere = ctx.expressionCompiler().compile(rawWhere, exprCtx, diag);
            }
            result.add(new SourcePlan(sp.alias(), sp.sourceClass(), sp.inputIds(), compiledWhere));
        }
        return result;
    }

    void checkSourcePaths(String expr, List<SourcePlan> sourcePlans,
                           String ruleId, CompilerContext ctx) {
        if (expr == null || expr.isBlank()) return;
        String trimmed = expr.trim();
        DiagnosticCollector diag = ctx.diagnostics();

        int idx = 0;
        while ((idx = trimmed.indexOf("${", idx)) >= 0) {
            int end = trimmed.indexOf('}', idx);
            if (end < 0) break;
            String path = trimmed.substring(idx + 2, end);
            idx = end + 1;

            String[] parts = path.split("\\.", 2);
            if (parts.length < 2) continue;
            String alias = parts[0];
            String attrName = parts[1];

            Optional<SourcePlan> spOpt = sourcePlans.stream()
                    .filter(s -> s.alias().equals(alias)).findFirst();
            if (spOpt.isEmpty()) continue;
            SourcePlan sp = spOpt.get();
            if (sp.sourceClass() == null) continue;

            boolean found = false;
            var it = sp.sourceClass().getAttributes();
            while (it.hasNext()) {
                ch.interlis.ili2c.metamodel.Extendable ext = it.next();
                if (ext instanceof AttributeDef attrDef) {
                    if (attrName.equals(attrDef.getName())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE, Severity.ERROR,
                        "Source attribute not found: " + alias + "." + attrName,
                        ruleId, "Check the attribute name in source class"));
            }
        }
    }
}
