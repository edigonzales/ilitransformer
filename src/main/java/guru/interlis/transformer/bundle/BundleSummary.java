package guru.interlis.transformer.bundle;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BundleSummary {

    public String name;
    public String direction;
    public String sourceSha256;

    public Counts counts = new Counts();

    public Map<String, Long> targetsByClass = new LinkedHashMap<>();
    public Map<String, Integer> warningsByCode = new LinkedHashMap<>();
    public Map<String, Map<String, Integer>> warningsByRule = new LinkedHashMap<>();

    public static final class Counts {
        public int sourceRecordsRead;
        public int sourceRecordsFiltered;
        public int targetsCreated;
        public int targetsWritten;
        public int errors;
        public int warnings;
    }
}
