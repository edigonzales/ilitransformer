package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.CoordValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.GeometryObjectValue;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.NumberValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;

import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

public final class GeometryFunctions {

    private static final double DEFAULT_MAX_OVERLAP = 0.001d;

    private GeometryFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register(
                "coordEquals",
                TypeInfo.BOOLEAN,
                List.of(
                        new FunctionDef.FunctionParam("coord1", TypeInfo.COORD),
                        new FunctionDef.FunctionParam("coord2", TypeInfo.COORD),
                        new FunctionDef.FunctionParam("tolerance", TypeInfo.NUMERIC)),
                GeometryFunctions::coordEquals);

        registry.register(
                "pointOnSurface",
                TypeInfo.COORD,
                List.of(new FunctionDef.FunctionParam("surface", TypeInfo.UNKNOWN)),
                GeometryFunctions::pointOnSurface);
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

    public static Value pointOnSurface(List<Value> args, EvalContext ctx) {
        if (args.isEmpty()) {
            return NullValue.INSTANCE;
        }
        Value arg = args.get(0);
        if (!arg.isDefined()) {
            return NullValue.INSTANCE;
        }
        if (arg instanceof CoordValue cv) {
            return cv;
        }
        if (!(arg instanceof GeometryObjectValue geom)) {
            return NullValue.INSTANCE;
        }
        CoordValue explicit = extractCoord(geom);
        if (explicit != null) {
            return explicit;
        }
        IomObject geometryObject = geom.geometryObject();
        try {
            Geometry geometry = toJtsSurface(geometryObject);
            if (geometry == null || geometry.isEmpty()) {
                reportPointOnSurfaceFailure(ctx, "Surface geometry is empty");
                return NullValue.INSTANCE;
            }
            if (!geometry.isValid()) {
                Geometry fixed = geometry.buffer(0);
                if (fixed != null
                        && !fixed.isEmpty()
                        && ("Polygon".equals(fixed.getGeometryType())
                                || "MultiPolygon".equals(fixed.getGeometryType()))) {
                    geometry = fixed;
                }
            }
            Point interiorPoint = geometry.getInteriorPoint();
            if (interiorPoint == null || interiorPoint.isEmpty() || interiorPoint.getCoordinate() == null) {
                reportPointOnSurfaceFailure(ctx, "Could not derive a deterministic point on surface");
                return NullValue.INSTANCE;
            }
            var coordinate = interiorPoint.getCoordinate();
            return new CoordValue(coordinate.x, coordinate.y);
        } catch (Iox2jtsException | RuntimeException ex) {
            reportPointOnSurfaceFailure(ctx, "Could not derive point on surface: " + ex.getMessage());
            return NullValue.INSTANCE;
        }
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

    private static Geometry toJtsSurface(IomObject geomValue) throws Iox2jtsException {
        if (geomValue == null) {
            return null;
        }
        String tag = geomValue.getobjecttag();
        if ("MULTISURFACE".equalsIgnoreCase(tag)) {
            return Iox2jts.multisurface2JTS(geomValue, DEFAULT_MAX_OVERLAP, 0);
        }
        if ("SURFACE".equalsIgnoreCase(tag) || geomValue.getattrvaluecount("boundary") > 0) {
            return Iox2jts.surface2JTS(geomValue, DEFAULT_MAX_OVERLAP);
        }
        if (geomValue.getattrvaluecount("surface") > 0) {
            Iom_jObject multi = new Iom_jObject("MULTISURFACE", null);
            for (int i = 0; i < geomValue.getattrvaluecount("surface"); i++) {
                IomObject surface = geomValue.getattrobj("surface", i);
                if (surface != null) {
                    multi.addattrobj("surface", new Iom_jObject(surface));
                }
            }
            return Iox2jts.multisurface2JTS(multi, DEFAULT_MAX_OVERLAP, 0);
        }
        return Iox2jts.surface2JTS(geomValue, DEFAULT_MAX_OVERLAP);
    }

    private static void reportPointOnSurfaceFailure(EvalContext ctx, String message) {
        if (ctx == null || ctx.diagnostics() == null) {
            return;
        }
        ctx.diagnostics()
                .add(new Diagnostic(
                        DiagnosticCode.GEOM_AREA_POINT_MISSING,
                        Severity.WARNING,
                        message,
                        ctx.ruleId(),
                        "Provide a valid surface geometry or an explicit point-on-surface"));
    }
}
