package guru.interlis.transformer.mapping.plan;

import guru.interlis.transformer.state.OidStrategy;

public record OidPlan(OidStrategy defaultStrategy, String namespace) {}
