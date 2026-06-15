package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.CoordValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.GeometryObjectValue;
import guru.interlis.transformer.expr.NumberValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.List;

public final class GeometryFunctions {

    private GeometryFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register("coordEquals", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("coord1", TypeInfo.COORD),
                        new FunctionDef.FunctionParam("coord2", TypeInfo.COORD),
                        new FunctionDef.FunctionParam("tolerance", TypeInfo.NUMERIC)),
                GeometryFunctions::coordEquals);
    }

    public static Value coordEquals(List<Value> args, EvalContext ctx) {
        if (args.size() < 3) return BooleanValue.FALSE;
        Value a = args.get(0);
        Value b = args.get(1);
        Value t = args.get(2);

        if (!a.isDefined() || !b.isDefined() || !t.isDefined()) return BooleanValue.FALSE;

        double tolerance;
        if (t instanceof NumberValue nv) {
            tolerance = nv.value().doubleValue();
        } else {
            return BooleanValue.FALSE;
        }

        if (tolerance < 0.0) return BooleanValue.FALSE;

        double ax, ay, bx, by;
        if (a instanceof CoordValue ca) {
            ax = ca.x();
            ay = ca.y();
        } else if (a instanceof GeometryObjectValue ga) {
            CoordValue pt = extractCoord(ga);
            if (pt == null) return BooleanValue.FALSE;
            ax = pt.x();
            ay = pt.y();
        } else {
            return BooleanValue.FALSE;
        }

        if (b instanceof CoordValue cb) {
            bx = cb.x();
            by = cb.y();
        } else if (b instanceof GeometryObjectValue gb) {
            CoordValue pt = extractCoord(gb);
            if (pt == null) return BooleanValue.FALSE;
            bx = pt.x();
            by = pt.y();
        } else {
            return BooleanValue.FALSE;
        }

        double dx = ax - bx;
        double dy = ay - by;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return BooleanValue.of(distance <= tolerance);
    }

    private static CoordValue extractCoord(GeometryObjectValue geom) {
        if (geom.geometryType() == TypeInfo.COORD && geom.geometryObject() != null) {
            ch.interlis.iom.IomObject obj = geom.geometryObject();
            String tag = obj.getobjecttag();
            if (tag != null && tag.endsWith("COORD")) {
                String c1 = obj.getattrvalue("C1");
                String c2 = obj.getattrvalue("C2");
                if (c1 != null && c2 != null) {
                    try {
                        return new CoordValue(Double.parseDouble(c1), Double.parseDouble(c2));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (geom.pointOnSurface() != null) {
            return geom.pointOnSurface();
        }
        return null;
    }
}
