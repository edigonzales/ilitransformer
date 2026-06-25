package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DbfReader implements AutoCloseable {

    private final FileChannel channel;
    private final DbfHeader header;
    private final Charset charset;
    private long currentRecord;

    private DbfReader(FileChannel channel, DbfHeader header, Charset charset) {
        this.channel = channel;
        this.header = header;
        this.charset = charset;
        this.currentRecord = 0;
    }

    public static DbfReader open(Path dbfPath, Charset charset) throws IOException, ShapefileMappingException {
        FileChannel channel = FileChannel.open(dbfPath, StandardOpenOption.READ);
        try {
            DbfHeader header = readHeader(channel);
            return new DbfReader(channel, header, charset);
        } catch (Exception e) {
            channel.close();
            throw e;
        }
    }

    public DbfHeader header() {
        return header;
    }

    public List<DbfField> fields() {
        return header.fields();
    }

    public Optional<DbfRecord> readNext() throws IOException {
        if (currentRecord >= header.recordCount()) {
            return Optional.empty();
        }

        ByteBuffer recordBuf = ByteBuffer.allocate(header.recordLength());
        readFully(channel, recordBuf);
        recordBuf.flip();

        byte flag = recordBuf.get();
        boolean deleted = (flag == (byte) 0x2A);

        List<String> values = new ArrayList<>(header.fields().size());
        for (DbfField field : header.fields()) {
            byte[] fieldBytes = new byte[field.length()];
            recordBuf.get(fieldBytes);
            String value = new String(fieldBytes, charset);
            values.add(value);
        }

        currentRecord++;
        return Optional.of(new DbfRecord(deleted, values));
    }

    public long currentRecordNumber() {
        return currentRecord;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private static DbfHeader readHeader(FileChannel channel) throws IOException, ShapefileMappingException {
        ByteBuffer fixedBuf = ByteBuffer.allocate(32);
        readFully(channel, fixedBuf);
        fixedBuf.flip();

        byte version = fixedBuf.get();
        int year = Byte.toUnsignedInt(fixedBuf.get());
        int month = Byte.toUnsignedInt(fixedBuf.get());
        int day = Byte.toUnsignedInt(fixedBuf.get());

        fixedBuf.order(ByteOrder.LITTLE_ENDIAN);
        int recordCount = fixedBuf.getInt();
        int headerLength = fixedBuf.getShort() & 0xFFFF;
        int recordLength = fixedBuf.getShort() & 0xFFFF;

        if (headerLength < 33) {
            throw new IOException("DBF header length too small: " + headerLength);
        }

        int fieldDescriptorBytes = headerLength - 32;
        int numFields = (fieldDescriptorBytes - 1) / 32;

        ByteBuffer fieldBuf = ByteBuffer.allocate(fieldDescriptorBytes);
        readFully(channel, fieldBuf);
        fieldBuf.flip();

        List<DbfField> fields = new ArrayList<>(numFields);
        for (int i = 0; i < numFields; i++) {
            byte[] nameBytes = new byte[11];
            fieldBuf.get(nameBytes);
            String name = new String(nameBytes, StandardCharsets.US_ASCII)
                    .replace("\0", "")
                    .trim();

            char typeCode = (char) fieldBuf.get();
            DbfFieldType type = DbfFieldType.fromCode(typeCode);

            fieldBuf.position(fieldBuf.position() + 4);

            int length = Byte.toUnsignedInt(fieldBuf.get());
            int decimalCount = Byte.toUnsignedInt(fieldBuf.get());

            fieldBuf.position(fieldBuf.position() + 14);

            fields.add(new DbfField(name, type, length, decimalCount));
        }

        return new DbfHeader(version, year, month, day, recordCount, headerLength, recordLength, fields);
    }

    static void readFully(FileChannel channel, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int n = channel.read(buf);
            if (n < 0) {
                throw new IOException("Unexpected end of DBF file at byte " + channel.position());
            }
        }
    }
}
