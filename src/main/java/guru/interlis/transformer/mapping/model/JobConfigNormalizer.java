package guru.interlis.transformer.mapping.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JobConfigNormalizer {

    private JobConfigNormalizer() {}

    public static void normalize(JobConfig config) {
        if (config.mapping == null) {
            config.mapping = new JobConfig.MappingSection();
        }
        if (config.job == null) {
            config.job = new JobConfig.JobSection();
        }
        if (config.mapping.oidStrategy == null) {
            config.mapping.oidStrategy = new JobConfig.OidStrategySpec();
        }
        if (config.mapping.basketStrategy == null) {
            config.mapping.basketStrategy = new JobConfig.BasketStrategySpec();
        }

        for (JobConfig.InputSpec input : config.job.inputs) {
            if (input.options == null) {
                input.options = new LinkedHashMap<>();
            }
            if (input.queries == null) {
                input.queries = new java.util.ArrayList<>();
            }
            for (JobConfig.JdbcQuerySpec query : input.queries) {
                if (query.columns == null) {
                    query.columns = new LinkedHashMap<>();
                }
            }
            if (input.connection != null && input.connection.properties == null) {
                input.connection.properties = new LinkedHashMap<>();
            }
        }
        for (JobConfig.OutputSpec output : config.job.outputs) {
            if (output.options == null) {
                output.options = new LinkedHashMap<>();
            }
        }

        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            if (rule.sources == null) {
                rule.sources = new java.util.ArrayList<>();
            }
        }
    }

    /**
     * Normalizes raw format options (as deserialized from YAML, where values may be Booleans, Integers
     * etc.) into an ordered string-keyed/string-valued map. Null values are dropped; all other values
     * are converted with {@link String#valueOf(Object)}.
     */
    public static Map<String, String> normalizeOptions(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
