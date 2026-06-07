package guru.interlis.transformer.mapping.model;

import java.util.ArrayList;
import java.util.List;

public final class JobConfig {
    public JobSection job = new JobSection();
    public MappingSection mapping = new MappingSection();

    public static final class JobSection {
        public List<InputSpec> inputs = new ArrayList<>();
        public List<OutputSpec> outputs = new ArrayList<>();
    }

    public static final class InputSpec {
        public String id;
        public String path;
        public String model;
    }

    public static final class OutputSpec {
        public String id;
        public String path;
        public String model;
    }

    public static final class MappingSection {
        public String oidStrategy = "integer";
        public String basketIdStrategy = "preserve";
        public List<RuleSpec> rules = new ArrayList<>();
    }

    public static final class RuleSpec {
        public String id;
        public String targetClass;
        public String output;
        public List<SourceSpec> sources = new ArrayList<>();
        public List<AttributeMapping> attributes = new ArrayList<>();
        public List<RefMapping> refs = new ArrayList<>();
    }

    public static final class SourceSpec {
        public String alias;
        public String input;
        public String clazz;
    }

    public static final class AttributeMapping {
        public String target;
        public String expr;
    }

    public static final class RefMapping {
        public String target;
        public String expr;
    }
}
