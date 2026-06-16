package guru.interlis.transformer.mapping.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes backward-compatible fields in a raw {@link JobConfig} into their canonical equivalents.
 * After normalization, consumers should only read the new/canonical fields.
 *
 * <p>Also provides static helpers that replace the deprecated
 * {@code getEffective*} methods on {@link JobConfig.RuleSpec} and {@link JobConfig.SourceSpec}.
 */
public final class JobConfigNormalizer {

    private JobConfigNormalizer() {}

    // -- Normalization --------------------------------------------------------

    /**
     * Normalizes a raw Jackson-deserialized {@link JobConfig} in-place.
     * <ul>
     * <li>Ensures non-null defaults for sections.
     * <li>Converts flat {@code targetClass}/{@code output} into nested {@code target.clazz}/{@code target.output}.
     * <li>Converts single {@code input} string into {@code inputs} list.
     * </ul>
     */
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

        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            if (rule.sources == null) {
                rule.sources = new java.util.ArrayList<>();
            }
            if (rule.target == null) {
                if ((rule.targetClass != null && !rule.targetClass.isBlank())
                        || (rule.output != null && !rule.output.isBlank())) {
                    rule.target = new JobConfig.TargetSpec();
                    rule.target.clazz = rule.targetClass;
                    rule.target.output = rule.output;
                }
            }
            for (JobConfig.SourceSpec source : rule.sources) {
                if (getInputIds(source).size() == 1
                        && (source.inputs == null || source.inputs.isEmpty())
                        && source.input != null
                        && !source.input.isBlank()) {
                    source.inputs = List.of(source.input);
                }
            }
        }
    }

    // -- Effective value helpers ----------------------------------------------

    /** Returns the effective target class (new format preferred, fallback to flat). */
    public static String getEffectiveTargetClass(JobConfig.RuleSpec rule) {
        if (rule.target != null && rule.target.clazz != null && !rule.target.clazz.isBlank()) {
            return rule.target.clazz;
        }
        return rule.targetClass;
    }

    /** Returns the effective target output ID (new format preferred, fallback to flat). */
    public static String getEffectiveTargetOutput(JobConfig.RuleSpec rule) {
        if (rule.target != null && rule.target.output != null && !rule.target.output.isBlank()) {
            return rule.target.output;
        }
        return rule.output;
    }

    /** Returns the merged list of attribute assignments from both {@code assign} map and {@code attributes} list. */
    public static List<JobConfig.AttributeMapping> getAllAttributes(JobConfig.RuleSpec rule) {
        List<JobConfig.AttributeMapping> result = new ArrayList<>();
        if (rule.assign != null) {
            rule.assign.forEach((k, v) -> {
                JobConfig.AttributeMapping am = new JobConfig.AttributeMapping();
                am.target = k;
                am.expr = v;
                result.add(am);
            });
        }
        if (rule.attributes != null) {
            result.addAll(rule.attributes);
        }
        return result;
    }

    /** Returns the effective refs list (non-null). */
    public static List<JobConfig.RefMapping> getEffectiveRefs(JobConfig.RuleSpec rule) {
        return rule.refs != null ? rule.refs : List.of();
    }

    /** Returns the list of input IDs (new format preferred, fallback to single string). */
    public static List<String> getInputIds(JobConfig.SourceSpec source) {
        if (source.inputs != null && !source.inputs.isEmpty()) {
            return source.inputs;
        }
        if (source.input != null && !source.input.isBlank()) {
            return List.of(source.input);
        }
        return List.of();
    }
}
