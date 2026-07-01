package guru.interlis.transformer.io.shp;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.io.FormatOpenContext;
import guru.interlis.transformer.io.FormatOptions;
import guru.interlis.transformer.io.shp.core.ShapeType;
import guru.interlis.transformer.io.shp.core.ShapefileDatasetWriter;
import guru.interlis.transformer.io.shp.core.ShapefileSchema;
import guru.interlis.transformer.io.shp.core.ShapefileWriteOptions;
import guru.interlis.transformer.io.shp.mapping.DbfNameMapper;
import guru.interlis.transformer.io.shp.mapping.IomToDbfMapper;
import guru.interlis.transformer.io.shp.mapping.ShapefileSchemaBuilder.WriteSchema;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.DefaultIoxFactoryCollection;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * IOX writer adapter that serialises a single IOX class into one Shapefile dataset ({@code .shp},
 * {@code .shx}, {@code .dbf}, plus {@code .cpg} and optionally {@code .prj}).
 *
 * <p>The writer is strict by design: it writes exactly one class and one geometry type. Objects of
 * a different class, a second basket (unless {@code failOnMultipleBaskets=false}) or a geometry that
 * does not match the settled shape type are rejected with a {@link ShapefileMappingException}.
 *
 * <p>The DBF schema is derived from the INTERLIS output class (model-driven) when it can be
 * resolved, otherwise from the first written object. Non-scalar attributes are skipped and reported
 * as diagnostics. The low-level {@link ShapefileDatasetWriter} streams directly into the files and
 * patches the headers in {@link #close()}/finish; no per-feature buffering happens here.
 */
public final class ShapefileIoxWriter implements IoxWriter {

    private enum State {
        INIT,
        TRANSFER_STARTED,
        INSIDE_BASKET,
        FINISHED
    }

    private final String outputId;
    private final Path targetShp;
    private final ShapefileWritePlan plan;
    private final OutputBinding binding;
    private final DiagnosticCollector diagnostics;

    private State state = State.INIT;
    private int basketCount = 0;
    private String resolvedClassName;
    private ShapefileDatasetWriter datasetWriter;
    private IomToDbfMapper mapper;
    private WriteSchema schema;
    private IoxFactoryCollection factory;

    private ShapefileIoxWriter(
            String outputId,
            Path targetShp,
            ShapefileWritePlan plan,
            OutputBinding binding,
            DiagnosticCollector diagnostics) {
        this.outputId = outputId;
        this.targetShp = targetShp;
        this.plan = plan;
        this.binding = binding;
        this.diagnostics = diagnostics;
    }

    public static ShapefileIoxWriter open(OutputBinding binding, FormatOpenContext context)
            throws ShapefileMappingException {
        String outputId = binding.outputId() != null ? binding.outputId() : "?";
        FormatOptions options = FormatOptions.of(binding.options());
        ShapefileOptions shapeOpts = ShapefileOptions.output(options);
        ShapefileWritePlan plan = ShapefileWritePlan.create(outputId, shapeOpts);
        return new ShapefileIoxWriter(outputId, binding.path(), plan, binding, context.diagnostics());
    }

    @Override
    public void write(IoxEvent event) throws IoxException {
        if (event instanceof StartTransferEvent) {
            if (state == State.INIT) {
                state = State.TRANSFER_STARTED;
            }
            return;
        }
        if (event instanceof StartBasketEvent) {
            handleStartBasket();
            return;
        }
        if (event instanceof ObjectEvent objectEvent) {
            handleObject(objectEvent.getIomObject());
            return;
        }
        if (event instanceof EndTransferEvent) {
            handleEndTransfer();
            return;
        }
        // EndBasketEvent and other lifecycle events are accepted without action.
    }

    private void handleStartBasket() throws IoxException {
        basketCount++;
        if (basketCount > 1 && plan.failOnMultipleBaskets()) {
            throw new ShapefileMappingException("SHP output '" + outputId
                    + "': the Shapefile writer handles a single basket only. Set option failOnMultipleBaskets=false "
                    + "to merge multiple baskets into one Shapefile.");
        }
        state = State.INSIDE_BASKET;
    }

    private void handleObject(IomObject object) throws IoxException {
        try {
            if (datasetWriter == null) {
                initWriter(object);
            } else {
                String tag = object.getobjecttag();
                if (!resolvedClassName.equals(tag)) {
                    throw new ShapefileMappingException("SHP output '" + outputId + "': cannot write object of class '"
                            + tag + "'. This Shapefile writer is configured for '" + resolvedClassName + "'.");
                }
            }

            Geometry geometry = mapper.extractGeometry(object);
            validateGeometryType(geometry, object.getobjectoid());
            Object[] values = mapper.extractValues(object);
            datasetWriter.write(geometry, values);
        } catch (IOException e) {
            throw new IoxException("SHP output '" + outputId + "': I/O error writing object: " + e.getMessage(), e);
        }
    }

    private void initWriter(IomObject firstObject) throws IoxException, IOException {
        resolvedClassName = plan.configuredClassName().orElseGet(firstObject::getobjecttag);
        schema = plan.buildSchema(
                binding.typeSystem(), diagnostics, Optional.ofNullable(resolvedClassName), Optional.of(firstObject));
        mapper = new IomToDbfMapper(schema);
        openDatasetWriter();
    }

    private void openDatasetWriter() throws IOException, ShapefileMappingException {
        ShapefileSchema coreSchema = new ShapefileSchema(schema.shapeType(), schema.dbfFields());
        ShapefileWriteOptions writeOptions =
                new ShapefileWriteOptions(plan.charset(), plan.prjWkt(), plan.overflowPolicy());
        datasetWriter = ShapefileDatasetWriter.open(targetShp, coreSchema, writeOptions);
    }

    private void validateGeometryType(Geometry geometry, String oid) throws ShapefileMappingException {
        ShapeType actual = shapeTypeOf(geometry);
        if (actual == null) {
            return; // null/empty geometry is a NULL shape and allowed in any dataset
        }
        if (actual != schema.shapeType()) {
            throw new ShapefileMappingException("SHP output '" + outputId + "': object "
                    + (oid != null ? "'" + oid + "' " : "") + "has geometry shape type " + actual
                    + " but this Shapefile is configured for " + schema.shapeType()
                    + ". A Shapefile writer handles a single geometry type only.");
        }
    }

    private static ShapeType shapeTypeOf(Geometry geometry) throws ShapefileMappingException {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry instanceof Point) {
            return ShapeType.POINT;
        }
        if (geometry instanceof MultiPoint) {
            return ShapeType.MULTIPOINT;
        }
        if (geometry instanceof LineString || geometry instanceof MultiLineString) {
            return ShapeType.POLYLINE;
        }
        if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
            return ShapeType.POLYGON;
        }
        throw new ShapefileMappingException("Geometry type '" + geometry.getGeometryType()
                + "' cannot be written to a Shapefile. Supported: Point, MultiPoint, LineString, MultiLineString, "
                + "Polygon, MultiPolygon.");
    }

    private void handleEndTransfer() throws IoxException {
        try {
            if (datasetWriter == null) {
                finalizeWithoutObjects();
            } else {
                datasetWriter.finish();
                writeSidecarIfNeeded();
            }
            state = State.FINISHED;
        } catch (IOException e) {
            throw new IoxException(
                    "SHP output '" + outputId + "': I/O error finalising Shapefile: " + e.getMessage(), e);
        }
    }

    private void finalizeWithoutObjects() throws IoxException, IOException {
        if (plan.configuredClassName().isPresent()
                && binding.typeSystem() != null
                && binding.typeSystem().classExists(plan.configuredClassName().get())) {
            resolvedClassName = plan.configuredClassName().get();
            schema = plan.buildSchema(
                    binding.typeSystem(), diagnostics, Optional.of(resolvedClassName), Optional.empty());
            mapper = new IomToDbfMapper(schema);
            openDatasetWriter();
            datasetWriter.finish();
            writeSidecarIfNeeded();
        } else if (diagnostics != null) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.IO_SHP_NO_OBJECTS,
                    Severity.WARNING,
                    "SHP output '" + outputId + "': no objects were written; no Shapefile was produced.",
                    null,
                    "Configure option 'class' to emit an empty Shapefile dataset."));
        }
    }

    private void writeSidecarIfNeeded() throws IOException {
        if (schema == null || !plan.writeSidecarMapping()) {
            return;
        }
        if (!DbfNameMapper.hasRenames(schema.nameMapping())) {
            return;
        }
        String fileName = targetShp.getFileName().toString();
        String base = fileName.substring(0, fileName.length() - ".shp".length());
        Path sidecar = targetShp.resolveSibling(base + ".iliattr.json");
        DbfNameMapper.writeSidecar(sidecar, schema.nameMapping());
        if (diagnostics != null) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.IO_SHP_SIDECAR_WRITTEN,
                    Severity.INFO,
                    "SHP output '" + outputId + "': wrote attribute-name mapping sidecar '" + sidecar.getFileName()
                            + "'.",
                    null,
                    null));
        }
    }

    @Override
    public void flush() throws IoxException {
        // No-op: a valid, final Shapefile exists only after EndTransferEvent/finish().
    }

    @Override
    public void close() throws IoxException {
        if (state == State.FINISHED) {
            return;
        }
        // Incomplete transfer: discard temporary files, leave no partial output.
        if (datasetWriter != null) {
            datasetWriter.close();
        }
        state = State.FINISHED;
    }

    @Override
    public void setFactory(IoxFactoryCollection factory) throws IoxException {
        this.factory = factory;
    }

    @Override
    public IoxFactoryCollection getFactory() throws IoxException {
        if (factory == null) {
            factory = new DefaultIoxFactoryCollection();
        }
        return factory;
    }

    @Override
    public IomObject createIomObject(String type, String oid) throws IoxException {
        return new Iom_jObject(type, oid);
    }
}
