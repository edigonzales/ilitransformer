package guru.interlis.transformer.io.shp.geom;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.core.Bounds;
import guru.interlis.transformer.io.shp.core.EndianByteBuffer;
import guru.interlis.transformer.io.shp.core.ShapeRecord;
import guru.interlis.transformer.io.shp.core.ShapeType;

import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public final class ShpGeometryDecoder {

    private final GeometryFactory geometryFactory;

    public ShpGeometryDecoder() {
        this.geometryFactory = new GeometryFactory();
    }

    public Geometry decode(ShapeRecord record) throws ShapefileMappingException {
        ShapeType shapeType = record.shapeType();
        EndianByteBuffer buf = EndianByteBuffer.wrap(record.content());

        int shapeTypeCode = buf.getLittleInt();

        return switch (shapeType) {
            case NULL -> null;
            case POINT -> decodePoint(buf);
            case POLYLINE -> decodePolyLine(buf);
            case POLYGON -> decodePolygon(buf);
            default ->
                throw new ShapefileMappingException("Unsupported shape type for geometry decoding: " + shapeType);
        };
    }

    private Point decodePoint(EndianByteBuffer buf) {
        double x = buf.getLittleDouble();
        double y = buf.getLittleDouble();
        return geometryFactory.createPoint(new Coordinate(x, y));
    }

    private Geometry decodePolyLine(EndianByteBuffer buf) throws ShapefileMappingException {
        Bounds.read(buf);
        int numParts = buf.getLittleInt();
        int numPoints = buf.getLittleInt();

        if (numPoints < 2) {
            throw new ShapefileMappingException(
                    "PolyLine has " + numPoints + " points, need at least 2 for a valid line");
        }
        if (numParts < 1) {
            throw new ShapefileMappingException("PolyLine has " + numParts + " parts, need at least 1");
        }

        int[] parts = new int[numParts];
        for (int i = 0; i < numParts; i++) {
            parts[i] = buf.getLittleInt();
        }

        Coordinate[] allPoints = new Coordinate[numPoints];
        for (int i = 0; i < numPoints; i++) {
            double x = buf.getLittleDouble();
            double y = buf.getLittleDouble();
            allPoints[i] = new Coordinate(x, y);
        }

        if (numParts == 1) {
            return geometryFactory.createLineString(allPoints);
        }

        LineString[] lineStrings = new LineString[numParts];
        for (int i = 0; i < numParts; i++) {
            int start = parts[i];
            int end = (i + 1 < numParts) ? parts[i + 1] : numPoints;
            int len = end - start;
            Coordinate[] segment = new Coordinate[len];
            System.arraycopy(allPoints, start, segment, 0, len);
            lineStrings[i] = geometryFactory.createLineString(segment);
        }
        return geometryFactory.createMultiLineString(lineStrings);
    }

    private Geometry decodePolygon(EndianByteBuffer buf) throws ShapefileMappingException {
        Bounds.read(buf);
        int numParts = buf.getLittleInt();
        int numPoints = buf.getLittleInt();

        if (numParts < 1) {
            throw new ShapefileMappingException("Polygon has " + numParts + " parts, need at least 1");
        }
        if (numPoints < 3) {
            throw new ShapefileMappingException("Polygon has " + numPoints + " points, need at least 3");
        }

        int[] parts = new int[numParts];
        for (int i = 0; i < numParts; i++) {
            parts[i] = buf.getLittleInt();
        }

        Coordinate[] allPoints = new Coordinate[numPoints];
        for (int i = 0; i < numPoints; i++) {
            double x = buf.getLittleDouble();
            double y = buf.getLittleDouble();
            allPoints[i] = new Coordinate(x, y);
        }

        List<LinearRing> allRings = new ArrayList<>();
        for (int i = 0; i < numParts; i++) {
            int start = parts[i];
            int end = (i + 1 < numParts) ? parts[i + 1] : numPoints;
            int len = end - start;

            if (len < 4) {
                throw new ShapefileMappingException(
                        "Polygon ring " + i + " has " + len + " points, need at least 4 (including closing point)");
            }

            Coordinate[] ringCoords = new Coordinate[len];
            System.arraycopy(allPoints, start, ringCoords, 0, len);

            Coordinate first = ringCoords[0];
            Coordinate last = ringCoords[len - 1];
            if (first.x != last.x || first.y != last.y) {
                throw new ShapefileMappingException("Polygon ring " + i + " is not closed: first=(" + first.x + ","
                        + first.y + "), last=(" + last.x + "," + last.y + ")");
            }

            allRings.add(geometryFactory.createLinearRing(ringCoords));
        }

        List<LinearRing> shells = new ArrayList<>();
        List<LinearRing> holes = new ArrayList<>();

        for (LinearRing ring : allRings) {
            double area = signedArea(ring);
            if (area < 0.0) {
                shells.add(ring);
            } else {
                holes.add(ring);
            }
        }

        if (shells.isEmpty()) {
            throw new ShapefileMappingException("Polygon has no shell rings");
        }

        List<List<LinearRing>> shellHoles = new ArrayList<>(shells.size());
        for (int i = 0; i < shells.size(); i++) {
            shellHoles.add(new ArrayList<>());
        }

        Polygon[] shellPolygons = new Polygon[shells.size()];
        for (int i = 0; i < shells.size(); i++) {
            shellPolygons[i] = geometryFactory.createPolygon(shells.get(i));
        }

        for (int h = 0; h < holes.size(); h++) {
            LinearRing holeRing = holes.get(h);
            Coordinate holePoint = holeRing.getCoordinateN(0);
            Point holeTestPoint = geometryFactory.createPoint(holePoint);

            int bestShell = -1;
            double bestArea = Double.MAX_VALUE;
            for (int s = 0; s < shells.size(); s++) {
                if (shellPolygons[s].contains(holeTestPoint)) {
                    double shellArea = Math.abs(signedArea(shells.get(s)));
                    if (shellArea < bestArea) {
                        bestArea = shellArea;
                        bestShell = s;
                    }
                }
            }

            if (bestShell < 0) {
                throw new ShapefileMappingException("Polygon hole ring " + h + " is not contained by any shell ring");
            }
            shellHoles.get(bestShell).add(holeRing);
        }

        if (shells.size() == 1) {
            return geometryFactory.createPolygon(
                    shells.get(0), shellHoles.get(0).toArray(new LinearRing[0]));
        }

        Polygon[] polygons = new Polygon[shells.size()];
        for (int i = 0; i < shells.size(); i++) {
            polygons[i] = geometryFactory.createPolygon(
                    shells.get(i), shellHoles.get(i).toArray(new LinearRing[0]));
        }
        return geometryFactory.createMultiPolygon(polygons);
    }

    static double signedArea(LinearRing ring) {
        Coordinate[] coords = ring.getCoordinates();
        double area = 0.0;
        int n = coords.length;
        for (int i = 0; i < n - 1; i++) {
            area += coords[i].x * coords[i + 1].y - coords[i + 1].x * coords[i].y;
        }
        return area / 2.0;
    }
}
