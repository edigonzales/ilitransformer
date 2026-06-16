package guru.interlis.transformer.mapping.plan;

import guru.interlis.transformer.state.OidStrategy;

public record IdentityPlan(OidStrategy oidStrategy, String namespace, java.util.List<String> sourceKeys) {}
