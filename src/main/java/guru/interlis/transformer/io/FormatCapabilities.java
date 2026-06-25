package guru.interlis.transformer.io;

public record FormatCapabilities(
        boolean canRead, boolean canWrite, boolean requiresPath, boolean requiresModel, boolean supportsOptions) {

    public static FormatCapabilities readWritePathModel() {
        return new FormatCapabilities(true, true, true, true, false);
    }

    public static FormatCapabilities readPathModelWithOptions() {
        return new FormatCapabilities(true, false, true, true, true);
    }

    /** Read-only source that has no file path (such as JDBC) but requires a model and uses options. */
    public static FormatCapabilities readConnectionModelWithOptions() {
        return new FormatCapabilities(true, false, false, true, true);
    }

    /** Read/write file format that requires a model and uses options (prepared for the Shapefile writer). */
    public static FormatCapabilities readWritePathModelWithOptions() {
        return new FormatCapabilities(true, true, true, true, true);
    }
}
