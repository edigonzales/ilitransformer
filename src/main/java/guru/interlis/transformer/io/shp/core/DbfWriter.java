package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Low-level writer for the {@code .dbf} attribute table (dBASE III).
 *
 * <p>The header is written first with a record count of {@code 0} and patched at the end via {@link
 * #patchRecordCount(int)}. Records are written one at a time; nothing is buffered.
 *
 * <p>Header geometry matches {@code DbfReader}: {@code headerLength = 32 + 32 * numFields + 1}
 * (including the {@code 0x0D} field terminator) and {@code recordLength = 1 + sum(fieldLengths)}
 * (including the leading deleted flag).
 *
 * <p>The {@link FileChannel} is owned by {@link ShapefileDatasetWriter}; {@link #close()} therefore
 * does not close the channel.
 */
public final class DbfWriter implements AutoCloseable {

    private static final int FIXED_HEADER_BYTES = 32;
    private static final int FIELD_DESCRIPTOR_BYTES = 32;
    private static final byte HEADER_TERMINATOR = 0x0D;
    private static final byte EOF_MARKER = 0x1A;
    private static final byte NOT_DELETED = 0x20;
    private static final byte DBASE3_VERSION = 0x03;
    private static final int MAX_FIELD_NAME_BYTES = 10;

    private static final DateTimeFormatter DBF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FileChannel channel;
    private final List<DbfField> fields;
    private final Charset charset;
    private final ShapefileWriteOptions.OverflowPolicy overflowPolicy;
    private final int headerLength;
    private final int recordLength;

    public DbfWriter(FileChannel channel, List<DbfField> fields, Charset charset) {
        this(channel, fields, charset, ShapefileWriteOptions.OverflowPolicy.STRICT);
    }

    public DbfWriter(
            FileChannel channel,
            List<DbfField> fields,
            Charset charset,
            ShapefileWriteOptions.OverflowPolicy overflowPolicy) {
        this.channel = channel;
        this.fields = List.copyOf(fields);
        this.charset = charset;
        this.overflowPolicy = overflowPolicy;

        int recLen = 1;
        for (DbfField field : this.fields) {
            recLen += field.length();
        }
        this.recordLength = recLen;
        this.headerLength = FIXED_HEADER_BYTES + this.fields.size() * FIELD_DESCRIPTOR_BYTES + 1;
    }

    public int headerLength() {
        return headerLength;
    }

    public int recordLength() {
        return recordLength;
    }

    /** Writes the full DBF header at the start of the file with the given record count. */
    public void writeHeader(int recordCount) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(headerLength);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.put(DBASE3_VERSION);
        LocalDate today = LocalDate.now();
        buf.put((byte) (today.getYear() - 1900));
        buf.put((byte) today.getMonthValue());
        buf.put((byte) today.getDayOfMonth());
        buf.putInt(recordCount);
        buf.putShort((short) headerLength);
        buf.putShort((short) recordLength);
        // bytes 12..31 reserved, already zero

        buf.position(FIXED_HEADER_BYTES);
        for (DbfField field : fields) {
            writeFieldDescriptor(buf, field);
        }
        buf.put(HEADER_TERMINATOR);

        buf.flip();
        channel.position(0);
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    private void writeFieldDescriptor(ByteBuffer buf, DbfField field) {
        byte[] nameBytes = field.name().getBytes(StandardCharsets.US_ASCII);
        int nameLen = Math.min(nameBytes.length, MAX_FIELD_NAME_BYTES);
        buf.put(nameBytes, 0, nameLen);
        for (int i = nameLen; i < 11; i++) {
            buf.put((byte) 0x00);
        }
        buf.put((byte) field.type().code());
        buf.putInt(0); // field data address, reserved
        buf.put((byte) field.length());
        buf.put((byte) field.decimalCount());
        for (int i = 0; i < 14; i++) {
            buf.put((byte) 0x00);
        }
    }

    /** Appends a single record at the current channel position. */
    public void writeRecord(Object[] values) throws IOException, ShapefileMappingException {
        if (values.length != fields.size()) {
            throw new ShapefileMappingException(
                    "DBF record has " + values.length + " values but the table defines " + fields.size() + " fields");
        }

        ByteBuffer buf = ByteBuffer.allocate(recordLength);
        buf.put(NOT_DELETED);
        for (int i = 0; i < fields.size(); i++) {
            byte[] fieldBytes = encodeField(fields.get(i), values[i]);
            buf.put(fieldBytes);
        }

        buf.flip();
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    /** Writes the trailing {@code 0x1A} end-of-file marker at the current channel position. */
    public void writeEndOfFile() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.put(EOF_MARKER);
        buf.flip();
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    /** Patches the 4-byte little-endian record count at offset 4 in the header. */
    public void patchRecordCount(int recordCount) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(recordCount);
        buf.flip();
        channel.position(4);
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    private byte[] encodeField(DbfField field, Object value) throws ShapefileMappingException {
        int length = field.length();
        String text =
                switch (field.type()) {
                    case CHARACTER -> value == null ? "" : value.toString();
                    case NUMERIC, FLOAT -> formatNumber(field, value);
                    case LOGICAL -> formatLogical(value);
                    case DATE -> formatDate(value);
                };

        Charset fieldCharset = field.type() == DbfFieldType.CHARACTER ? charset : StandardCharsets.US_ASCII;
        byte[] encoded = text.getBytes(fieldCharset);
        if (encoded.length > length) {
            if (field.type() == DbfFieldType.CHARACTER
                    && overflowPolicy == ShapefileWriteOptions.OverflowPolicy.TRUNCATE) {
                encoded = truncateToBytes(text, fieldCharset, length);
            } else {
                throw new ShapefileMappingException("DBF field '" + field.name() + "': value '" + text + "' encodes to "
                        + encoded.length + " bytes but the field width is " + length
                        + " (overflow policy is " + overflowPolicy.name().toLowerCase() + ")");
            }
        }

        byte[] out = new byte[length];
        boolean rightJustify = field.type() == DbfFieldType.NUMERIC || field.type() == DbfFieldType.FLOAT;
        int pad = length - encoded.length;
        if (rightJustify) {
            for (int i = 0; i < pad; i++) {
                out[i] = (byte) 0x20;
            }
            System.arraycopy(encoded, 0, out, pad, encoded.length);
        } else {
            System.arraycopy(encoded, 0, out, 0, encoded.length);
            for (int i = encoded.length; i < length; i++) {
                out[i] = (byte) 0x20;
            }
        }
        return out;
    }

    private static byte[] truncateToBytes(String text, Charset charset, int maxBytes) {
        StringBuilder out = new StringBuilder();
        int usedBytes = 0;
        int offset = 0;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            byte[] next = new String(Character.toChars(codePoint)).getBytes(charset);
            if (usedBytes + next.length > maxBytes) {
                break;
            }
            out.appendCodePoint(codePoint);
            usedBytes += next.length;
            offset += Character.charCount(codePoint);
        }
        return out.toString().getBytes(charset);
    }

    private String formatNumber(DbfField field, Object value) throws ShapefileMappingException {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s.trim();
        }
        if (value instanceof Number n) {
            try {
                BigDecimal bd = new BigDecimal(n.toString());
                bd = bd.setScale(field.decimalCount(), RoundingMode.HALF_UP);
                return bd.toPlainString();
            } catch (NumberFormatException e) {
                throw new ShapefileMappingException(
                        "DBF field '" + field.name() + "': cannot format numeric value '" + value + "'", e);
            }
        }
        throw new ShapefileMappingException("DBF field '" + field.name() + "': unsupported value type "
                + value.getClass().getName() + " for numeric field");
    }

    private String formatLogical(Object value) throws ShapefileMappingException {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean b) {
            return b ? "T" : "F";
        }
        String s = value.toString().trim().toLowerCase();
        return switch (s) {
            case "t", "true", "y", "yes", "1" -> "T";
            case "f", "false", "n", "no", "0" -> "F";
            case "" -> "";
            default ->
                throw new ShapefileMappingException(
                        "DBF logical field: cannot interpret value '" + value + "' as a boolean");
        };
    }

    private String formatDate(Object value) throws ShapefileMappingException {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate d) {
            return d.format(DBF_DATE);
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.length() == 8 && s.chars().allMatch(Character::isDigit)) {
            return s;
        }
        try {
            return LocalDate.parse(s).format(DBF_DATE);
        } catch (RuntimeException e) {
            throw new ShapefileMappingException(
                    "DBF date field: cannot interpret value '" + value + "' (expected yyyyMMdd or ISO date)", e);
        }
    }

    @Override
    public void close() {
        // The FileChannel is owned and closed by ShapefileDatasetWriter.
    }
}
