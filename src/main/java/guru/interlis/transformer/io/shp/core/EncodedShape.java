package guru.interlis.transformer.io.shp.core;

import java.nio.ByteBuffer;

/**
 * A single Shapefile record content, already serialized into little-endian bytes.
 *
 * <p>The {@link #content()} buffer is positioned at {@code 0} with its limit set to
 * {@link #contentLengthBytes()} and is ready to be written to a {@code .shp} channel. It already
 * contains the leading little-endian shape type integer, exactly like the content returned by the
 * reader, so that {@code ShpGeometryDecoder} can read it back unchanged.
 *
 * <p>{@link #bounds()} is {@code null} for {@code NULL} shapes, because they do not contribute to
 * the dataset bounding box.
 */
public record EncodedShape(ByteBuffer content, int contentLengthBytes, Bounds bounds) {}
