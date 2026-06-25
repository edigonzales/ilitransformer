package guru.interlis.transformer.io.shp.mapping;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.ShapefileOptions;
import guru.interlis.transformer.io.shp.core.DbfField;
import guru.interlis.transformer.io.shp.core.DbfRecord;

import ch.interlis.iom_j.Iom_jObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DbfToIomMapper {

    private final String className;
    private final Optional<String> oidField;
    private final int oidFieldIndex;
    private final Map<String, String> columnMappings;
    private final Map<String, Integer> fieldNameToIndex;
    private final ShapefileOptions.DeletedRecordPolicy deletedRecordPolicy;

    public DbfToIomMapper(
            String className,
            Optional<String> oidField,
            Map<String, String> columnMappings,
            List<DbfField> fields,
            ShapefileOptions.DeletedRecordPolicy deletedRecordPolicy) {

        this.className = className;
        this.oidField = oidField;
        this.columnMappings = columnMappings;
        this.deletedRecordPolicy = deletedRecordPolicy;

        LinkedHashMap<String, Integer> idxMap = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String fieldName = fields.get(i).name();
            if (!fieldName.isEmpty()) {
                idxMap.put(fieldName, i);
            }
        }
        this.fieldNameToIndex = idxMap;

        this.oidFieldIndex = oidField.map(oid -> {
                    for (int i = 0; i < fields.size(); i++) {
                        if (fields.get(i).name().equalsIgnoreCase(oid)) {
                            return i;
                        }
                    }
                    return -1;
                })
                .orElse(-1);
    }

    public Iom_jObject map(long recordNumber, DbfRecord dbfRecord) throws ShapefileMappingException {
        if (dbfRecord.deleted()) {
            if (deletedRecordPolicy == ShapefileOptions.DeletedRecordPolicy.ERROR) {
                throw new ShapefileMappingException("DBF record " + recordNumber
                        + " is marked as deleted. Set option deletedRecordPolicy=skip to ignore deleted records.");
            }
            return null;
        }

        String oid = resolveOid(recordNumber, dbfRecord);

        Iom_jObject object = new Iom_jObject(className, oid);

        List<String> values = dbfRecord.values();
        for (String dbfFieldName : fieldNameToIndex.keySet()) {
            int fieldIndex = fieldNameToIndex.get(dbfFieldName);
            if (fieldIndex >= values.size()) {
                continue;
            }
            String rawValue = values.get(fieldIndex);
            if (rawValue == null) {
                continue;
            }

            String iomAttrName = columnMappings.getOrDefault(dbfFieldName, dbfFieldName);

            String trimmed = rawValue.trim();
            if (!trimmed.isEmpty()) {
                object.setattrvalue(iomAttrName, trimmed);
            }
        }

        return object;
    }

    private String resolveOid(long recordNumber, DbfRecord dbfRecord) {
        if (oidFieldIndex >= 0 && oidFieldIndex < dbfRecord.values().size()) {
            String rawValue = dbfRecord.values().get(oidFieldIndex);
            if (rawValue != null) {
                String trimmed = rawValue.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "shp." + recordNumber;
    }

    public String className() {
        return className;
    }
}
