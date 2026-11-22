package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.RefPlan;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.ReferenceType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class MandatoryCoverageValidator {

    void checkMandatoryCoverage(
            AbstractClassDef<?> targetClass,
            List<AssignmentPlan> assignments,
            List<RefPlan> refs,
            List<BagPlan> bags,
            String ruleId,
            DiagnosticCollector diag) {
        var attrIt = targetClass.getAttributes();
        Set<String> assigned = new HashSet<>();
        for (AssignmentPlan ap : assignments) {
            assigned.add(ap.targetAttrName());
        }
        for (BagPlan bag : bags) {
            if (bag.isEmbed()) {
                assigned.add(bag.bagAttrName());
            }
        }
        Set<String> mappedRefs = new HashSet<>();
        for (RefPlan ref : refs) {
            mappedRefs.add(ref.targetRoleName());
        }
        if (targetClass instanceof AssociationDef association) {
            for (var role : association.getRoles()) {
                requireReference(role.getName(), mappedRefs, ruleId, diag);
            }
        }
        while (attrIt.hasNext()) {
            Extendable ext = attrIt.next();
            if (ext instanceof AttributeDef attr) {
                var card = attr.getCardinality();
                if (card != null && card.getMinimum() > 0) {
                    if (attr.getDomainResolvingAliases() instanceof ReferenceType) {
                        requireReference(attr.getName(), mappedRefs, ruleId, diag);
                    } else if (!assigned.contains(attr.getName())) {
                        diag.add(new Diagnostic(
                                DiagnosticCode.MAP_MANDATORY_MISSING,
                                Severity.WARNING,
                                "Mandatory attribute not assigned: " + attr.getName() + " in " + targetClass.getName(),
                                ruleId,
                                "Add an assignment or specifier a default value"));
                    }
                }
            }
        }
    }

    private static void requireReference(String name, Set<String> mappedRefs, String ruleId, DiagnosticCollector diag) {
        if (!mappedRefs.contains(name)) {
            diag.add(new Diagnostic(
                    DiagnosticCode.MAP_MANDATORY_MISSING,
                    Severity.ERROR,
                    "Mandatory reference not mapped: " + name,
                    ruleId,
                    "Add a ref mapping"));
        }
    }
}
