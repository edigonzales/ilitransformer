package guru.interlis.transformer.io.shp.core;

import java.nio.ByteBuffer;

public record ShapeRecord(int recordNumber, ShapeType shapeType, ByteBuffer content, Bounds bounds) {}
