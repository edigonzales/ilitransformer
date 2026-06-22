package guru.interlis.transformer.mapping.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class JobConfig {

    public int version;

    @JsonProperty("job")
    public JobSection job = new JobSection();

    @JsonProperty("mapping")
    public MappingSection mapping = new MappingSection();

    // -- JobSection --------------------------------------------------------

    public static final class JobSection {
        public String name;
        public String description;
        public String direction;

        @JsonProperty("failPolicy")
        public String failPolicy = "strict";

        public List<String> modeldir;
        public List<InputSpec> inputs = new ArrayList<>();
        public List<OutputSpec> outputs = new ArrayList<>();
    }

    // -- InputSpec ---------------------------------------------------------

    public static final class InputSpec {
        public String id;
        public String path;
        public String model;
        public String format;
    }

    // -- OutputSpec --------------------------------------------------------

    public static final class OutputSpec {
        public String id;
        public String path;
        public String model;
        public String format;
    }

    // -- MappingSection ----------------------------------------------------

    public static final class MappingSection {
        @JsonProperty("oidStrategy")
        public OidStrategySpec oidStrategy = new OidStrategySpec();

        @JsonProperty("basketStrategy")
        public BasketStrategySpec basketStrategy = new BasketStrategySpec();

        public Map<String, Map<String, String>> enums;
        public Map<String, String> defaults;

        @JsonProperty("compileMode")
        public String compileMode = "strict";

        public List<RuleSpec> rules = new ArrayList<>();
    }

    // -- OidStrategySpec ---------------------------------------------------

    public static final class OidStrategySpec {
        @JsonProperty("default")
        public String defaultStrategy = "integer";

        public String namespace;
    }

    // -- BasketStrategySpec ------------------------------------------------

    public static final class BasketStrategySpec {
        @JsonProperty("default")
        public String defaultStrategy = "preserve";
    }

    // -- RuleSpec ----------------------------------------------------------

    public static final class RuleSpec {

        public String id;

        @JsonProperty("target")
        public TargetSpec target;

        @JsonAlias("targetClass")
        public String targetClass; // backward compat

        @JsonAlias("output")
        public String output; // backward compat

        public List<SourceSpec> sources = new ArrayList<>();

        @JsonProperty("assign")
        public Map<String, String> assign;

        @JsonAlias("attributes")
        public List<AttributeMapping> attributes; // backward compat

        @JsonProperty("where")
        public String where;

        @JsonProperty("identity")
        public IdentitySpec identity;

        @JsonProperty("refs")
        public List<RefMapping> refs;

        @JsonProperty("bags")
        public Map<String, BagSpec> bags;

        @JsonProperty("losses")
        public List<LossSpec> losses;

        @JsonProperty("create")
        public List<CreateSpec> create;

        @JsonProperty("joins")
        public List<JoinSpec> joins;

        @JsonProperty("metadata")
        public MetadataSpec metadata;

        @JsonProperty("defaults")
        public Map<String, String> defaults;

        /** @deprecated Use {@link JobConfigNormalizer#getEffectiveTargetClass(RuleSpec)} instead. */
        @Deprecated
        @JsonIgnore
        public String getEffectiveTargetClass() {
            return JobConfigNormalizer.getEffectiveTargetClass(this);
        }

        /** @deprecated Use {@link JobConfigNormalizer#getEffectiveTargetOutput(RuleSpec)} instead. */
        @Deprecated
        @JsonIgnore
        public String getEffectiveTargetOutput() {
            return JobConfigNormalizer.getEffectiveTargetOutput(this);
        }

        /** @deprecated Use {@link JobConfigNormalizer#getAllAttributes(RuleSpec)} instead. */
        @Deprecated
        @JsonIgnore
        public List<AttributeMapping> getAllAttributes() {
            return JobConfigNormalizer.getAllAttributes(this);
        }

        /** @deprecated Use {@link JobConfigNormalizer#getEffectiveRefs(RuleSpec)} instead. */
        @Deprecated
        @JsonIgnore
        public List<RefMapping> getEffectiveRefs() {
            return JobConfigNormalizer.getEffectiveRefs(this);
        }
    }

    // -- TargetSpec --------------------------------------------------------

    public static final class TargetSpec {
        public String output;

        @JsonProperty("class")
        @JsonAlias("clazz")
        public String clazz;
    }

    // -- SourceSpec --------------------------------------------------------

    public static final class SourceSpec {

        public String alias;

        @JsonProperty("class")
        @JsonAlias("clazz")
        public String clazz;

        @JsonProperty("inputs")
        public List<String> inputs;

        @JsonAlias("input")
        public String input; // backward compat

        @JsonProperty("where")
        public String where;

        /** @deprecated Use {@link JobConfigNormalizer#getInputIds(SourceSpec)} instead. */
        @Deprecated
        @JsonIgnore
        public List<String> getInputIds() {
            return JobConfigNormalizer.getInputIds(this);
        }
    }

    // -- AttributeMapping --------------------------------------------------

    public static final class AttributeMapping {
        public String target;
        public String expr;
    }

    // -- RefMapping --------------------------------------------------------

    public static final class RefMapping {
        public String target; // backward compat (target attribute name)
        public String expr; // backward compat (expression)

        @JsonProperty("association")
        public String association;

        @JsonProperty("role")
        public String role;

        @JsonProperty("sourceRef")
        public String sourceRef;

        @JsonProperty("targetRule")
        public String targetRule;

        @JsonProperty("required")
        public boolean required;

        @JsonProperty("targetObject")
        public RefTargetObject targetObject;

        public static final class RefTargetObject {
            public String rule;
            public String sourceRef;
        }
    }

    // -- IdentitySpec ------------------------------------------------------

    public static final class IdentitySpec {
        public List<String> sourceKey;
    }

    // -- BagSpec -----------------------------------------------------------

    public static final class BagSpec {
        @JsonProperty("target")
        public String target;

        @JsonProperty("from")
        public BagFrom from;

        public String structure;
        public Map<String, String> assign;
        public String mode;
        public Integer maxItems;
        public String where;

        @JsonProperty("parentRef")
        public BagParentRef parentRef;

        @JsonProperty("nestedBags")
        public Map<String, BagSpec> nestedBags;
    }

    public static final class LossSpec {
        public String sourcePath;
        public String reasonCode;
        public String description;
        public String when;
    }

    public static final class BagParentRef {
        public String attribute;
        public String parentAlias;
        public String association;
        public String role;
    }

    public static final class BagFrom {
        public String input;

        @JsonProperty("class")
        public String clazz;

        public String alias;
        public String where;
    }

    // -- CreateSpec / JoinSpec / MetadataSpec -------------------------------

    public static final class CreateSpec {
        @JsonProperty("class")
        public String clazz;

        public Map<String, String> assign;
    }

    public static final class JoinSpec {
        public String left;
        public String right;
        public String on;
        public String type; // "inner", "left"
    }

    public static final class MetadataSpec {
        public String direction;
        public String roundtrip;
        public String lossiness;
    }
}
