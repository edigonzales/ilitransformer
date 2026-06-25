package guru.interlis.transformer.io.shp.geom;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * Converts an INTERLIS/IOX geometry value ({@link IomObject}) into a {@code
 * com.vividsolutions.jts} geometry, the symmetric counterpart of {@code Jts2iox} used by the
 * Shapefile reader.
 *
 * <p>Dispatch is driven by the IOX object tag so that single vs. multi geometries are detected
 * reliably:
 *
 * <ul>
 *   <li>{@code COORD} &rarr; {@code Point}, {@code MULTICOORD} &rarr; {@code MultiPoint}
 *   <li>{@code POLYLINE} &rarr; {@code LineString}, {@code MULTIPOLYLINE} &rarr; {@code
 *       MultiLineString}
 *   <li>{@code SURFACE}/{@code MULTISURFACE} &rarr; {@code Polygon}/{@code MultiPolygon}
 * </ul>
 */
public final class IoxToJtsGeometry {

    private static final double DEFAULT_MAX_OVERLAP = 0.0d;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public Geometry convert(IomObject geometry) throws ShapefileMappingException {
        if (geometry == null) {
            return null;
        }
        String tag = geometry.getobjecttag();
        String upper = tag == null ? "" : tag.toUpperCase();
        try {
            if (upper.endsWith("MULTICOORD")) {
                return Iox2jts.multicoord2JTS(geometry);
            }
            if (upper.endsWith("COORD")) {
                Coordinate coordinate = Iox2jts.coord2JTS(geometry);
                return geometryFactory.createPoint(coordinate);
            }
            if (upper.endsWith("MULTIPOLYLINE")) {
                return Iox2jts.multipolyline2JTS(geometry, DEFAULT_MAX_OVERLAP);
            }
            if (upper.endsWith("POLYLINE")) {
                return Iox2jts.polyline2JTSlineString(geometry, false, DEFAULT_MAX_OVERLAP);
            }
            if (upper.endsWith("MULTISURFACE")) {
                return Iox2jts.multisurface2JTS(geometry, DEFAULT_MAX_OVERLAP, 0);
            }
            if (upper.endsWith("SURFACE") || upper.endsWith("AREA") || geometry.getattrvaluecount("surface") > 0) {
                if (geometry.getattrvaluecount("surface") > 0 && !upper.endsWith("SURFACE")) {
                    return Iox2jts.multisurface2JTS(geometry, DEFAULT_MAX_OVERLAP, 0);
                }
                return Iox2jts.surface2JTS(geometry, DEFAULT_MAX_OVERLAP);
            }
        } catch (Iox2jtsException e) {
            throw new ShapefileMappingException(
                    "Cannot convert IOX geometry '" + tag + "' to JTS: " + e.getMessage(), e);
        }
        throw new ShapefileMappingException("Unsupported IOX geometry tag '" + tag
                + "'. Supported: COORD, POLYLINE, MULTIPOLYLINE, SURFACE/AREA, MULTISURFACE.");
    }
}
