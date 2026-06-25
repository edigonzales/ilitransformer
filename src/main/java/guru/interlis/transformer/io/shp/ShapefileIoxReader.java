package guru.interlis.transformer.io.shp;

import guru.interlis.transformer.io.FormatOpenContext;
import guru.interlis.transformer.io.FormatOptions;
import guru.interlis.transformer.io.shp.core.DbfReader;
import guru.interlis.transformer.io.shp.core.DbfRecord;
import guru.interlis.transformer.io.shp.core.ShapeRecord;
import guru.interlis.transformer.io.shp.core.ShapeType;
import guru.interlis.transformer.io.shp.core.ShapefileDataset;
import guru.interlis.transformer.io.shp.core.ShpReader;
import guru.interlis.transformer.io.shp.geom.ShpGeometryDecoder;
import guru.interlis.transformer.io.shp.mapping.DbfToIomMapper;
import guru.interlis.transformer.mapping.plan.InputBinding;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.DefaultIoxFactoryCollection;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;
import ch.interlis.iox_j.jts.Jts2iox;

import java.io.IOException;
import java.util.Optional;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public final class ShapefileIoxReader implements IoxReader {

    private enum State {
        START,
        BEFORE_BASKET,
        INSIDE_BASKET,
        DONE
    }

    private final String inputId;
    private final ShpReader shpReader;
    private final DbfReader dbfReader;
    private final DbfToIomMapper mapper;
    private final ShapefileReadPlan plan;
    private final ShpGeometryDecoder geometryDecoder;

    private State state = State.START;
    private long currentRecordNumber = 0;
    private IoxFactoryCollection factory;

    private ShapefileIoxReader(
            String inputId, ShpReader shpReader, DbfReader dbfReader, DbfToIomMapper mapper, ShapefileReadPlan plan) {
        this.inputId = inputId;
        this.shpReader = shpReader;
        this.dbfReader = dbfReader;
        this.mapper = mapper;
        this.plan = plan;
        this.geometryDecoder = new ShpGeometryDecoder();
    }

    public static ShapefileIoxReader open(InputBinding binding, FormatOpenContext context)
            throws IOException, ShapefileMappingException {

        String inputId = binding.inputId() != null ? binding.inputId() : "?";
        FormatOptions options = FormatOptions.of(binding.options());
        ShapefileOptions shapeOpts = ShapefileOptions.input(options);

        ShapefileDataset dataset = ShapefileDataset.fromPath(binding.path(), shapeOpts.requireShx());

        ShpReader shpReader = ShpReader.open(dataset.shp());
        DbfReader dbfReader = DbfReader.open(dataset.dbf(), shapeOpts.dbfCharset());

        try {
            ShapefileReadPlan plan = ShapefileReadPlan.create(
                    shapeOpts, binding.typeSystem(), shpReader.header().shapeType(), inputId);

            DbfToIomMapper mapper = new DbfToIomMapper(
                    plan.className(),
                    plan.oidField(),
                    plan.fieldMapping(),
                    dbfReader.fields(),
                    plan.deletedRecordPolicy());

            return new ShapefileIoxReader(inputId, shpReader, dbfReader, mapper, plan);

        } catch (Exception e) {
            closeQuietly(shpReader);
            closeQuietly(dbfReader);
            throw e;
        }
    }

    @Override
    public IoxEvent read() throws IoxException {
        switch (state) {
            case START:
                state = State.BEFORE_BASKET;
                return new StartTransferEvent();
            case BEFORE_BASKET:
                state = State.INSIDE_BASKET;
                return new StartBasketEvent(plan.topicName(), plan.basketId());
            case INSIDE_BASKET:
                return readFeatureOrEndBasket();
            case DONE:
            default:
                return null;
        }
    }

    private IoxEvent readFeatureOrEndBasket() throws IoxException {
        try {
            Optional<ShapeRecord> shpRecord = shpReader.readNext();
            Optional<DbfRecord> dbfRec = dbfReader.readNext();

            if (shpRecord.isPresent() && dbfRec.isEmpty()) {
                long recordNum = currentRecordNumber + 1;
                throw new ShapefileMappingException("SHP input '" + inputId + "': SHP has record " + recordNum
                        + " but DBF has fewer records. SHP and DBF must have the same number of records.");
            }
            if (shpRecord.isEmpty() && dbfRec.isPresent()) {
                long recordNum = currentRecordNumber + 1;
                throw new ShapefileMappingException("SHP input '" + inputId + "': DBF has record " + recordNum
                        + " but SHP has fewer records. SHP and DBF must have the same number of records.");
            }

            if (shpRecord.isEmpty()) {
                state = State.DONE;
                return new EndTransferEvent();
            }

            currentRecordNumber++;

            IomObject obj = mapCurrentRecord(currentRecordNumber, shpRecord.get(), dbfRec.get());
            if (obj == null) {
                return readFeatureOrEndBasket();
            }
            return new ObjectEvent(obj);

        } catch (IOException e) {
            throw new IoxException(
                    "SHP input '" + inputId + "': I/O error reading record " + (currentRecordNumber + 1) + ": "
                            + e.getMessage(),
                    e);
        }
    }

    private IomObject mapCurrentRecord(long recordNumber, ShapeRecord shape, DbfRecord dbf)
            throws ShapefileMappingException {

        Iom_jObject object = mapper.map(recordNumber, dbf);
        if (object == null) {
            return null;
        }

        ShapeType shapeType = shape.shapeType();
        if (shapeType == ShapeType.NULL) {
            return object;
        }

        Geometry jtsGeometry = geometryDecoder.decode(shape);
        if (jtsGeometry == null) {
            return object;
        }

        IomObject ioxGeom = toIoxGeometry(jtsGeometry, recordNumber);
        object.addattrobj(plan.geometry().attributeName(), ioxGeom);

        return object;
    }

    private IomObject toIoxGeometry(Geometry jtsGeometry, long recordNumber) throws ShapefileMappingException {
        if (jtsGeometry instanceof Point point) {
            return Jts2iox.JTS2coord(point.getCoordinate());
        }
        if (jtsGeometry instanceof LineString ls) {
            return Jts2iox.JTS2polyline(ls);
        }
        if (jtsGeometry instanceof MultiLineString mls) {
            return Jts2iox.JTS2multipolyline(mls);
        }
        if (jtsGeometry instanceof Polygon polygon) {
            return Jts2iox.JTS2surface(polygon);
        }
        if (jtsGeometry instanceof MultiPolygon mp) {
            return Jts2iox.JTS2multisurface(mp);
        }
        throw new ShapefileMappingException("SHP input '" + inputId + "', record " + recordNumber
                + ": unsupported geometry type '" + jtsGeometry.getGeometryType()
                + "'. Supported: Point, LineString, MultiLineString, Polygon, MultiPolygon.");
    }

    @Override
    public void close() throws IoxException {
        closeQuietly(shpReader);
        closeQuietly(dbfReader);
        state = State.DONE;
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

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
