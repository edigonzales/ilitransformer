package guru.interlis.transformer.geometry;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;

import ch.ehi.iox.objpool.ObjectPoolManager;
import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AreaType;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iom_j.itf.ModelUtilities;
import ch.interlis.iom_j.itf.impl.ItfAreaPolygon2Linetable;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;
import ch.interlis.iox_j.jts.Jts2iox;
import ch.interlis.iox_j.logging.LogEventFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

public final class ItfGeometryWriter implements IoxWriter {

    private static final double DEFAULT_MAX_OVERLAP = 0.001d;

    private final IoxWriter delegate;
    private final TransferDescription transferDescription;
    private final Map<String, List<GeometryAttr>> geometryByTag;
    private final DiagnosticCollector diagnostics;

    private List<Object> currentItfTables;
    private Map<String, List<BufferedObj>> bufferedObjects;
    private final ObjectPoolManager objectPoolManager = new ObjectPoolManager();
    private final Map<String, List<AreaEntry>> areaAccumulator = new HashMap<>();
    private long oidCounter;
    private long maxBaseOid;

    public ItfGeometryWriter(IoxWriter delegate, TransferDescription transferDescription) {
        this(delegate, transferDescription, null);
    }

    public ItfGeometryWriter(
            IoxWriter delegate, TransferDescription transferDescription, DiagnosticCollector diagnostics) {
        this.delegate = Objects.requireNonNull(delegate);
        this.transferDescription = Objects.requireNonNull(transferDescription);
        this.geometryByTag = buildGeometryMap(transferDescription);
        this.diagnostics = diagnostics;
        this.bufferedObjects = new LinkedHashMap<>();
    }

    @Override
    public void write(IoxEvent event) throws IoxException {
        if (event instanceof ch.interlis.iox_j.StartTransferEvent
                || event instanceof ch.interlis.iox_j.EndTransferEvent) {
            delegate.write(event);
            return;
        }
        if (event instanceof ch.interlis.iox_j.StartBasketEvent basket) {
            String[] parts =
                    basket.getType() == null ? new String[0] : basket.getType().split("\\.");
            if (parts.length >= 2) {
                currentItfTables = ModelUtilities.getItfTables(transferDescription, parts[0], parts[1]);
            } else {
                currentItfTables = null;
            }
            bufferedObjects = new LinkedHashMap<>();
            areaAccumulator.clear();
            oidCounter = 0;
            maxBaseOid = 0;
            delegate.write(event);
            return;
        }
        if (event instanceof ObjectEvent obj) {
            handleObject(obj.getIomObject());
            return;
        }
        if (event instanceof ch.interlis.iox_j.EndBasketEvent) {
            flushBufferedObjects();
            delegate.write(event);
            currentItfTables = null;
            bufferedObjects = new LinkedHashMap<>();
            areaAccumulator.clear();
            return;
        }
        delegate.write(event);
    }

    @Override
    public void close() throws IoxException {
        objectPoolManager.close();
        delegate.close();
    }

    @Override
    public void flush() throws IoxException {
        delegate.flush();
    }

    @Override
    public void setFactory(IoxFactoryCollection f) throws IoxException {
        delegate.setFactory(f);
    }

    @Override
    public IoxFactoryCollection getFactory() throws IoxException {
        return delegate.getFactory();
    }

    @Override
    public IomObject createIomObject(String t, String o) throws IoxException {
        return delegate.createIomObject(t, o);
    }

    private void handleObject(IomObject source) throws IoxException {
        updateMaxBaseOid(source.getobjectoid());

        List<GeometryAttr> geomAttrs = geometryByTag.get(source.getobjecttag());
        if (geomAttrs == null || geomAttrs.isEmpty()) {
            buffer(tableName(source), new ImmediateObj(new Iom_jObject(source)));
            return;
        }

        Iom_jObject baseObj = new Iom_jObject(source);
        for (GeometryAttr geometryAttr : geomAttrs) {
            String attrName = geometryAttr.attribute().getName();
            String helperAttrName = helperAttrName(attrName);
            IomObject geomValue = firstObjectAttribute(source, attrName);

            removeObjectAttribute(baseObj, attrName);
            removeObjectAttribute(baseObj, helperAttrName);

            if (geomValue == null) {
                continue;
            }

            if (geometryAttr.isArea()) {
                IomObject surfaceGeometry = toSurfaceContainer(geomValue);
                if (surfaceGeometry == null) {
                    reportGeometryError(
                            DiagnosticCode.GEOM_INVALID,
                            source,
                            attrName,
                            "Geometry object is not a serializable AREA container",
                            "Provide canonical SURFACE/MULTISURFACE geometry before writing ITF");
                    continue;
                }
                int surfaceCount = surfaceGeometry.getattrvaluecount("surface");
                if (surfaceCount <= 0) {
                    reportGeometryError(
                            DiagnosticCode.GEOM_INVALID,
                            source,
                            attrName,
                            "AREA geometry contains no surface elements",
                            "Provide a single-surface AREA geometry");
                    continue;
                }
                if (surfaceCount > 1) {
                    reportGeometryError(
                            DiagnosticCode.GEOM_INVALID,
                            source,
                            attrName,
                            "AREA with multiple surface elements is not supported in ILI1 reverse serialization",
                            "Provide a single-surface AREA geometry");
                    continue;
                }
                IomObject areaPoint = resolveAreaPoint(source, attrName, geomValue);
                if (areaPoint == null) {
                    continue;
                }
                areaAccumulator
                        .computeIfAbsent(geometryTableTag(geometryAttr.attribute()), k -> new ArrayList<>())
                        .add(new AreaEntry(source.getobjectoid(), surfaceGeometry, source, attrName, geometryAttr));
                baseObj.addattrobj(attrName, new Iom_jObject(areaPoint));
            } else {
                PreparedGeometry prepared = prepareGeometry(source, geometryAttr, geomValue);
                if (prepared == null) {
                    continue;
                }
                for (HelperRowObj row : prepared.rows()) {
                    buffer(geometryTableName(geometryAttr.attribute()), row);
                }
            }
        }
        buffer(tableName(source), new ImmediateObj(baseObj));
    }

    private PreparedGeometry prepareGeometry(IomObject source, GeometryAttr geometryAttr, IomObject geomValue)
            throws IoxException {
        String attrName = geometryAttr.attribute().getName();
        List<IomObject> polylines = extractHelperPolylines(source, geometryAttr, geomValue);
        if (polylines == null) {
            return null;
        }
        if (polylines.isEmpty()) {
            reportGeometryError(
                    DiagnosticCode.GEOM_INVALID,
                    source,
                    attrName,
                    "Geometry object has no boundary/polyline content",
                    "Provide a valid SURFACE/AREA geometry object before writing ITF");
            return null;
        }
        if (!validatePolylines(polylines, source, attrName)) {
            return null;
        }

        if (source.getobjectoid() == null) {
            reportGeometryError(
                    DiagnosticCode.GEOM_INVALID,
                    source,
                    attrName,
                    "SURFACE helper rows require a source object OID",
                    "Ensure the source object has an OID before writing ILI1 helper tables");
            return null;
        }

        List<HelperRowObj> rows = new ArrayList<>();
        String tableName = geometryTableName(geometryAttr.attribute());
        String tableTag = geometryTableTag(geometryAttr.attribute());
        String geomAttrName = ModelUtilities.getHelperTableGeomAttrName(geometryAttr.attribute());
        String refAttrName = ModelUtilities.getHelperTableMainTableRef(geometryAttr.attribute());
        for (IomObject polyline : polylines) {
            rows.add(new HelperRowObj(tableTag, geomAttrName, refAttrName, source.getobjectoid(), polyline));
        }
        return new PreparedGeometry(rows, null);
    }

    private List<IomObject> extractHelperPolylines(IomObject source, GeometryAttr geometryAttr, IomObject geomValue) {
        String attrName = geometryAttr.attribute().getName();
        IomObject surfaceGeometry = toSurfaceContainer(geomValue);
        if (surfaceGeometry == null) {
            reportGeometryError(
                    DiagnosticCode.GEOM_INVALID,
                    source,
                    attrName,
                    "Geometry object is not a serializable SURFACE/AREA container",
                    "Provide canonical SURFACE/MULTISURFACE geometry before writing ITF");
            return null;
        }

        int surfaceCount = surfaceGeometry.getattrvaluecount("surface");
        if (surfaceCount <= 0) {
            reportGeometryError(
                    DiagnosticCode.GEOM_INVALID,
                    source,
                    attrName,
                    "Geometry object contains no surface elements",
                    "Provide canonical SURFACE/MULTISURFACE geometry before writing ITF");
            return null;
        }

        try {
            if (surfaceCount == 1) {
                return ItfAreaPolygon2Linetable.getLinesFromPolygon(surfaceGeometry);
            }
            return ItfAreaPolygon2Linetable.getLinesFromMultiPolygon(surfaceGeometry);
        } catch (IllegalArgumentException ex) {
            reportGeometryError(
                    DiagnosticCode.GEOM_INVALID,
                    source,
                    attrName,
                    "Geometry is not serializable to ITF helper tables: " + ex.getMessage(),
                    "Provide an unclipped canonical surface geometry");
            return null;
        }
    }

    private IomObject toSurfaceContainer(IomObject geomValue) {
        if (geomValue == null) {
            return null;
        }
        if (geomValue.getattrvaluecount("surface") > 0) {
            return new Iom_jObject(geomValue);
        }
        if (geomValue.getattrvaluecount("boundary") > 0 || "SURFACE".equalsIgnoreCase(geomValue.getobjecttag())) {
            Iom_jObject wrapped = new Iom_jObject("MULTISURFACE", null);
            wrapped.addattrobj("surface", new Iom_jObject(geomValue));
            return wrapped;
        }
        return null;
    }

    private boolean validatePolylines(List<IomObject> polylines, IomObject source, String attrName) {
        for (IomObject polyline : polylines) {
            if (polyline == null) {
                reportGeometryError(
                        DiagnosticCode.GEOM_INVALID,
                        source,
                        attrName,
                        "Polyline entry is missing",
                        "Provide canonical COORD/ARC polyline segments");
                return false;
            }
            if (polyline.getattrobj("lineattr", 0) != null) {
                reportGeometryError(
                        DiagnosticCode.GEOM_LINEATTR_UNSUPPORTED,
                        source,
                        attrName,
                        "LINEATTR is not supported in XTF -> ITF surface/area serialization",
                        "Remove LINEATTR from the reverse mapping scope or implement a dedicated line attribute writer");
                return false;
            }

            int sequenceCount = polyline.getattrvaluecount("sequence");
            if (sequenceCount == 0) {
                reportGeometryError(
                        DiagnosticCode.GEOM_INVALID,
                        source,
                        attrName,
                        "Polyline contains no sequence",
                        "Provide canonical COORD/ARC polyline segments");
                return false;
            }
            if (sequenceCount > 1) {
                reportGeometryError(
                        DiagnosticCode.GEOM_SEGMENT_UNSUPPORTED,
                        source,
                        attrName,
                        "Polyline with multiple sequences is not supported in ITF reverse serialization",
                        "Provide an unclipped single-sequence polyline");
                return false;
            }

            IomObject sequence = polyline.getattrobj("sequence", 0);
            if (sequence == null) {
                reportGeometryError(
                        DiagnosticCode.GEOM_INVALID,
                        source,
                        attrName,
                        "Polyline sequence is missing",
                        "Provide canonical COORD/ARC polyline segments");
                return false;
            }

            int segmentCount = sequence.getattrvaluecount("segment");
            if (segmentCount == 0) {
                reportGeometryError(
                        DiagnosticCode.GEOM_INVALID,
                        source,
                        attrName,
                        "Polyline contains no writable segments",
                        "Provide canonical COORD/ARC polyline segments");
                return false;
            }

            for (int i = 0; i < segmentCount; i++) {
                IomObject segment = sequence.getattrobj("segment", i);
                if (segment == null) {
                    reportGeometryError(
                            DiagnosticCode.GEOM_INVALID,
                            source,
                            attrName,
                            "Polyline segment is missing",
                            "Provide canonical COORD/ARC polyline segments");
                    return false;
                }
                String tag = segment.getobjecttag();
                if (i == 0 && !"COORD".equalsIgnoreCase(tag)) {
                    reportGeometryError(
                            DiagnosticCode.GEOM_SEGMENT_UNSUPPORTED,
                            source,
                            attrName,
                            "Polyline must start with a COORD segment",
                            "Provide canonical COORD/ARC polyline segments");
                    return false;
                }
                if ("COORD".equalsIgnoreCase(tag)) {
                    if (segment.getattrvalue("C1") == null || segment.getattrvalue("C2") == null) {
                        reportGeometryError(
                                DiagnosticCode.GEOM_INVALID,
                                source,
                                attrName,
                                "COORD segment has no C1/C2 values",
                                "Provide canonical COORD segments");
                        return false;
                    }
                    continue;
                }
                if ("ARC".equalsIgnoreCase(tag)) {
                    if (segment.getattrvalue("A1") == null
                            || segment.getattrvalue("A2") == null
                            || segment.getattrvalue("C1") == null
                            || segment.getattrvalue("C2") == null) {
                        reportGeometryError(
                                DiagnosticCode.GEOM_INVALID,
                                source,
                                attrName,
                                "ARC segment has no A1/A2/C1/C2 values",
                                "Provide canonical ARC segments with midpoint and endpoint");
                        return false;
                    }
                    continue;
                }
                reportGeometryError(
                        DiagnosticCode.GEOM_SEGMENT_UNSUPPORTED,
                        source,
                        attrName,
                        "Unsupported polyline segment type: " + tag,
                        "Only canonical COORD and ARC segments are supported in ITF reverse serialization");
                return false;
            }
        }
        return true;
    }

    private IomObject resolveAreaPoint(IomObject source, String attrName, IomObject geomValue) {
        IomObject helperPoint = firstObjectAttribute(source, helperAttrName(attrName));
        if (helperPoint != null) {
            return new Iom_jObject(helperPoint);
        }
        try {
            Geometry geometry = toJtsSurface(geomValue);
            if (geometry == null || geometry.isEmpty()) {
                reportGeometryError(
                        DiagnosticCode.GEOM_AREA_POINT_MISSING,
                        source,
                        attrName,
                        "AREA geometry is empty and has no point-on-surface",
                        "Provide a valid surface geometry or preserve the ILI1 helper point");
                return null;
            }
            if (!geometry.isValid()) {
                Geometry fixed = geometry.buffer(0);
                if (!fixed.isEmpty()
                        && ("Polygon".equals(fixed.getGeometryType())
                                || "MultiPolygon".equals(fixed.getGeometryType()))) {
                    geometry = fixed;
                }
            }
            Point interiorPoint = geometry.getInteriorPoint();
            if (interiorPoint == null || interiorPoint.isEmpty() || interiorPoint.getCoordinate() == null) {
                reportGeometryError(
                        DiagnosticCode.GEOM_AREA_POINT_MISSING,
                        source,
                        attrName,
                        "Could not derive a deterministic point-on-surface for AREA geometry",
                        "Provide an explicit point-on-surface or fix the AREA geometry");
                return null;
            }
            return Jts2iox.JTS2coord(interiorPoint.getCoordinate());
        } catch (Iox2jtsException | RuntimeException ex) {
            reportGeometryError(
                    DiagnosticCode.GEOM_AREA_POINT_MISSING,
                    source,
                    attrName,
                    "Could not derive point-on-surface for AREA geometry: " + ex.getMessage(),
                    "Provide an explicit point-on-surface or fix the AREA geometry");
            return null;
        }
    }

    private Geometry toJtsSurface(IomObject geomValue) throws Iox2jtsException {
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

    private void buildAreaTopology() throws IoxException {
        for (var entry : areaAccumulator.entrySet()) {
            String tableTag = entry.getKey();
            List<AreaEntry> areas = entry.getValue();
            if (areas.isEmpty()) {
                continue;
            }

            ItfAreaPolygon2Linetable topoWriter = new ItfAreaPolygon2Linetable(tableTag, objectPoolManager);
            LogEventFactory logEventFactory = new LogEventFactory();

            for (AreaEntry area : areas) {
                try {
                    topoWriter.addMultiPolygon(
                            area.sourceOid(), null, area.surfaceGeometry(), "Warning", logEventFactory);
                } catch (IoxException e) {
                    reportGeometryError(
                            DiagnosticCode.GEOM_INVALID,
                            area.source(),
                            area.attrName(),
                            "Topology build failed: " + e.getMessage(),
                            "Fix source geometry");
                }
            }

            List<IomObject> topoLines;
            try {
                topoLines = topoWriter.getLines();
            } catch (IoxException e) {
                reportGeometryError(
                        DiagnosticCode.GEOM_INVALID,
                        null,
                        tableTag,
                        "AREA topology has intersections: " + e.getMessage(),
                        "Source geometries may have invalid topology");
                continue;
            }

            if (topoLines.isEmpty()) {
                continue;
            }

            AreaEntry first = areas.get(0);
            String tableName = geometryTableName(first.geometryAttr().attribute());
            String geomAttrName = ModelUtilities.getHelperTableGeomAttrName(
                    first.geometryAttr().attribute());
            for (IomObject polyline : topoLines) {
                buffer(tableName, new HelperRowObj(tableTag, geomAttrName, null, null, polyline));
            }
        }
        areaAccumulator.clear();
    }

    private void buffer(String tableName, BufferedObj obj) {
        bufferedObjects.computeIfAbsent(tableName, ignored -> new ArrayList<>()).add(obj);
    }

    private void flushBufferedObjects() throws IoxException {
        oidCounter = maxBaseOid;
        if (!areaAccumulator.isEmpty()) {
            buildAreaTopology();
        }
        if (currentItfTables != null) {
            for (Object element : currentItfTables) {
                writeBufferedTable(itfTableName(element));
            }
        }
        for (String tableName : new ArrayList<>(bufferedObjects.keySet())) {
            writeBufferedTable(tableName);
        }
        bufferedObjects.clear();
    }

    private void writeBufferedTable(String tableName) throws IoxException {
        List<BufferedObj> objects = bufferedObjects.remove(tableName);
        if (objects == null || objects.isEmpty()) {
            return;
        }
        for (BufferedObj object : objects) {
            String oid = object.requiresGeneratedOid() ? nextOid() : null;
            delegate.write(new ch.interlis.iox_j.ObjectEvent(object.materialize(oid)));
        }
    }

    private String nextOid() {
        return Long.toString(++oidCounter);
    }

    private void updateMaxBaseOid(String value) {
        Long num = tryParseLong(value);
        if (num != null && num > maxBaseOid) {
            maxBaseOid = num;
        }
    }

    private Map<String, List<GeometryAttr>> buildGeometryMap(TransferDescription td) {
        Map<String, List<GeometryAttr>> map = new HashMap<>();
        var modelIt = td.iterator();
        while (modelIt.hasNext()) {
            var modelElement = modelIt.next();
            if (!(modelElement instanceof Model model)) {
                continue;
            }
            var topicIt = model.iterator();
            while (topicIt.hasNext()) {
                var topicElement = topicIt.next();
                if (!(topicElement instanceof Topic topic)) {
                    continue;
                }
                for (var viewableElement : topic.getViewables()) {
                    if (!(viewableElement instanceof Viewable viewable)
                            || !(viewable instanceof AbstractClassDef<?> classDef)) {
                        continue;
                    }
                    List<GeometryAttr> attrs = new ArrayList<>();
                    var attrIt = classDef.getAttributes();
                    while (attrIt.hasNext()) {
                        var attributeElement = attrIt.next();
                        if (!(attributeElement instanceof AttributeDef attribute)) {
                            continue;
                        }
                        Type real = Type.findReal(attribute.getDomain());
                        if (real instanceof AreaType) {
                            attrs.add(new GeometryAttr(attribute, true));
                        } else if (real instanceof SurfaceOrAreaType) {
                            attrs.add(new GeometryAttr(attribute, false));
                        }
                    }
                    if (!attrs.isEmpty()) {
                        map.put(viewable.getScopedName(null), attrs);
                    }
                }
            }
        }
        return map;
    }

    private IomObject firstObjectAttribute(IomObject object, String attrName) {
        if (object.getattrvaluecount(attrName) <= 0) {
            return null;
        }
        return object.getattrobj(attrName, 0);
    }

    private void removeObjectAttribute(Iom_jObject object, String attrName) {
        for (int i = object.getattrvaluecount(attrName) - 1; i >= 0; i--) {
            object.deleteattrobj(attrName, i);
        }
    }

    private void reportGeometryError(String code, IomObject source, String attrName, String detail, String suggestion) {
        if (diagnostics == null) {
            return;
        }
        String oid = source.getobjectoid() != null ? source.getobjectoid() : "<no-oid>";
        diagnostics.add(new Diagnostic(
                code,
                Severity.ERROR,
                "ITF geometry write failed for " + source.getobjecttag() + "." + attrName + ": " + detail,
                source.getobjecttag() + "/" + oid,
                suggestion));
    }

    private static String tableName(IomObject obj) {
        return tableName(obj.getobjecttag());
    }

    private static String tableName(String scopedName) {
        String[] parts = scopedName.split("\\.");
        return parts.length == 0 ? scopedName : parts[parts.length - 1];
    }

    private static String geometryTableName(AttributeDef attr) {
        return ((Viewable) attr.getContainer()).getName() + "_" + attr.getName();
    }

    private static String geometryTableTag(AttributeDef attr) {
        return ((Viewable) attr.getContainer()).getScopedName(null) + "_" + attr.getName();
    }

    private static String helperAttrName(String attrName) {
        return "_itf_" + attrName;
    }

    private static String itfTableName(Object element) {
        if (element instanceof AttributeDef attribute) {
            return geometryTableName(attribute);
        }
        if (element instanceof Viewable viewable) {
            return viewable.getName();
        }
        return element.toString();
    }

    private static Long tryParseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private interface BufferedObj {
        boolean requiresGeneratedOid();

        IomObject materialize(String generatedOid);
    }

    private record ImmediateObj(IomObject obj) implements BufferedObj {
        @Override
        public boolean requiresGeneratedOid() {
            return false;
        }

        @Override
        public IomObject materialize(String generatedOid) {
            return obj;
        }
    }

    private record HelperRowObj(
            String tableName, String geomAttrName, String refAttrName, String sourceOid, IomObject polyline)
            implements BufferedObj {
        private HelperRowObj {
            polyline = new Iom_jObject(polyline);
        }

        @Override
        public boolean requiresGeneratedOid() {
            return true;
        }

        @Override
        public IomObject materialize(String generatedOid) {
            Iom_jObject helper = new Iom_jObject(tableName, generatedOid);
            helper.addattrobj(geomAttrName, new Iom_jObject(polyline));
            if (refAttrName != null) {
                IomObject ref = helper.addattrobj(refAttrName, "REF");
                ref.setobjectrefoid(sourceOid);
            }
            return helper;
        }
    }

    private record GeometryAttr(AttributeDef attribute, boolean isArea) {}

    private record AreaEntry(
            String sourceOid,
            IomObject surfaceGeometry,
            IomObject source,
            String attrName,
            GeometryAttr geometryAttr) {}

    private record PreparedGeometry(List<HelperRowObj> rows, IomObject areaPoint) {}
}
