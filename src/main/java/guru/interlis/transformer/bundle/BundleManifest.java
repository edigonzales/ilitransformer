package guru.interlis.transformer.bundle;

import java.util.ArrayList;
import java.util.List;

public final class BundleManifest {

    public String name;
    public String description;
    public String direction;
    public String failPolicy = "strict";

    public SourceSpec source = new SourceSpec();
    public OutputSpec output = new OutputSpec();
    public MappingSpec mapping = new MappingSpec();

    public List<String> modeldirs = new ArrayList<>();
    public List<MappingModule> modules = new ArrayList<>();

    public String expectedSummary;
    public boolean validate = true;

    public static final class SourceSpec {
        public String id = "input";
        public String pathHint;
        public String sha256;
        public String model;
        public String format;
    }

    public static final class OutputSpec {
        public String id = "output";
        public String model;
        public String format;
        public String fileName;
    }

    public static final class MappingSpec {
        public String oidStrategy = "deterministicUuid";
        public String oidNamespace;
        public String basketStrategy = "preserve";
        public String compileMode = "strict";
    }

    public static final class MappingModule {
        public String id;
        public String mapping;
    }
}
