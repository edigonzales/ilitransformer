package guru.interlis.transformer.mapping.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        public Map<String, Object> options = new LinkedHashMap<>();

        /** Generic JDBC connection. Only used when {@code format} is {@code jdbc}. */
        @JsonProperty("connection")
        public JdbcConnectionSpec connection;

        /** One source class per query. Only used when {@code format} is {@code jdbc}. */
        @JsonProperty("queries")
        public List<JdbcQuerySpec> queries = new ArrayList<>();
    }

    // -- JDBC specs (Phase 6) ----------------------------------------------

    /**
     * Generic JDBC connection settings for a {@code jdbc} input. Credentials may be given inline
     * ({@code user}/{@code password}) or, preferably, indirected through environment variables
     * ({@code userEnv}/{@code passwordEnv}). Passwords must never be logged.
     */
    public static final class JdbcConnectionSpec {
        public String driver;
        public String url;
        public String user;
        public String password;
        public String userEnv;
        public String passwordEnv;
        public Map<String, String> properties = new LinkedHashMap<>();
    }

    /**
     * A single JDBC query mapped to one flat source class. Each query becomes one basket in the IOX
     * event stream produced by the reader.
     */
    public static final class JdbcQuerySpec {
        public String id;

        @JsonProperty("class")
        public String clazz;

        public String topic;
        public String basketId;
        public String oidColumn;
        public String sql;

        /** Optional column-label -&gt; attribute-name overrides. */
        public Map<String, String> columns = new LinkedHashMap<>();

        /** Optional spatial geometry column mappings (Phase 7). */
        public List<JdbcGeometrySpec> geometry = new ArrayList<>();
    }

    /**
     * Maps a JDBC result-set column containing encoded geometry (WKT or WKB) to a source model
     * geometry attribute (Phase 7).
     */
    public static final class JdbcGeometrySpec {
        /** The INTERLIS source attribute name this geometry maps to. */
        public String attribute;

        /** The result-set column label that holds the encoded geometry. */
        public String column;

        /** Geometry encoding: {@code wkt}, {@code wkb}, or {@code ewkb}. */
        public String encoding;

        /** Geometry type: {@code coord}, {@code polyline}, or {@code surface}. Auto-detect from model if absent. */
        public String type;

        /** SRID override (optional). */
        public Integer srid;
    }

    // -- OutputSpec --------------------------------------------------------

    public static final class OutputSpec {
        public String id;
        public String path;
        public String model;
        public String format;
        public Map<String, Object> options = new LinkedHashMap<>();
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

        public List<SourceSpec> sources = new ArrayList<>();

        @JsonProperty("assign")
        public Map<String, String> assign;

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
    }

    // -- TargetSpec --------------------------------------------------------

    public static final class TargetSpec {
        public String output;

        @JsonProperty("class")
        public String clazz;
    }

    // -- SourceSpec --------------------------------------------------------

    public static final class SourceSpec {

        public String alias;

        @JsonProperty("class")
        public String clazz;

        @JsonProperty("inputs")
        public List<String> inputs;

        @JsonProperty("where")
        public String where;
    }

    // -- RefMapping --------------------------------------------------------

    public static final class RefMapping {

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
        public String attribute;
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
