package guru.interlis.transformer.engine;

import guru.interlis.transformer.mapping.model.JobConfig;

public record RuleRuntime(JobConfig.RuleSpec rule) {}
