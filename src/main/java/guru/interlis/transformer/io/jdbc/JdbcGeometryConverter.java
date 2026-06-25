package guru.interlis.transformer.io.jdbc;

import guru.interlis.transformer.mapping.model.JobConfig;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxException;
import ch.interlis.iox_j.jts.Jts2iox;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKTReader;

public final class JdbcGeometryConverter {

    private static final WKTReader WKT_READER = new WKTReader();
    private static final WKBReader WKB_READER = new WKBReader();

    public IomObject convertToGeometry(Object rawValue, JobConfig.JdbcGeometrySpec spec) throws IoxException {
        if (rawValue == null) {
            return null;
        }
        String encoding = (spec.encoding != null) ? spec.encoding.toLowerCase().trim() : "wkt";
        return switch (encoding) {
            case "wkt" -> fromWkt(rawValue, spec);
            case "wkb" -> fromWkb(rawValue, spec);
            default ->
                throw new JdbcMappingException("Unsupported geometry encoding '" + spec.encoding + "' for attribute '"
                        + spec.attribute + "'. Use 'wkt' or 'wkb'.");
        };
    }

    private IomObject fromWkt(Object rawValue, JobConfig.JdbcGeometrySpec spec) throws IoxException {
        String wkt = (rawValue instanceof String s) ? s : rawValue.toString();
        Geometry geom;
        try {
            geom = WKT_READER.read(wkt);
        } catch (ParseException e) {
            throw new JdbcMappingException(
                    "Cannot parse WKT for attribute '" + spec.attribute + "': " + e.getMessage());
        }
        return convertByType(geom, spec);
    }

    private IomObject fromWkb(Object rawValue, JobConfig.JdbcGeometrySpec spec) throws IoxException {
        byte[] wkb;
        if (rawValue instanceof byte[] bytes) {
            wkb = bytes;
        } else {
            throw new JdbcMappingException("Expected byte[] for WKB geometry on attribute '" + spec.attribute
                    + "', got " + rawValue.getClass().getSimpleName());
        }
        Geometry geom;
        try {
            geom = WKB_READER.read(wkb);
        } catch (ParseException e) {
            throw new JdbcMappingException(
                    "Cannot parse WKB for attribute '" + spec.attribute + "': " + e.getMessage());
        }
        return convertByType(geom, spec);
    }

    private IomObject convertByType(Geometry geom, JobConfig.JdbcGeometrySpec spec) throws IoxException {
        String type = (spec.type != null) ? spec.type.toLowerCase().trim() : inferType(geom);
        return switch (type) {
            case "coord" -> pointToCoord(geom, spec.attribute);
            default ->
                throw new JdbcMappingException("Unsupported geometry type '" + spec.type + "' for attribute '"
                        + spec.attribute + "'. Supported: coord.");
        };
    }

    private IomObject pointToCoord(Geometry geom, String attribute) throws IoxException {
        if (geom.isEmpty()) {
            return null;
        }
        if (!(geom instanceof Point)) {
            throw new JdbcMappingException("Expected POINT geometry for coord type on attribute '" + attribute
                    + "', got " + geom.getGeometryType());
        }
        Point point = (Point) geom;
        Coordinate coordinate = point.getCoordinate();
        return Jts2iox.JTS2coord(coordinate);
    }

    private static String inferType(Geometry geom) {
        return switch (geom.getGeometryType()) {
            case "Point" -> "coord";
            case "LineString", "MultiLineString" -> "polyline";
            case "Polygon", "MultiPolygon" -> "surface";
            default ->
                throw new JdbcMappingException("Cannot infer geometry type from JTS geometry '" + geom.getGeometryType()
                        + "'. Set an explicit 'type' in the geometry spec.");
        };
    }
}
