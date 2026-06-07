package guru.interlis.transformer.diag;

public final class DiagnosticCode {
    private DiagnosticCode() {}

    // Mapping/Compiler validation
    public static final String MAP_VERSION = "ILITRF-MAP-VERSION";
    public static final String MAP_MISSING_ID = "ILITRF-MAP-MISSING-ID";
    public static final String MAP_DUPLICATE_ID = "ILITRF-MAP-DUPLICATE-ID";
    public static final String MAP_MISSING_TARGET_CLASS = "ILITRF-MAP-MISSING-TARGET-CLASS";
    public static final String MAP_UNKNOWN_OUTPUT = "ILITRF-MAP-UNKNOWN-OUTPUT";
    public static final String MAP_MISSING_SOURCE_CLASS = "ILITRF-MAP-MISSING-SOURCE-CLASS";
    public static final String MAP_MISSING_ALIAS = "ILITRF-MAP-MISSING-ALIAS";
    public static final String MAP_DUPLICATE_ALIAS = "ILITRF-MAP-DUPLICATE-ALIAS";
    public static final String MAP_UNKNOWN_INPUT = "ILITRF-MAP-UNKNOWN-INPUT";
    public static final String MAP_MISSING_INPUT = "ILITRF-MAP-MISSING-INPUT";

    // Typed compiler (Phase 3) – model-aware validation
    public static final String MAP_UNKNOWN_TARGET_CLASS = "ILITRF-MAP-UNKNOWN-TARGET-CLASS";
    public static final String MAP_UNKNOWN_SOURCE_CLASS = "ILITRF-MAP-UNKNOWN-SOURCE-CLASS";
    public static final String MAP_ABSTRACT_TARGET_CLASS = "ILITRF-MAP-ABSTRACT-TARGET-CLASS";
    public static final String MAP_UNKNOWN_TARGET_ATTRIBUTE = "ILITRF-MAP-UNKNOWN-TARGET-ATTRIBUTE";
    public static final String MAP_UNKNOWN_SOURCE_ATTRIBUTE = "ILITRF-MAP-UNKNOWN-SOURCE-ATTRIBUTE";
    public static final String MAP_UNKNOWN_ROLE = "ILITRF-MAP-UNKNOWN-ROLE";
    public static final String MAP_TYPE_MISMATCH = "ILITRF-MAP-TYPE-MISMATCH";
    public static final String MAP_MANDATORY_MISSING = "ILITRF-MAP-MANDATORY-MISSING";
    public static final String MAP_DUPLICATE_TARGET_ASSIGN = "ILITRF-MAP-DUPLICATE-TARGET-ASSIGN";
    public static final String MAP_CYCLIC_DEPENDENCY = "ILITRF-MAP-CYCLIC-DEPENDENCY";
    public static final String MAP_NON_TRANSFERABLE_TARGET = "ILITRF-MAP-NON-TRANSFERABLE-TARGET";

    // Phase 6 – OID / Basket strategy validation
    public static final String MAP_UNKNOWN_OID_STRATEGY = "ILITRF-MAP-UNKNOWN-OID-STRATEGY";
    public static final String MAP_UNKNOWN_BASKET_STRATEGY = "ILITRF-MAP-UNKNOWN-BASKET-STRATEGY";
    public static final String MAP_OID_TYPE_MISMATCH = "ILITRF-MAP-OID-TYPE-MISMATCH";
    public static final String MAP_IDENTITY_KEY_MISSING = "ILITRF-MAP-IDENTITY-KEY-MISSING";

    // Model
    public static final String MODEL_COMPILE_FAILED = "ILITRF-MODEL-COMPILE-FAILED";

    // Runtime
    public static final String RUN_REF_UNRESOLVED = "ILITRF-RUN-REF-UNRESOLVED";
    public static final String RUN_REF_AMBIGUOUS = "ILITRF-RUN-REF-AMBIGUOUS";
    public static final String RUN_REF_TYPE_MISMATCH = "ILITRF-RUN-REF-TYPE-MISMATCH";
    public static final String RUN_REF_MISSING_MANDATORY = "ILITRF-RUN-REF-MISSING-MANDATORY";
    public static final String RUN_REF_CARDINALITY = "ILITRF-RUN-REF-CARDINALITY";

    // Expression (Phase 4)
    public static final String EXPR_SYNTAX = "ILITRF-EXPR-SYNTAX";
    public static final String EXPR_UNKNOWN_FUNC = "ILITRF-EXPR-UNKNOWN-FUNC";
    public static final String EXPR_TYPE = "ILITRF-EXPR-TYPE";
    public static final String EXPR_NON_DETERMINISTIC = "ILITRF-EXPR-NON-DETERMINISTIC";
    public static final String EXPR_UNSUPPORTED = "ILITRF-EXPR-UNSUPPORTED";

    // DM01/DMAV
    public static final String DMAV_CORRELATION_PARSE = "ILITRF-DMAV-CORRELATION-PARSE";

    // Geometry (Phase 13)
    public static final String GEOM_TYPE_MISMATCH = "ILITRF-GEOM-TYPE-MISMATCH";
    public static final String GEOM_CRS_MISMATCH = "ILITRF-GEOM-CRS-MISMATCH";
    public static final String GEOM_INVALID = "ILITRF-GEOM-INVALID";
    public static final String GEOM_TOPOLOGY = "ILITRF-GEOM-TOPOLOGY";
    public static final String GEOM_LINEATTR_UNSUPPORTED = "ILITRF-GEOM-LINEATTR-UNSUPPORTED";
}
