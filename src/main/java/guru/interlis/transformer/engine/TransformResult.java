package guru.interlis.transformer.engine;

public record TransformResult(
        long sourceRecordsRead,
        long sourceRecordsFiltered,
        long targetsCreated,
        long targetsWritten,
        long errors,
        long warnings,
        String oidStrategy,
        String basketStrategy) {
    public String summary() {
        return String.format(
                "Transform summary: %d source records read, %d filtered, %d targets created, %d written (%d errors, %d warnings) [OID: %s, Basket: %s]",
                sourceRecordsRead,
                sourceRecordsFiltered,
                targetsCreated,
                targetsWritten,
                errors,
                warnings,
                oidStrategy,
                basketStrategy);
    }
}
