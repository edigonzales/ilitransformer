package guru.interlis.transformer.io.shp.core;

import java.util.List;

public record DbfHeader(
        byte version,
        int lastUpdateYear,
        int lastUpdateMonth,
        int lastUpdateDay,
        int recordCount,
        int headerLength,
        int recordLength,
        List<DbfField> fields) {}
