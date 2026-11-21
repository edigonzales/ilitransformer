package guru.interlis.transformer.expr;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.state.SourceLookupIndex;
import guru.interlis.transformer.state.SourceRecord;

import ch.interlis.iom.IomObject;

import java.util.LinkedHashMap;
import java.util.Map;

public record EvalContext(
        Map<String, IomObject> sources,
        DiagnosticCollector diagnostics,
        String ruleId,
        Map<String, Map<String, String>> enumMaps,
        GeometryAdapter geometryAdapter,
        Map<String, Map<String, TypeInfo>> sourceAttributeTypes,
        SourceLookupIndex lookupIndex,
        Map<String, SourceRecord> sourceRecords,
        Map<String, InputBinding> inputs) {

    public static EvalContext forRecords(
            Map<String, SourceRecord> records,
            Map<String, InputBinding> inputs,
            DiagnosticCollector diagnostics,
            String ruleId,
            Map<String, Map<String, String>> enumMaps,
            GeometryAdapter geometryAdapter,
            Map<String, Map<String, TypeInfo>> sourceAttributeTypes,
            SourceLookupIndex lookupIndex) {
        Map<String, IomObject> sources = new LinkedHashMap<>();
        records.forEach((alias, record) -> sources.put(alias, record.sourceObject()));
        return new EvalContext(
                sources,
                diagnostics,
                ruleId,
                enumMaps,
                geometryAdapter,
                sourceAttributeTypes,
                lookupIndex,
                records,
                inputs);
    }

    public EvalContext(
            Map<String, IomObject> sources,
            DiagnosticCollector diagnostics,
            String ruleId,
            Map<String, Map<String, String>> enumMaps) {
        this(sources, diagnostics, ruleId, enumMaps, null, null, null, Map.of(), Map.of());
    }

    public EvalContext(Map<String, IomObject> sources, DiagnosticCollector diagnostics, String ruleId) {
        this(sources, diagnostics, ruleId, null, null, null, null, Map.of(), Map.of());
    }

    public EvalContext(
            Map<String, IomObject> sources,
            DiagnosticCollector diagnostics,
            String ruleId,
            Map<String, Map<String, String>> enumMaps,
            GeometryAdapter geometryAdapter,
            Map<String, Map<String, TypeInfo>> sourceAttributeTypes) {
        this(sources, diagnostics, ruleId, enumMaps, geometryAdapter, sourceAttributeTypes, null, Map.of(), Map.of());
    }

    public EvalContext withEnumMaps(Map<String, Map<String, String>> maps) {
        return new EvalContext(
                sources,
                diagnostics,
                ruleId,
                maps,
                geometryAdapter,
                sourceAttributeTypes,
                lookupIndex,
                sourceRecords,
                inputs);
    }

    public EvalContext withGeometry(GeometryAdapter adapter, Map<String, Map<String, TypeInfo>> attrTypes) {
        return new EvalContext(
                sources, diagnostics, ruleId, enumMaps, adapter, attrTypes, lookupIndex, sourceRecords, inputs);
    }

    public EvalContext withLookupIndex(SourceLookupIndex index) {
        return new EvalContext(
                sources,
                diagnostics,
                ruleId,
                enumMaps,
                geometryAdapter,
                sourceAttributeTypes,
                index,
                sourceRecords,
                inputs);
    }
}
