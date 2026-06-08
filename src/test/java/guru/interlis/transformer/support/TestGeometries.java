package guru.interlis.transformer.support;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;

public final class TestGeometries {
    private TestGeometries() {
    }

    public static IomObject surface(IomObject... boundaries) {
        Iom_jObject multiSurface = new Iom_jObject("MULTISURFACE", null);
        Iom_jObject surface = new Iom_jObject("SURFACE", null);
        for (IomObject boundary : boundaries) {
            surface.addattrobj("boundary", boundary);
        }
        multiSurface.addattrobj("surface", surface);
        return multiSurface;
    }

    public static IomObject boundary(IomObject... segments) {
        Iom_jObject boundary = new Iom_jObject("boundary", null);
        boundary.addattrobj("polyline", polyline(segments));
        return boundary;
    }

    public static IomObject polyline(IomObject... segments) {
        Iom_jObject polyline = new Iom_jObject("POLYLINE", null);
        Iom_jObject sequence = new Iom_jObject("sequence", null);
        for (IomObject segment : segments) {
            sequence.addattrobj("segment", segment);
        }
        polyline.addattrobj("sequence", sequence);
        return polyline;
    }

    public static IomObject coord(double x, double y) {
        Iom_jObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", Double.toString(x));
        coord.setattrvalue("C2", Double.toString(y));
        return coord;
    }

    public static IomObject arc(double midX, double midY, double endX, double endY) {
        Iom_jObject arc = new Iom_jObject("ARC", null);
        arc.setattrvalue("A1", Double.toString(midX));
        arc.setattrvalue("A2", Double.toString(midY));
        arc.setattrvalue("C1", Double.toString(endX));
        arc.setattrvalue("C2", Double.toString(endY));
        return arc;
    }
}
