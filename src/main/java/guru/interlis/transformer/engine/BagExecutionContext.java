package guru.interlis.transformer.engine;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.state.ParentChildIndex;
import guru.interlis.transformer.state.ReferenceIndex;
import guru.interlis.transformer.state.StateStore;

import ch.interlis.iom.IomObject;

import java.util.List;
import java.util.Map;

public record BagExecutionContext(
        BagPlan plan,
        BoundSourceObject parent,
        IomObject target,
        TransformPlan transformPlan,
        StateStore stateStore,
        ParentChildIndex parentChildIndex,
        DiagnosticCollector diagnostics,
        RulePlan rule,
        Map<String, Map<String, List<IomObject>>> expandedTargets,
        GeometryAdapter geometryAdapter,
        Map<String, Map<String, TypeInfo>> sourceAttributeTypes,
        ReferenceIndex referenceIndex) {}
