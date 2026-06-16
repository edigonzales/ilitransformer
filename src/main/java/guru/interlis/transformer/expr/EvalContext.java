package guru.interlis.transformer.expr;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.state.SourceLookupIndex;

import ch.interlis.iom.IomObject;

import java.util.Map;

public record EvalContext(
        Map<String, IomObject> sources,
        DiagnosticCollector diagnostics,
        String ruleId,
        Map<String, Map<String, String>> enumMaps,
        GeometryAdapter geometryAdapter,
        Map<String, Map<String, TypeInfo>> sourceAttributeTypes,
        SourceLookupIndex lookupIndex) {

    public EvalContext(
            Map<String, IomObject> sources,
            DiagnosticCollector diagnostics,
            String ruleId,
            Map<String, Map<String, String>> enumMaps) {
        this(sources, diagnostics, ruleId, enumMaps, null, null, null);
    }

    public EvalContext(Map<String, IomObject> sources, DiagnosticCollector diagnostics, String ruleId) {
        this(sources, diagnostics, ruleId, null, null, null, null);
    }

    public EvalContext(
            Map<String, IomObject> sources,
            DiagnosticCollector diagnostics,
            String ruleId,
            Map<String, Map<String, String>> enumMaps,
            GeometryAdapter geometryAdapter,
            Map<String, Map<String, TypeInfo>> sourceAttributeTypes) {
        this(sources, diagnostics, ruleId, enumMaps, geometryAdapter, sourceAttributeTypes, null);
    }

    public EvalContext withEnumMaps(Map<String, Map<String, String>> maps) {
        return new EvalContext(sources, diagnostics, ruleId, maps, geometryAdapter, sourceAttributeTypes, lookupIndex);
    }

    public EvalContext withGeometry(GeometryAdapter adapter, Map<String, Map<String, TypeInfo>> attrTypes) {
        return new EvalContext(sources, diagnostics, ruleId, enumMaps, adapter, attrTypes, lookupIndex);
    }

    public EvalContext withLookupIndex(SourceLookupIndex index) {
        return new EvalContext(sources, diagnostics, ruleId, enumMaps, geometryAdapter, sourceAttributeTypes, index);
    }
}
