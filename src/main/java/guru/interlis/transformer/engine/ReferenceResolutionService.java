package guru.interlis.transformer.engine;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.io.FormatIdResolver;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.DeferredReference;
import guru.interlis.transformer.state.ReferenceIndex;
import guru.interlis.transformer.state.SourceReferenceSelector;
import guru.interlis.transformer.state.StateStore;
import guru.interlis.transformer.state.TargetReference;

import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ReferenceResolutionService {

    public ReferenceResolutionReport resolveAll(
            TransformPlan plan,
            StateStore stateStore,
            ReferenceIndex referenceIndex,
            Map<String, Map<String, List<IomObject>>> outputObjects,
            DiagnosticCollector diagnostics) {

        List<DeferredReference> deferredRefs = stateStore.deferredReferences();
        int totalDeferred = deferredRefs.size();
        int resolved = 0;
        int unresolvedOptional = 0;
        int unresolvedMandatory = 0;
        int ambiguous = 0;
        int typeMismatch = 0;
        int cardinalityViolations = 0;
        int unrepresentable = 0;
        var duplicateXtfOids =
                deferredRefs.isEmpty() ? Map.<String, Set<String>>of() : duplicateXtfOids(plan, outputObjects);

        for (DeferredReference ref : deferredRefs) {
            ExecutionCancellation.check();
            SourceReferenceSelector selector = ref.sourceSelectors().isEmpty()
                    ? null
                    : ref.sourceSelectors().getFirst();
            String oid = selector != null ? selector.referencedSourceOid() : null;
            List<TargetReference> candidates = ref.singleton()
                    ? referenceIndex.findByRuleId(ref.targetRuleId())
                    : ref.sourceSelectors().stream()
                            .flatMap(source -> referenceIndex.find(source).stream())
                            .filter(candidate -> ref.targetRuleId() == null
                                    || ref.targetRuleId().equals(candidate.producingRuleId()))
                            .toList();
            String ownerLocation = ref.ownerRuleId() + " / " + ref.owner().getobjecttag() + "/"
                    + ref.owner().getobjectoid();
            boolean required = ref.required()
                    || ref.expectedCardinality() != null
                            && ref.expectedCardinality().isRequired();

            if (candidates.isEmpty()) {
                // An absent optional value is normal. A supplied optional value that does
                // not resolve is reported without writing an invalid reference.
                if (!required && oid == null && ref.sourceSelectors().size() == 1) continue;
                diagnostics.add(new Diagnostic(
                        required ? DiagnosticCode.RUN_REF_MISSING_MANDATORY : DiagnosticCode.RUN_REF_UNRESOLVED,
                        required ? Severity.ERROR : Severity.WARNING,
                        (required ? "Required reference missing for " : "Optional reference unresolved for ")
                                + context(ref, oid),
                        ownerLocation,
                        "Check source input, OID and basket"));
                if (required) unresolvedMandatory++;
                else unresolvedOptional++;
                continue;
            }

            if (candidates.size() > 1) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.RUN_REF_AMBIGUOUS,
                        Severity.ERROR,
                        "Ambiguous reference for " + context(ref, oid) + ": found " + candidates.size() + " targets",
                        ownerLocation,
                        "Constrain the reference by input, basket or target rule"));
                ambiguous++;
                continue;
            }

            TargetReference resolvedTarget = candidates.get(0);

            if (ref.expectedTargetClass() != null
                    && !ref.expectedTargetClass().isEmpty()
                    && !isTypeCompatible(plan, ref.ownerOutputId(), ref.expectedTargetClass(), resolvedTarget)) {
                Severity severity = required ? Severity.ERROR : failPolicySeverity(plan, Severity.ERROR);
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.RUN_REF_TYPE_MISMATCH,
                        severity,
                        "Type mismatch for " + context(ref, oid) + ": resolved " + resolvedTarget.targetClass(),
                        ownerLocation,
                        "Check target class of resolved object"));
                typeMismatch++;
                continue;
            }

            IomObject owner = ref.owner();
            long max = ref.expectedCardinality() == null
                    ? Long.MAX_VALUE
                    : ref.expectedCardinality().max();
            if (owner.getattrvaluecount(ref.targetRoleName()) >= max) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.RUN_REF_CARDINALITY,
                        Severity.ERROR,
                        "Cardinality exceeded for " + context(ref, oid) + ": max " + max,
                        ownerLocation,
                        "Check how many references the mapping creates for this role"));
                cardinalityViolations++;
                continue;
            }

            boolean sameOutput = ref.ownerOutputId().equals(resolvedTarget.outputId());
            boolean sameBid = Objects.equals(ref.ownerBasketId(), resolvedTarget.targetBasketId());
            boolean sameBasket = sameOutput
                    && sameBid
                    && TargetObjectFactory.extractTopic(ref.owner().getobjecttag())
                            .equals(TargetObjectFactory.extractTopic(resolvedTarget.targetClass()));
            OutputBinding output = plan != null ? plan.outputsById().get(ref.ownerOutputId()) : null;
            boolean xtf24 = output != null
                    && output.transferDescription() != null
                    && Model.ILI2_4.equals(
                            output.transferDescription().getLastModel().getIliVersion());
            var role = output != null && output.typeSystem() != null
                    ? output.typeSystem().resolveReference(ref.owner().getobjecttag(), ref.targetRoleName())
                    : null;
            String reason = null;
            if (duplicateXtfOids
                    .getOrDefault(resolvedTarget.outputId(), Set.of())
                    .contains(resolvedTarget.targetOid())) {
                reason = "ambiguous target TID " + resolvedTarget.targetOid();
            } else if (!sameBasket) {
                if (output == null || !FormatIdResolver.isXtf(output.format()) || role == null || !role.external()) {
                    reason = "the target role or output format does not allow an external basket reference";
                } else if (!xtf24) {
                    // XtfWriterAlt in IOX 1.24.4 emits EXTREF for a non-null refbid.
                    // XTF 2.3 requires REF and BID (reference manual 3.3.9).
                    reason = "the IOX XTF 2.3 writer cannot encode REF with BID";
                } else if (!sameOutput) {
                    reason = "XTF 2.4 external references require a target in the same output";
                } else if (sameBid) {
                    reason = "different target topics share the same BID";
                }
            }
            if (reason != null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.RUN_REF_UNREPRESENTABLE,
                        Severity.ERROR,
                        "Cannot encode reference for " + context(ref, oid) + " from output/basket "
                                + ref.ownerOutputId() + "/" + ref.ownerBasketId() + " to "
                                + resolvedTarget.outputId() + "/" + resolvedTarget.targetBasketId() + ": " + reason,
                        ownerLocation,
                        "Check the model's EXTERNAL declaration, output format and basket routing"));
                unrepresentable++;
                continue;
            }

            IomObject refObj = owner.addattrobj(ref.targetRoleName(), Iom_jObject.REF);
            refObj.setobjectrefoid(resolvedTarget.targetOid());
            resolved++;
        }

        return new ReferenceResolutionReport(
                resolved,
                unresolvedOptional,
                unresolvedMandatory,
                ambiguous,
                typeMismatch,
                cardinalityViolations,
                unrepresentable,
                totalDeferred);
    }

    private static String context(DeferredReference ref, String oid) {
        return "role '" + ref.targetRoleName() + "'"
                + (ref.associationName() == null ? "" : " in association '" + ref.associationName() + "'")
                + " (source OID " + oid + ", input/BID "
                + ref.sourceSelectors().stream()
                        .map(source -> source.inputId() + "/" + source.basketId())
                        .distinct()
                        .toList()
                + ", target rule " + ref.targetRuleId() + ", expected class " + ref.expectedTargetClass() + ")";
    }

    private static Map<String, Set<String>> duplicateXtfOids(
            TransformPlan plan, Map<String, Map<String, List<IomObject>>> outputObjects) {
        Map<String, Set<String>> duplicates = new HashMap<>();
        for (var output : outputObjects.entrySet()) {
            OutputBinding binding = plan.outputsById().get(output.getKey());
            if (binding == null || !FormatIdResolver.isXtf(binding.format())) continue;
            Set<String> seen = new HashSet<>();
            for (var basket : output.getValue().values()) {
                ExecutionCancellation.check();
                for (IomObject object : basket) {
                    String oid = object.getobjectoid();
                    if (oid != null && !seen.add(oid)) {
                        duplicates
                                .computeIfAbsent(output.getKey(), ignored -> new HashSet<>())
                                .add(oid);
                    }
                }
            }
        }
        return duplicates;
    }

    private boolean isTypeCompatible(
            TransformPlan plan, String ownerOutputId, String expectedTargetClass, TargetReference resolvedTarget) {
        if (expectedTargetClass.equals(resolvedTarget.targetClass())) return true;

        TypeSystemFacade typeSystem = null;
        if (plan != null) {
            OutputBinding ownerOutput = plan.outputsById().get(ownerOutputId);
            if (ownerOutput != null) typeSystem = ownerOutput.typeSystem();
            if (typeSystem == null) {
                OutputBinding resolvedOutput = plan.outputsById().get(resolvedTarget.outputId());
                if (resolvedOutput != null) typeSystem = resolvedOutput.typeSystem();
            }
        }
        return typeSystem != null && typeSystem.isTypeCompatible(expectedTargetClass, resolvedTarget.targetClass());
    }

    private static Severity failPolicySeverity(TransformPlan plan, Severity defaultSeverity) {
        if (plan == null) return defaultSeverity;
        return plan.failPolicy() == FailPolicy.LENIENT ? Severity.WARNING : defaultSeverity;
    }
}
