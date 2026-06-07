package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.mapping.model.JobConfig;
import java.util.HashSet;
import java.util.Set;

public final class MappingCompiler {
    public void validate(JobConfig config) {
        Set<String> outputs = new HashSet<>();
        for (JobConfig.OutputSpec output : config.job.outputs) {
            if (output.id == null || output.id.isBlank()) {
                throw new IllegalArgumentException("Output id is required");
            }
            outputs.add(output.id);
        }

        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            if (rule.targetClass == null || rule.targetClass.isBlank()) {
                throw new IllegalArgumentException("Rule targetClass is required");
            }
            if (rule.output == null || !outputs.contains(rule.output)) {
                throw new IllegalArgumentException("Rule references unknown output: " + rule.output);
            }
            Set<String> aliases = new HashSet<>();
            for (JobConfig.SourceSpec source : rule.sources) {
                if (source.alias == null || source.alias.isBlank()) {
                    throw new IllegalArgumentException("Source alias is required for rule " + rule.targetClass);
                }
                if (!aliases.add(source.alias)) {
                    throw new IllegalArgumentException("Duplicate source alias in rule " + rule.targetClass + ": " + source.alias);
                }
                if (source.clazz == null || source.clazz.isBlank()) {
                    throw new IllegalArgumentException("Source class is required for alias " + source.alias);
                }
                if (source.input == null || source.input.isBlank()) {
                    throw new IllegalArgumentException("Source input is required for alias " + source.alias);
                }
            }
        }
    }
}
