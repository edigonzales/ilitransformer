package guru.interlis.transformer.expr;

import ch.interlis.iom.IomObject;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import java.util.Map;

public record EvalContext(
        Map<String, IomObject> sources,
        DiagnosticCollector diagnostics,
        String ruleId,
        Map<String, Map<String, String>> enumMaps,
        GeometryAdapter geometryAdapter,
        Map<String, Map<String, TypeInfo>> sourceAttributeTypes
) {

    public EvalContext(Map<String, IomObject> sources, DiagnosticCollector diagnostics, String ruleId, Map<String, Map<String, String>> enumMaps) {
        this(sources, diagnostics, ruleId, enumMaps, null, null);
    }

    public EvalContext(Map<String, IomObject> sources, DiagnosticCollector diagnostics, String ruleId) {
        this(sources, diagnostics, ruleId, null, null, null);
    }

    public EvalContext withEnumMaps(Map<String, Map<String, String>> maps) {
        return new EvalContext(sources, diagnostics, ruleId, maps, geometryAdapter, sourceAttributeTypes);
    }

    public EvalContext withGeometry(GeometryAdapter adapter, Map<String, Map<String, TypeInfo>> attrTypes) {
        return new EvalContext(sources, diagnostics, ruleId, enumMaps, adapter, attrTypes);
    }
}
