package guru.interlis.transformer.mapping.compiler;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Table;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class MandatoryCoverageValidator {

    void checkMandatoryCoverage(Table targetClass, TypeSystemFacade ts,
                                 List<AssignmentPlan> assignments,
                                 String ruleId, DiagnosticCollector diag) {
        var attrIt = targetClass.getAttributes();
        Set<String> assigned = new HashSet<>();
        for (AssignmentPlan ap : assignments) {
            assigned.add(ap.targetAttrName());
        }
        while (attrIt.hasNext()) {
            Extendable ext = attrIt.next();
            if (ext instanceof AttributeDef attr) {
                var card = attr.getCardinality();
                if (card != null && card.getMinimum() > 0) {
                    if (!assigned.contains(attr.getName())) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_MANDATORY_MISSING, Severity.WARNING,
                                "Mandatory attribute not assigned: " + attr.getName()
                                        + " in " + targetClass.getName(),
                                ruleId, "Add an assignment or specifier a default value"));
                    }
                }
            }
        }
    }
}
