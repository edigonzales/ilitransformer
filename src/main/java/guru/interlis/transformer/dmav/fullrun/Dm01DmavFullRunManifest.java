package guru.interlis.transformer.dmav.fullrun;

import java.util.ArrayList;
import java.util.List;

public final class Dm01DmavFullRunManifest {

    public String datasetSlug;
    public String description;
    public String direction = "dm01-to-dmav";
    public String failPolicy = "strict";

    public SourceSpec source = new SourceSpec();
    public OutputSpec output = new OutputSpec();
    public MappingSpec mapping = new MappingSpec();
    public ReportSpec report = new ReportSpec();
    public TopicsSpec topics = new TopicsSpec();

    public List<String> modeldirs = new ArrayList<>();
    public List<String> sourceTopicsWithoutProfile = new ArrayList<>();
    public List<String> sourceTopicsPresentInDataset = new ArrayList<>();

    public static final class SourceSpec {
        public String pathHint;
        public String sha256;
        public String model;
        public String format;
        public String inputId = "dm01";
    }

    public static final class OutputSpec {
        public String outputId = "dmav";
        public String model;
        public String format;
        public String fileName;
    }

    public static final class MappingSpec {
        public String oidStrategy = "deterministicUuid";
        public String oidNamespace;
        public String basketStrategy = "byTopic";
        public String compileMode = "compatible";
    }

    public static final class ReportSpec {
        public String expectedSummary;
    }

    public static final class TopicsSpec {
        public List<TopicMappingSpec> include = new ArrayList<>();
        public List<ExcludedTopicSpec> exclude = new ArrayList<>();
    }

    public static final class TopicMappingSpec {
        public String id;
        public String mapping;
    }

    public static final class ExcludedTopicSpec {
        public String id;
        public String reason;
    }
}
