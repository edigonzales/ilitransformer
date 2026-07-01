package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.geom.ShpGeometryEncoder;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import com.vividsolutions.jts.geom.Geometry;

/*
 * Performance rule:
 * Do not introduce a FeatureCollection-like buffering layer here.
 * The former iox-wkf Shapefile writer was slow because each feature was
 * wrapped in a ListFeatureCollection and written through SimpleFeatureStore.
 * PR claeis/iox-wkf#32 switched to FeatureWriter and reduced 10k feature
 * export from roughly minutes to seconds. This implementation follows the
 * same low-level principle directly: one pass, open channels, reusable
 * buffers, header patching at finish().
 */

/**
 * Streaming, low-level writer for a complete Shapefile dataset ({@code .shp}, {@code .shx}, {@code
 * .dbf}, plus an optional {@code .cpg} and {@code .prj}).
 *
 * <p>The dataset is written to temporary {@code *.tmp} files and only committed to the final names
 * in {@link #finish()}. {@link #close()} without a successful {@code finish()} deletes the temporary
 * files and leaves no partial output.
 *
 * <p>This class knows nothing about INTERLIS or IOX. It accepts plain JTS geometries and DBF
 * attribute value arrays; the translation from IOX objects is the responsibility of the writer
 * adapter built in a later phase.
 */
public final class ShapefileDatasetWriter implements AutoCloseable {

    private final Path shpTarget;
    private final Path shxTarget;
    private final Path dbfTarget;
    private final Path cpgTarget;
    private final Path prjTarget;

    private final Path shpTmp;
    private final Path shxTmp;
    private final Path dbfTmp;

    private final ShapeType shapeType;
    private final ShapefileWriteOptions options;

    private final FileChannel shpChannel;
    private final FileChannel shxChannel;
    private final FileChannel dbfChannel;
    private final ShpWriter shpWriter;
    private final ShxWriter shxWriter;
    private final DbfWriter dbfWriter;
    private final ShpGeometryEncoder encoder = new ShpGeometryEncoder();
    private final BoundsAccumulator bounds = new BoundsAccumulator();

    private long shpLengthBytes = ShapefileHeader.HEADER_SIZE;
    private long shxLengthBytes = ShapefileHeader.HEADER_SIZE;
    private int recordNumber = 0;
    private boolean finished = false;
    private boolean closed = false;

    private ShapefileDatasetWriter(
            Path shpTarget,
            ShapeType shapeType,
            ShapefileWriteOptions options,
            FileChannel shpChannel,
            FileChannel shxChannel,
            FileChannel dbfChannel,
            DbfWriter dbfWriter) {
        this.shpTarget = shpTarget;
        String base = baseName(shpTarget);
        this.shxTarget = shpTarget.resolveSibling(base + ".shx");
        this.dbfTarget = shpTarget.resolveSibling(base + ".dbf");
        this.cpgTarget = shpTarget.resolveSibling(base + ".cpg");
        this.prjTarget = shpTarget.resolveSibling(base + ".prj");
        this.shpTmp = tmp(shpTarget);
        this.shxTmp = tmp(this.shxTarget);
        this.dbfTmp = tmp(this.dbfTarget);
        this.shapeType = shapeType;
        this.options = options;
        this.shpChannel = shpChannel;
        this.shxChannel = shxChannel;
        this.dbfChannel = dbfChannel;
        this.shpWriter = new ShpWriter(shpChannel, shapeType);
        this.shxWriter = new ShxWriter(shxChannel);
        this.dbfWriter = dbfWriter;
    }

    public static ShapefileDatasetWriter open(Path targetShp, ShapefileSchema schema, ShapefileWriteOptions options)
            throws IOException, ShapefileMappingException {
        schema.validateWritable();
        String fileName = targetShp.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".shp")) {
            throw new ShapefileMappingException(
                    "Shapefile target path must end with '.shp' but was '" + targetShp + "'");
        }

        Path parent = targetShp.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String base = baseName(targetShp);
        Path shpTmp = tmp(targetShp);
        Path shxTmp = tmp(targetShp.resolveSibling(base + ".shx"));
        Path dbfTmp = tmp(targetShp.resolveSibling(base + ".dbf"));

        FileChannel shpChannel = null;
        FileChannel shxChannel = null;
        FileChannel dbfChannel = null;
        try {
            shpChannel = openTruncating(shpTmp);
            shxChannel = openTruncating(shxTmp);
            dbfChannel = openTruncating(dbfTmp);

            DbfWriter dbfWriter = new DbfWriter(dbfChannel, schema.fields(), options.dbfCharset(), options.overflow());

            ShapefileDatasetWriter writer = new ShapefileDatasetWriter(
                    targetShp, schema.shapeType(), options, shpChannel, shxChannel, dbfChannel, dbfWriter);

            writer.shpWriter.writeHeader(writer.placeholderHeader());
            writer.shxWriter.writeHeader(writer.placeholderHeader());
            writer.dbfWriter.writeHeader(0);
            return writer;
        } catch (Exception e) {
            closeQuietly(shpChannel);
            closeQuietly(shxChannel);
            closeQuietly(dbfChannel);
            deleteQuietly(shpTmp);
            deleteQuietly(shxTmp);
            deleteQuietly(dbfTmp);
            throw e;
        }
    }

    public void write(Geometry geometry, Object[] dbfValues) throws IOException, ShapefileMappingException {
        if (finished || closed) {
            throw new ShapefileMappingException("Cannot write to a Shapefile dataset writer that is already finished");
        }

        recordNumber++;
        EncodedShape encoded = encoder.encode(geometry);

        long shpOffsetWords = shpLengthBytes / 2;
        int contentLengthWords = encoded.contentLengthBytes() / 2;

        shpWriter.writeRecord(recordNumber, encoded);
        shxWriter.writeIndexEntry(shpOffsetWords, contentLengthWords);
        dbfWriter.writeRecord(dbfValues);
        bounds.expand(encoded.bounds());

        shpLengthBytes += 8L + encoded.contentLengthBytes();
        shxLengthBytes += 8L;

        if (shpLengthBytes / 2 > Integer.MAX_VALUE) {
            throw new ShapefileMappingException(
                    "Shapefile exceeds the maximum file size of " + Integer.MAX_VALUE + " 16-bit words");
        }
    }

    public void finish() throws IOException, ShapefileMappingException {
        if (finished) {
            return;
        }
        Bounds b = bounds.toBounds();

        shpWriter.writeHeader(finalHeader((int) (shpLengthBytes / 2), b));
        shxWriter.writeHeader(finalHeader((int) (shxLengthBytes / 2), b));
        dbfWriter.writeEndOfFile();
        dbfWriter.patchRecordCount(recordNumber);

        shpChannel.close();
        shxChannel.close();
        dbfChannel.close();

        moveAtomically(shpTmp, shpTarget);
        moveAtomically(shxTmp, shxTarget);
        moveAtomically(dbfTmp, dbfTarget);

        Files.writeString(cpgTarget, options.dbfCharset().name(), StandardCharsets.US_ASCII);
        if (options.prjWkt().isPresent()) {
            Files.writeString(prjTarget, options.prjWkt().get(), StandardCharsets.UTF_8);
        }

        finished = true;
        closed = true;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeQuietly(shpChannel);
        closeQuietly(shxChannel);
        closeQuietly(dbfChannel);
        deleteQuietly(shpTmp);
        deleteQuietly(shxTmp);
        deleteQuietly(dbfTmp);
    }

    public Path shpPath() {
        return shpTarget;
    }

    public Path shxPath() {
        return shxTarget;
    }

    public Path dbfPath() {
        return dbfTarget;
    }

    public Path cpgPath() {
        return cpgTarget;
    }

    public Path prjPath() {
        return prjTarget;
    }

    public int recordCount() {
        return recordNumber;
    }

    private ShapefileHeader placeholderHeader() {
        return new ShapefileHeader(
                9994, ShapefileHeader.HEADER_SIZE / 2, 1000, shapeType, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private ShapefileHeader finalHeader(int fileLengthWords, Bounds b) {
        return new ShapefileHeader(
                9994, fileLengthWords, 1000, shapeType, b.xmin(), b.ymin(), b.xmax(), b.ymax(), 0.0, 0.0, 0.0, 0.0);
    }

    private static FileChannel openTruncating(Path path) throws IOException {
        return FileChannel.open(
                path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String baseName(Path shp) {
        String fileName = shp.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".shp".length());
    }

    private static Path tmp(Path path) {
        return path.resolveSibling(path.getFileName() + ".tmp");
    }

    private static void moveAtomically(Path src, Path dst) throws IOException {
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
