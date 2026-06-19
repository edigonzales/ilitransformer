package guru.interlis.transformer.dmav.fullrun;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Dm01DmavFullRunSummary {

    public String datasetSlug;
    public String direction;
    public String sourceSha256;

    public Counts counts = new Counts();
    public List<String> includedTopics = new ArrayList<>();
    public List<ExcludedTopicSummary> excludedTopics = new ArrayList<>();
    public List<String> sourceTopicsWithoutProfile = new ArrayList<>();
    public List<String> sourceTopicsPresentInDataset = new ArrayList<>();

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

    public static final class ExcludedTopicSummary {
        public String id;
        public String reason;
    }
}
