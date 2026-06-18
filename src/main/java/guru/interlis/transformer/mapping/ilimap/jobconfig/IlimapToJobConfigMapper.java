package guru.interlis.transformer.mapping.ilimap.jobconfig;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapExpressionNormalizer;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolTable;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.JobConfigNormalizer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IlimapToJobConfigMapper {

    private final IlimapExpressionNormalizer normalizer = new IlimapExpressionNormalizer();

    public JobConfig map(IlimapDocument document, IlimapSymbolTable symbols, Path baseDirectory) {
        JobConfig config = new JobConfig();
        config.version = 1;

        mapJobSection(document, config);
        mapInputs(document, config);
        mapOutputs(document, config);
        mapMappingSection(document, symbols, config);

        JobConfigNormalizer.normalize(config);
        return config;
    }

    private void mapJobSection(IlimapDocument document, JobConfig config) {
        config.job = new JobConfig.JobSection();
        if (document.job() != null) {
            IlimapJobBlock job = document.job();
            config.job.name = job.name();
            config.job.description = job.description();
            config.job.direction = job.direction();
            if (job.failPolicy() != null) {
                config.job.failPolicy = job.failPolicy();
            }
            config.job.modeldir = job.modeldirs().isEmpty() ? null : job.modeldirs();
        }
        if (config.job.name == null && document.name() != null) {
            config.job.name = document.name();
        }
    }

    private void mapInputs(IlimapDocument document, JobConfig config) {
        config.job.inputs = new ArrayList<>();
        for (IlimapInputBlock input : document.inputs()) {
            JobConfig.InputSpec spec = new JobConfig.InputSpec();
            spec.id = input.id();
            spec.path = input.path();
            spec.model = input.model();
            spec.format = input.format();
            config.job.inputs.add(spec);
        }
    }

    private void mapOutputs(IlimapDocument document, JobConfig config) {
        config.job.outputs = new ArrayList<>();
        for (IlimapOutputBlock output : document.outputs()) {
            JobConfig.OutputSpec spec = new JobConfig.OutputSpec();
            spec.id = output.id();
            spec.path = output.path();
            spec.model = output.model();
            spec.format = output.format();
            config.job.outputs.add(spec);
        }
    }

    private void mapMappingSection(IlimapDocument document, IlimapSymbolTable symbols, JobConfig config) {
        config.mapping = new JobConfig.MappingSection();

        if (document.job() != null && document.job().compileMode() != null) {
            config.mapping.compileMode = document.job().compileMode();
        }

        mapOidStrategy(document, config);
        mapBasketStrategy(document, config);
        mapEnums(document, config);
        mapTopLevelDefaults(document, symbols, config);
        mapRules(document, symbols, config);
    }

    private void mapOidStrategy(IlimapDocument document, JobConfig config) {
        config.mapping.oidStrategy = new JobConfig.OidStrategySpec();
        if (document.oid() != null) {
            config.mapping.oidStrategy.defaultStrategy = document.oid().strategy();
            config.mapping.oidStrategy.namespace = document.oid().namespace();
        }
    }

    private void mapBasketStrategy(IlimapDocument document, JobConfig config) {
        config.mapping.basketStrategy = new JobConfig.BasketStrategySpec();
        if (document.basket() != null) {
            config.mapping.basketStrategy.defaultStrategy = document.basket().strategy();
        }
    }

    private void mapEnums(IlimapDocument document, JobConfig config) {
        if (document.enums() == null || document.enums().isEmpty()) {
            return;
        }
        config.mapping.enums = new LinkedHashMap<>();
        for (IlimapEnumBlock enumBlock : document.enums()) {
            Map<String, String> entries = new LinkedHashMap<>();
            for (IlimapEnumEntry entry : enumBlock.entries()) {
                entries.put(literalToString(entry.source()), literalToString(entry.target()));
            }
            config.mapping.enums.put(enumBlock.id(), entries);
        }
    }

    private void mapTopLevelDefaults(IlimapDocument document, IlimapSymbolTable symbols, JobConfig config) {
        if (document.defaults() != null) {
            config.mapping.defaults = mapAssignments(document.defaults().assignments(), symbols);
        }
    }

    private void mapRules(IlimapDocument document, IlimapSymbolTable symbols, JobConfig config) {
        config.mapping.rules = new ArrayList<>();
        for (IlimapRuleBlock ruleBlock : document.rules()) {
            config.mapping.rules.add(mapRule(ruleBlock, symbols));
        }
    }

    private JobConfig.RuleSpec mapRule(IlimapRuleBlock rule, IlimapSymbolTable symbols) {
        JobConfig.RuleSpec spec = new JobConfig.RuleSpec();
        spec.id = rule.id();
        spec.sources = new ArrayList<>();

        for (IlimapRuleElement element : rule.elements()) {
            switch (element) {
                case IlimapTargetStmt target -> {
                    spec.target = new JobConfig.TargetSpec();
                    spec.target.output = target.outputId();
                    spec.target.clazz = target.targetClass();
                }
                case IlimapSourceStmt source -> {
                    JobConfig.SourceSpec sourceSpec = new JobConfig.SourceSpec();
                    sourceSpec.alias = source.alias();
                    sourceSpec.inputs = source.inputIds();
                    sourceSpec.clazz = source.sourceClass();
                    if (source.where() != null) {
                        sourceSpec.where = normalizer.normalizeForJobConfig(source.where(), symbols);
                    }
                    spec.sources.add(sourceSpec);
                }
                case IlimapWhereStmt where ->
                        spec.where = normalizer.normalizeForJobConfig(where.expression(), symbols);
                case IlimapIdentityStmt identity -> {
                    spec.identity = new JobConfig.IdentitySpec();
                    spec.identity.sourceKey = identity.expressions().stream()
                            .map(e -> normalizer.normalizeForJobConfig(e, symbols))
                            .toList();
                }
                case IlimapAssignmentBlock assignBlock ->
                        spec.assign = mapAssignments(assignBlock.assignments(), symbols);
                case IlimapDefaultsBlock defaultsBlock ->
                        spec.defaults = mapAssignments(defaultsBlock.assignments(), symbols);
            }
        }
        return spec;
    }

    private LinkedHashMap<String, String> mapAssignments(
            java.util.List<IlimapAssignment> assignments, IlimapSymbolTable symbols) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (IlimapAssignment a : assignments) {
            map.put(a.targetAttribute(), normalizer.normalizeForJobConfig(a.expression(), symbols));
        }
        return map;
    }

    static String literalToString(IlimapLiteral literal) {
        return switch (literal) {
            case IlimapLiteral.StringLit s -> s.value();
            case IlimapLiteral.BooleanLit b -> String.valueOf(b.value());
            case IlimapLiteral.NumberLit n -> n.text();
            case IlimapLiteral.NullLit ignored -> null;
            case IlimapLiteral.HashLit h -> h.value();
        };
    }
}
