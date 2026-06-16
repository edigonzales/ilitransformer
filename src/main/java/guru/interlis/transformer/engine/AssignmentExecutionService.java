package guru.interlis.transformer.engine;

import guru.interlis.transformer.expr.CoordValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.expr.GeometryObjectValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.geometry.GeometryAdapter;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;

import java.util.List;

public final class AssignmentExecutionService {

    private final ExpressionEngine expressionEngine;
    private final GeometryAdapter geometryAdapter;

    public AssignmentExecutionService(ExpressionEngine expressionEngine, GeometryAdapter geometryAdapter) {
        this.expressionEngine = expressionEngine;
        this.geometryAdapter = geometryAdapter;
    }

    public void execute(
            List<AssignmentPlan> assignments, EvalContext evalCtx, Iom_jObject target, TypeSystemFacade targetTs) {
        for (AssignmentPlan ap : assignments) {
            Value value = expressionEngine.evaluate(ap.expression(), evalCtx);
            if (value.isDefined()) {
                setTargetAttribute(target, ap, value, targetTs);
            }
        }
    }

    private void setTargetAttribute(Iom_jObject target, AssignmentPlan ap, Value value, TypeSystemFacade targetTs) {
        TypeInfo targetType = ap.expression().resultType();
        if (isGeometryType(targetType)) {
            IomObject geomObj = geometryAdapter.denormalize(value, targetType);
            if (geomObj != null) {
                target.addattrobj(ap.targetAttrName(), geomObj);
                if (targetType == TypeInfo.AREA
                        && value instanceof GeometryObjectValue gov
                        && isIli1TargetClass(targetTs, target.getobjecttag())) {
                    addAreaPointHelper(target, ap.targetAttrName(), gov.pointOnSurface());
                }
                return;
            }
            return;
        }
        Object nativeValue = value.toNative();
        if (nativeValue != null) {
            String stringValue = nativeValue.toString();
            if (value instanceof guru.interlis.transformer.expr.EnumValue && ap.targetAttr() != null) {
                stringValue = targetTs.resolveEnumName(ap.targetAttr(), stringValue);
            }
            target.setattrvalue(ap.targetAttrName(), stringValue);
        }
    }

    private void addAreaPointHelper(Iom_jObject target, String attrName, CoordValue pointOnSurface) {
        if (pointOnSurface == null) return;
        String helperAttr = "_itf_" + attrName;
        for (int i = target.getattrvaluecount(helperAttr) - 1; i >= 0; i--) {
            target.deleteattrobj(helperAttr, i);
        }
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", Double.toString(pointOnSurface.x()));
        coord.setattrvalue("C2", Double.toString(pointOnSurface.y()));
        target.addattrobj(helperAttr, coord);
    }

    private static boolean isIli1TargetClass(TypeSystemFacade targetTs, String targetClassName) {
        if (targetTs == null || targetClassName == null) return false;
        ch.interlis.ili2c.metamodel.Table table = targetTs.resolveClass(targetClassName);
        if (table == null) return false;
        ch.interlis.ili2c.metamodel.Container container = table.getContainer();
        while (container != null && !(container instanceof ch.interlis.ili2c.metamodel.Model)) {
            container = container.getContainer();
        }
        return container instanceof ch.interlis.ili2c.metamodel.Model model
                && ch.interlis.ili2c.metamodel.Model.ILI1.equals(model.getIliVersion());
    }

    private static boolean isGeometryType(TypeInfo type) {
        return type == TypeInfo.COORD || type == TypeInfo.POLYLINE || type == TypeInfo.SURFACE || type == TypeInfo.AREA;
    }
}
