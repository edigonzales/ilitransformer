package guru.interlis.transformer.geometry;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.expr.CoordValue;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.PolylineValue;
import guru.interlis.transformer.expr.SurfaceValue;
import guru.interlis.transformer.expr.TextValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.ArrayList;
import java.util.List;

public final class NoOpGeometryAdapter implements GeometryAdapter {

    @Override
    public Value normalize(IomObject sourceGeometry, TypeInfo sourceType) {
        if (sourceGeometry == null) return NullValue.INSTANCE;
        return switch (sourceType) {
            case COORD -> parseCoord(sourceGeometry);
            case POLYLINE -> parsePolyline(sourceGeometry);
            case SURFACE, AREA -> parseSurface(sourceGeometry);
            default -> new TextValue(sourceGeometry.toString());
        };
    }

    @Override
    public IomObject denormalize(Value geometry, TypeInfo targetType) {
        if (geometry == null || geometry.isNull()) return null;
        if (geometry instanceof TextValue tv) {
            Iom_jObject obj = new Iom_jObject(targetType.name(), null);
            obj.setattrvalue("value", tv.value());
            return obj;
        }
        if (geometry instanceof CoordValue cv) {
            Iom_jObject obj = new Iom_jObject("COORD", null);
            obj.setattrvalue("C1", Double.toString(cv.x()));
            obj.setattrvalue("C2", Double.toString(cv.y()));
            return obj;
        }
        if (geometry instanceof PolylineValue pv) {
            Iom_jObject obj = new Iom_jObject("POLYLINE", null);
            for (int i = 0; i < pv.points().size(); i++) {
                CoordValue pt = pv.points().get(i);
                Iom_jObject coord = new Iom_jObject("COORD", null);
                coord.setattrvalue("C1", Double.toString(pt.x()));
                coord.setattrvalue("C2", Double.toString(pt.y()));
                obj.addattrobj("coord", coord);
            }
            return obj;
        }
        if (geometry instanceof SurfaceValue sv) {
            Iom_jObject obj = new Iom_jObject("SURFACE", null);
            for (List<CoordValue> ring : sv.rings()) {
                Iom_jObject boundary = new Iom_jObject("boundary", null);
                Iom_jObject polyline = new Iom_jObject("POLYLINE", null);
                for (CoordValue pt : ring) {
                    Iom_jObject coord = new Iom_jObject("COORD", null);
                    coord.setattrvalue("C1", Double.toString(pt.x()));
                    coord.setattrvalue("C2", Double.toString(pt.y()));
                    polyline.addattrobj("coord", coord);
                }
                boundary.addattrobj("polyline", polyline);
                obj.addattrobj("boundary", boundary);
            }
            return obj;
        }
        return null;
    }

    @Override
    public Value transform(Value geometry, GeometryOperation operation) {
        return geometry;
    }

    private CoordValue parseCoord(IomObject geom) {
        if (geom == null) return null;
        String c1 = geom.getattrvalue("C1");
        String c2 = geom.getattrvalue("C2");
        if (c1 != null && c2 != null) {
            return new CoordValue(Double.parseDouble(c1), Double.parseDouble(c2));
        }
        String value = geom.getattrvalue("value");
        if (value != null) {
            String[] parts = value.trim().split("\\s+");
            if (parts.length >= 2) {
                return new CoordValue(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
            }
        }
        return null;
    }

    private PolylineValue parsePolyline(IomObject geom) {
        if (geom == null) return null;
        List<CoordValue> points = new ArrayList<>();
        int count = geom.getattrvaluecount("coord");
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                IomObject coord = geom.getattrobj("coord", i);
                if (coord != null) {
                    CoordValue cv = parseCoord(coord);
                    if (cv != null) points.add(cv);
                }
            }
        }
        if (points.isEmpty()) {
            String value = geom.getattrvalue("value");
            if (value != null) {
                for (String part : value.split(",")) {
                    String[] xy = part.trim().split("\\s+");
                    if (xy.length >= 2) {
                        points.add(new CoordValue(Double.parseDouble(xy[0]), Double.parseDouble(xy[1])));
                    }
                }
            }
        }
        return points.isEmpty() ? null : new PolylineValue(points);
    }

    private SurfaceValue parseSurface(IomObject geom) {
        if (geom == null) return null;
        List<List<CoordValue>> rings = new ArrayList<>();
        int boundaryCount = geom.getattrvaluecount("boundary");
        if (boundaryCount > 0) {
            for (int b = 0; b < boundaryCount; b++) {
                IomObject boundary = geom.getattrobj("boundary", b);
                if (boundary != null) {
                    int polyCount = boundary.getattrvaluecount("polyline");
                    for (int p = 0; p < polyCount; p++) {
                        IomObject poly = boundary.getattrobj("polyline", p);
                        if (poly != null) {
                            PolylineValue pv = parsePolyline(poly);
                            if (pv != null) rings.add(pv.points());
                        }
                    }
                }
            }
        }
        if (rings.isEmpty()) {
            PolylineValue pv = parsePolyline(geom);
            if (pv != null) rings.add(pv.points());
        }
        return rings.isEmpty() ? null : new SurfaceValue(rings);
    }
}
