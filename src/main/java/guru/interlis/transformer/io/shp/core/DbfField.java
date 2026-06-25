package guru.interlis.transformer.io.shp.core;

public record DbfField(String name, DbfFieldType type, int length, int decimalCount) {}
