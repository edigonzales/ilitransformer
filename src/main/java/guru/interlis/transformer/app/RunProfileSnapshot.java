package guru.interlis.transformer.app;

public record RunProfileSnapshot(long compilePrepareMs, long validationMs, long reportWriteMs, long totalRunMs) {

    public RunProfileSnapshot withReportWriteMs(long reportWriteMs) {
        return new RunProfileSnapshot(compilePrepareMs, validationMs, reportWriteMs, totalRunMs + reportWriteMs);
    }
}
