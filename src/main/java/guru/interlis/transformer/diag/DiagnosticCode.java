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
    public static final String MAP_IDENTITY_KEY_INVALID_TYPE = "ILITRF-MAP-IDENTITY-KEY-INVALID-TYPE";
    public static final String MAP_IDENTITY_KEY_DUPLICATE = "ILITRF-MAP-IDENTITY-KEY-DUPLICATE";
    public static final String MAP_EXTERNAL_STRATEGY_UNSUPPORTED = "ILITRF-MAP-EXTERNAL-STRATEGY-UNSUPPORTED";
    public static final String MAP_OID_STRATEGY_INCOMPATIBLE = "ILITRF-MAP-OID-STRATEGY-INCOMPATIBLE";

    // Model
    public static final String MODEL_COMPILE_FAILED = "ILITRF-MODEL-COMPILE-FAILED";

    // Runtime
    public static final String RUN_REF_UNRESOLVED = "ILITRF-RUN-REF-UNRESOLVED";
    public static final String RUN_REF_AMBIGUOUS = "ILITRF-RUN-REF-AMBIGUOUS";
    public static final String RUN_REF_TYPE_MISMATCH = "ILITRF-RUN-REF-TYPE-MISMATCH";
    public static final String RUN_REF_MISSING_MANDATORY = "ILITRF-RUN-REF-MISSING-MANDATORY";
    public static final String RUN_REF_CARDINALITY = "ILITRF-RUN-REF-CARDINALITY";
    public static final String RUN_MISSING_SOURCE_OID = "ILITRF-RUN-MISSING-SOURCE-OID";
    public static final String RUN_DUPLICATE_TARGET_OID = "ILITRF-RUN-DUPLICATE-TARGET-OID";

    // Expression (Phase 4)
    public static final String EXPR_SYNTAX = "ILITRF-EXPR-SYNTAX";
    public static final String EXPR_UNKNOWN_FUNC = "ILITRF-EXPR-UNKNOWN-FUNC";
    public static final String EXPR_TYPE = "ILITRF-EXPR-TYPE";
    public static final String EXPR_NON_DETERMINISTIC = "ILITRF-EXPR-NON-DETERMINISTIC";
    public static final String EXPR_UNSUPPORTED = "ILITRF-EXPR-UNSUPPORTED";

    // Expression Compiler (Phase 20)
    public static final String EXPR_UNKNOWN_PATH = "ILITRF-EXPR-UNKNOWN-PATH";
    public static final String EXPR_WRONG_ARG_COUNT = "ILITRF-EXPR-WRONG-ARG-COUNT";
    public static final String EXPR_WRONG_ARG_TYPE = "ILITRF-EXPR-WRONG-ARG-TYPE";
    public static final String EXPR_ENUM_MAP_MISSING = "ILITRF-EXPR-ENUM-MAP-MISSING";
    public static final String EXPR_ENUM_MAP_INCOMPLETE = "ILITRF-EXPR-ENUM-MAP-INCOMPLETE";
    public static final String EXPR_ENUM_TARGET_INVALID = "ILITRF-EXPR-ENUM-TARGET-INVALID";
    public static final String EXPR_NOT_DETERMINISTIC = "ILITRF-EXPR-NOT-DETERMINISTIC";

    // DM01/DMAV
    public static final String DMAV_CORRELATION_PARSE = "ILITRF-DMAV-CORRELATION-PARSE";

    // DSL consistency (Phase 21)
    public static final String MAP_UNSUPPORTED_FEATURE = "ILITRF-MAP-UNSUPPORTED-FEATURE";
    public static final String MAP_UNSUPPORTED_BASKET_STRATEGY = "ILITRF-MAP-UNSUPPORTED-BASKET-STRATEGY";
    public static final String MAP_UNSUPPORTED_BAG_MODE = "ILITRF-MAP-UNSUPPORTED-BAG-MODE";
    public static final String MAP_UNKNOWN_COMPILE_MODE = "ILITRF-MAP-UNKNOWN-COMPILE-MODE";

    // Phase 22 – Joins
    public static final String MAP_JOIN_INVALID = "ILITRF-MAP-JOIN-INVALID";
    public static final String MAP_JOIN_NON_EQUI = "ILITRF-MAP-JOIN-NON-EQUI";
    public static final String MAP_JOIN_UNKNOWN_ALIAS = "ILITRF-MAP-JOIN-UNKNOWN-ALIAS";
    public static final String MAP_JOIN_SELF_REF = "ILITRF-MAP-JOIN-SELF-REF";
    public static final String MAP_JOIN_MISSING_SOURCE = "ILITRF-MAP-JOIN-MISSING-SOURCE";

    // Phase 22 – Create
    public static final String MAP_CREATE_INVALID = "ILITRF-MAP-CREATE-INVALID";
    public static final String MAP_CREATE_UNKNOWN_CLASS = "ILITRF-MAP-CREATE-UNKNOWN-CLASS";
    public static final String MAP_CREATE_DUPLICATE = "ILITRF-MAP-CREATE-DUPLICATE";

    // Phase 22 – Runtime Join
    public static final String RUN_JOIN_MISSING = "ILITRF-RUN-JOIN-MISSING";
    public static final String RUN_JOIN_AMBIGUOUS = "ILITRF-RUN-JOIN-AMBIGUOUS";

    // Phase 23 – BAG OF STRUCTURE
    public static final String MAP_BAG_PARENT_REF_MISSING = "ILITRF-MAP-BAG-PARENT-REF-MISSING";
    public static final String MAP_BAG_EXPAND_IDENTITY_MISSING = "ILITRF-MAP-BAG-EXPAND-IDENTITY-MISSING";
    public static final String RUN_BAG_MANDATORY_MISSING = "ILITRF-RUN-BAG-MANDATORY-MISSING";
    public static final String RUN_BAG_PARENT_OID_MISSING = "ILITRF-RUN-BAG-PARENT-OID-MISSING";

    // Geometry (Phase 13)
    public static final String GEOM_TYPE_MISMATCH = "ILITRF-GEOM-TYPE-MISMATCH";
    public static final String GEOM_CRS_MISMATCH = "ILITRF-GEOM-CRS-MISMATCH";
    public static final String GEOM_INVALID = "ILITRF-GEOM-INVALID";
    public static final String GEOM_TOPOLOGY = "ILITRF-GEOM-TOPOLOGY";
    public static final String GEOM_LINEATTR_UNSUPPORTED = "ILITRF-GEOM-LINEATTR-UNSUPPORTED";
    public static final String GEOM_AREA_POINT_MISSING = "ILITRF-GEOM-AREA-POINT-MISSING";
    public static final String GEOM_SEGMENT_UNSUPPORTED = "ILITRF-GEOM-SEGMENT-UNSUPPORTED";

    // Geometry – Phase 24
    public static final String GEOM_DIMENSION_MISMATCH = "ILITRF-GEOM-DIMENSION-MISMATCH";
    public static final String GEOM_COORD_DOMAIN_MISMATCH = "ILITRF-GEOM-COORD-DOMAIN-MISMATCH";
}
