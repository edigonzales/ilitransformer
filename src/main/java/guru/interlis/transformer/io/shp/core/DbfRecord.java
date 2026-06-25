package guru.interlis.transformer.io.shp.core;

import java.util.List;

public record DbfRecord(boolean deleted, List<String> values) {}
