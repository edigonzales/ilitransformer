package guru.interlis.transformer.engine;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.RoleResolver;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.DeferredReference;
import guru.interlis.transformer.state.ReferenceIndex;
import guru.interlis.transformer.state.SourceReferenceSelector;
import guru.interlis.transformer.state.StateStore;
import guru.interlis.transformer.state.TargetReference;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReferenceResolutionService {

    public ReferenceResolutionReport resolveAll(
            TransformPlan plan,
            StateStore stateStore,
            ReferenceIndex referenceIndex,
            DiagnosticCollector diagnostics) {

        List<DeferredReference> deferredRefs = stateStore.deferredReferences();
        int totalDeferred = deferredRefs.size();
        int resolved = 0;
        int unresolvedOptional = 0;
        int unresolvedMandatory = 0;
        int ambiguous = 0;
        int typeMismatch = 0;
        int cardinalityViolations = 0;

        Map<String, Map<String, Long>> roleCountByOwner = new HashMap<>();

        for (DeferredReference ref : deferredRefs) {
            SourceReferenceSelector selector = ref.sourceSelector();
            List<TargetReference> candidates = referenceIndex.find(selector);
            if (ref.targetRuleId() != null && !ref.targetRuleId().isBlank()) {
                candidates = candidates.stream()
                        .filter(candidate -> ref.targetRuleId().equals(candidate.producingRuleId()))
                        .toList();
            }

            if (candidates.isEmpty()) {
                Severity severity = ref.required()
                        ? failPolicySeverity(plan, Severity.ERROR)
                        : failPolicySeverity(plan, Severity.WARNING);
                String code = ref.required()
                        ? DiagnosticCode.RUN_REF_MISSING_MANDATORY
                        : DiagnosticCode.RUN_REF_UNRESOLVED;
                String detail = ref.required()
                        ? "Required reference missing"
                        : "Optional reference unresolved";
                diagnostics.add(new Diagnostic(code, severity,
                        detail + " for role '" + ref.targetRoleName()
                                + "' (OID " + selector.referencedSourceOid() + ")"
                                + (ref.associationName() != null
                                        ? " in association '" + ref.associationName() + "'" : ""),
                        ref.owner().targetClass() + "/" + ref.owner().targetOid(),
                        "Check source OID / basket routing"));
                if (ref.required()) {
                    unresolvedMandatory++;
                } else {
                    unresolvedOptional++;
                }
                continue;
            }

            if (candidates.size() > 1) {
                diagnostics.add(new Diagnostic(DiagnosticCode.RUN_REF_AMBIGUOUS, Severity.ERROR,
                        "Ambiguous reference for OID " + selector.referencedSourceOid()
                                + " in class " + selector.expectedSourceClass()
                                + ": found " + candidates.size() + " targets",
                        ref.owner().targetClass() + "/" + ref.owner().targetOid(),
                        "Constrain mapping by input/basket/class"));
                ambiguous++;
                continue;
            }

            TargetReference resolvedTarget = candidates.get(0);

            if (ref.expectedTargetClass() != null
                    && !ref.expectedTargetClass().isEmpty()
                    && !ref.expectedTargetClass().equals(resolvedTarget.targetClass())) {
                Severity severity = failPolicySeverity(plan, Severity.ERROR);
                diagnostics.add(new Diagnostic(DiagnosticCode.RUN_REF_TYPE_MISMATCH, severity,
                        "Type mismatch for reference " + selector.referencedSourceOid()
                                + ": expected " + ref.expectedTargetClass()
                                + " but resolved " + resolvedTarget.targetClass(),
                        ref.owner().targetClass() + "/" + ref.owner().targetOid(),
                        "Check target class of resolved object"));
                typeMismatch++;
                continue;
            }

            // Per-owner cardinality check
            if (ref.targetRoleName() != null && ref.expectedCardinality() != null) {
                String roleKey = ref.owner().targetClass() + "::" + ref.owner().targetOid();
                roleCountByOwner.computeIfAbsent(roleKey, k -> new LinkedHashMap<>())
                        .merge(ref.targetRoleName(), 1L, Long::sum);

                long count = roleCountByOwner.get(roleKey).get(ref.targetRoleName());
                if (count > ref.expectedCardinality().max()) {
                    diagnostics.add(new Diagnostic(DiagnosticCode.RUN_REF_CARDINALITY,
                            failPolicySeverity(plan, Severity.ERROR),
                            "Cardinality exceeded for role '" + ref.targetRoleName()
                                    + "': max " + ref.expectedCardinality().max()
                                    + ", got " + count,
                            ref.owner().targetClass() + "/" + ref.owner().targetOid(),
                            "Check that source does not produce more references than allowed"));
                    cardinalityViolations++;
                    continue;
                }
            }

            // Set reference on owner object
            Optional<IomObject> ownerObj = stateStore.findTarget(ref.owner());
            if (ownerObj.isPresent()) {
                IomObject owner = ownerObj.get();
                IomObject refObj = owner.addattrobj(ref.targetRoleName(), Iom_jObject.REF);
                refObj.setobjectrefoid(resolvedTarget.targetOid());
                resolved++;
            }
        }

        return new ReferenceResolutionReport(resolved, unresolvedOptional, unresolvedMandatory,
                ambiguous, typeMismatch, cardinalityViolations, totalDeferred);
    }

    private int checkRequiredRefsWithoutDeferred(
            TransformPlan plan,
            StateStore stateStore,
            DiagnosticCollector diagnostics) {
        int missingCount = 0;
        if (plan == null) return 0;
        for (RulePlan rule : plan.rules()) {
            OutputBinding outputBinding = plan.outputsById().get(rule.outputId());
            TypeSystemFacade targetTs = outputBinding != null ? outputBinding.typeSystem() : null;
            if (targetTs == null) continue;
            String targetClassScoped = getScopedName(rule.targetClass());

            for (var ref : rule.refs()) {
                if (!ref.required()) continue;
                RoleResolver roleResolver = new RoleResolver(targetTs);
                long minCardinality = roleResolver.getTargetRoleCardinality(
                        ref, targetClassScoped).min();
                if (minCardinality <= 0) continue;

                List<DeferredReference> deferredRefs = stateStore.deferredReferences();
                boolean hasResolved = false;
                for (DeferredReference dr : deferredRefs) {
                    if (dr.owner().targetClass() != null
                            && dr.owner().targetClass().equals(targetClassScoped)
                            && dr.targetRoleName().equals(ref.targetRoleName())) {
                        hasResolved = true;
                        break;
                    }
                }

                if (!hasResolved) {
                    Severity severity = failPolicySeverity(plan, Severity.ERROR);
                    diagnostics.add(new Diagnostic(DiagnosticCode.RUN_REF_MISSING_MANDATORY, severity,
                            "Required reference missing for role " + ref.targetRoleName(),
                            targetClassScoped,
                            "Ensure source objects have the required reference"));
                    missingCount++;
                }
            }
        }
        return missingCount;
    }

    private static Severity failPolicySeverity(TransformPlan plan, Severity defaultSeverity) {
        if (plan == null) return defaultSeverity;
        return plan.failPolicy() == FailPolicy.LENIENT ? Severity.WARNING : defaultSeverity;
    }

    private static String getScopedName(ch.interlis.ili2c.metamodel.Table table) {
        return TypeSystemFacade.getScopedName(table);
    }
}
