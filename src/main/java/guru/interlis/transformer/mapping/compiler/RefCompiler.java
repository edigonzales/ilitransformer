package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.RefPlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.Table;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RefCompiler {

    RefPlan compileRef(
            JobConfig.RefMapping ref,
            Table targetClass,
            TypeSystemFacade targetTs,
            List<SourcePlan> sourcePlans,
            String ruleId,
            CompilerContext ctx) {
        String roleName = ref.role;
        if (roleName == null && ref.target != null) {
            roleName = ref.target; // backward compat
        }

        if (roleName != null && targetTs != null) {
            TypeSystemFacade.ReferenceInfo referenceInfo =
                    targetTs.resolveReference(CompileUtils.getScopedName(targetClass), roleName);
            if (referenceInfo == null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.MAP_UNKNOWN_ROLE,
                                Severity.WARNING,
                                "Role not found on target class '" + targetClass.getName() + "': " + roleName,
                                ruleId,
                                "Check the owner-side reference/role name in the target model"));
            }

            if (referenceInfo != null && ref.association != null) {
                if (referenceInfo.association() != null) {
                    if (!ref.association.equals(referenceInfo.association().getName())) {
                        ctx.diagnostics()
                                .add(new Diagnostic(
                                        DiagnosticCode.MAP_UNKNOWN_ROLE,
                                        Severity.ERROR,
                                        "Role '" + roleName + "' belongs to association '"
                                                + referenceInfo.association().getName() + "', not '" + ref.association
                                                + "'",
                                        ruleId,
                                        "Correct the association name or remove it"));
                    }
                } else if (ref.association != null) {
                    ctx.diagnostics()
                            .add(new Diagnostic(
                                    DiagnosticCode.MAP_UNKNOWN_ROLE,
                                    Severity.WARNING,
                                    "Reference '" + roleName + "' is not part of an association" + " but association '"
                                            + ref.association + "' was specified",
                                    ruleId,
                                    "Remove the association name or use the correct role"));
                }
            }

            if (referenceInfo != null) {
                long modelMin = referenceInfo.minCardinality();
                if (modelMin > 0 && !ref.required) {
                    ctx.diagnostics()
                            .add(new Diagnostic(
                                    DiagnosticCode.MAP_TYPE_MISMATCH,
                                    Severity.WARNING,
                                    "Reference '" + roleName + "' is mandatory in model but ref is marked optional",
                                    ruleId,
                                    "Set required: true or adjust the model"));
                }
                if (modelMin == 0 && ref.required) {
                    ctx.diagnostics()
                            .add(new Diagnostic(
                                    DiagnosticCode.MAP_TYPE_MISMATCH,
                                    Severity.WARNING,
                                    "Reference '" + roleName + "' is optional in model but ref is marked required",
                                    ruleId,
                                    "Set required: false or adjust the model"));
                }
            }
        }

        String association = ref.association;
        String sourceRef = ref.sourceRef;
        String targetRuleId = ref.targetRule;
        if (targetRuleId == null && ref.targetObject != null) {
            targetRuleId = ref.targetObject.rule;
            if (sourceRef == null) {
                sourceRef = ref.targetObject.sourceRef;
            }
        }

        if (sourceRef != null && !sourceRef.isBlank()) {
            Map<String, SourcePlan> sourcesByAlias = new LinkedHashMap<>();
            for (SourcePlan sourcePlan : sourcePlans) {
                sourcesByAlias.put(sourcePlan.alias(), sourcePlan);
            }
            ctx.expressionCompiler()
                    .compile(
                            sourceRef,
                            new ExpressionCompileContext(
                                    ruleId + "-ref",
                                    sourcesByAlias,
                                    TypeInfo.UNKNOWN,
                                    ctx.functionRegistry(),
                                    ctx.enumMaps()),
                            ctx.diagnostics());
        }

        return new RefPlan(roleName, association, sourceRef, targetRuleId, ref.required);
    }
}
