package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.SourcePlan;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class IdentityCompiler {

    List<String> compileIdentityKeys(JobConfig.RuleSpec rule, List<SourcePlan> sourcePlans, CompilerContext ctx) {
        DiagnosticCollector diag = ctx.diagnostics();
        if (rule.identity == null || rule.identity.sourceKey == null || rule.identity.sourceKey.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (String key : rule.identity.sourceKey) {
            if (key == null || key.isBlank()) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_MISSING,
                        Severity.ERROR,
                        "Identity key is null or empty in rule " + rule.id,
                        rule.id,
                        "Remove empty key or provide a valid attribute reference"));
                continue;
            }
            String trimmed = key.trim();

            if (!seenKeys.add(trimmed)) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_DUPLICATE,
                        Severity.ERROR,
                        "Duplicate identity key: " + trimmed + " in rule " + rule.id,
                        rule.id,
                        "Remove duplicate identity key"));
                continue;
            }

            if (!trimmed.contains(".")) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_MISSING,
                        Severity.ERROR,
                        "Identity key must be qualified with alias: " + trimmed,
                        rule.id,
                        "Use format: <alias>.<attributeName>"));
                continue;
            }

            String[] parts = trimmed.split("\\.", 2);
            if (parts.length != 2) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_MISSING,
                        Severity.ERROR,
                        "Invalid identity key format: " + trimmed,
                        rule.id,
                        "Use format: <alias>.<attributeName>"));
                continue;
            }

            String alias = parts[0];
            String attrName = parts[1];

            SourcePlan matchingSource = sourcePlans.stream()
                    .filter(sp -> sp.alias() != null && sp.alias().equals(alias))
                    .findFirst()
                    .orElse(null);

            if (matchingSource == null) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_MISSING,
                        Severity.ERROR,
                        "Identity key alias not found: " + alias + " in rule " + rule.id,
                        rule.id,
                        "Verify the alias matches a source definition"));
                continue;
            }

            if (matchingSource.sourceClass() == null) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_MISSING,
                        Severity.ERROR,
                        "Source class not resolved for alias: " + alias,
                        rule.id,
                        "Verify the source class is valid"));
                continue;
            }

            AttributeDef attr = null;
            var attrIt = matchingSource.sourceClass().getAttributes();
            while (attrIt.hasNext()) {
                Extendable ext = attrIt.next();
                if (ext instanceof AttributeDef ad) {
                    if (ad.getName() != null && ad.getName().equals(attrName)) {
                        attr = ad;
                        break;
                    }
                }
            }

            if (attr == null) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_MISSING,
                        Severity.ERROR,
                        "Identity key attribute not found: " + attrName + " on source " + alias + " in rule " + rule.id,
                        rule.id,
                        "Verify the attribute name exists on the source class"));
                continue;
            }

            if (!isValidIdentityKeyType(attr)) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_INVALID_TYPE,
                        Severity.ERROR,
                        "Identity key attribute is not a usable scalar type: " + alias + "." + attrName + " in rule "
                                + rule.id,
                        rule.id,
                        "Identity keys must be scalar text, numeric, enum, boolean, or date attributes. "
                                + "Geometry, BAG/STRUCTURE, and reference types are not allowed."));
                continue;
            }

            keys.add(trimmed);
        }
        return keys;
    }

    static boolean isValidIdentityKeyType(AttributeDef attr) {
        ch.interlis.ili2c.metamodel.Type type = attr.getDomainResolvingAliases();
        if (type == null) type = attr.getDomain();
        if (type == null) return false;

        if (type instanceof ch.interlis.ili2c.metamodel.CoordType
                || type instanceof ch.interlis.ili2c.metamodel.PolylineType
                || type instanceof ch.interlis.ili2c.metamodel.AreaType
                || type instanceof ch.interlis.ili2c.metamodel.SurfaceOrAreaType
                || type instanceof ch.interlis.ili2c.metamodel.SurfaceType
                || type instanceof ch.interlis.ili2c.metamodel.ReferenceType) {
            return false;
        }

        if (type instanceof CompositionType ct) {
            Table component = ct.getComponentType();
            if (component == null) return false;
            var innerIt = component.getAttributes();
            while (innerIt.hasNext()) {
                Extendable ext = innerIt.next();
                if (ext instanceof AttributeDef) return false;
            }
            return false;
        }

        return true;
    }

    void validateIdentityKeysStructurally(JobConfig.RuleSpec rule, CompilerContext ctx) {
        DiagnosticCollector diag = ctx.diagnostics();
        if (rule.identity == null || rule.identity.sourceKey == null || rule.identity.sourceKey.isEmpty()) {
            return;
        }
        Set<String> seenKeys = new HashSet<>();
        Set<String> aliases = new HashSet<>();
        for (JobConfig.SourceSpec src : rule.sources) {
            if (src.alias != null && !src.alias.isBlank()) {
                aliases.add(src.alias);
            }
        }
        for (String key : rule.identity.sourceKey) {
            if (key == null || key.isBlank()) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_MISSING,
                        Severity.ERROR,
                        "Identity key is null or empty in rule " + rule.id,
                        rule.id,
                        "Remove empty key or provide a valid attribute reference"));
                continue;
            }
            String trimmed = key.trim();

            if (!seenKeys.add(trimmed)) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_DUPLICATE,
                        Severity.ERROR,
                        "Duplicate identity key: " + trimmed + " in rule " + rule.id,
                        rule.id,
                        "Remove duplicate identity key"));
            }

            if (!trimmed.contains(".")) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_MISSING,
                        Severity.ERROR,
                        "Identity key must be qualified with alias: " + trimmed,
                        rule.id,
                        "Use format: <alias>.<attributeName>"));
                continue;
            }

            String[] parts = trimmed.split("\\.", 2);
            String alias = parts[0];
            if (!aliases.isEmpty() && !aliases.contains(alias)) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_IDENTITY_KEY_MISSING,
                        Severity.ERROR,
                        "Identity key alias not found: " + alias + " in rule " + rule.id,
                        rule.id,
                        "Available aliases: " + aliases));
            }
        }
    }
}
