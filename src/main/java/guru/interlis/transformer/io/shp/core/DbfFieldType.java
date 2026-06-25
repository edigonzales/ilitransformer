package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

public enum DbfFieldType {
    CHARACTER('C'),
    NUMERIC('N'),
    FLOAT('F'),
    LOGICAL('L'),
    DATE('D');

    private final char code;

    DbfFieldType(char code) {
        this.code = code;
    }

    public char code() {
        return code;
    }

    public static DbfFieldType fromCode(char code) throws ShapefileMappingException {
        for (DbfFieldType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new ShapefileMappingException("Unsupported DBF field type '" + code
                + "'. Supported: C (Character), N (Numeric), F (Float), L (Logical), D (Date).");
    }
}
