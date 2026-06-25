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
            spec.options = new LinkedHashMap<>(input.options());
            spec.connection = mapConnection(input.connection());
            spec.queries = mapQueries(input.queries());
            config.job.inputs.add(spec);
        }
    }

    private JobConfig.JdbcConnectionSpec mapConnection(IlimapConnectionBlock connection) {
        if (connection == null) {
            return null;
        }
        JobConfig.JdbcConnectionSpec spec = new JobConfig.JdbcConnectionSpec();
        spec.driver = connection.driver();
        spec.url = connection.url();
        spec.user = connection.user();
        spec.password = connection.password();
        spec.userEnv = connection.userEnv();
        spec.passwordEnv = connection.passwordEnv();
        spec.properties = new LinkedHashMap<>(connection.properties());
        return spec;
    }

    private java.util.List<JobConfig.JdbcQuerySpec> mapQueries(java.util.List<IlimapQueryBlock> queries) {
        java.util.List<JobConfig.JdbcQuerySpec> result = new ArrayList<>();
        for (IlimapQueryBlock query : queries) {
            JobConfig.JdbcQuerySpec spec = new JobConfig.JdbcQuerySpec();
            spec.id = query.id();
            spec.clazz = query.sourceClass();
            spec.topic = query.topic();
            spec.basketId = query.basketId();
            spec.oidColumn = query.oidColumn();
            spec.sql = query.sql();
            spec.columns = new LinkedHashMap<>(query.columns());
            spec.geometry = mapGeometry(query.geometry());
            result.add(spec);
        }
        return result;
    }

    private java.util.List<JobConfig.JdbcGeometrySpec> mapGeometry(java.util.List<IlimapGeometryBlock> geometryBlocks) {
        java.util.List<JobConfig.JdbcGeometrySpec> result = new ArrayList<>();
        for (IlimapGeometryBlock block : geometryBlocks) {
            JobConfig.JdbcGeometrySpec spec = new JobConfig.JdbcGeometrySpec();
            spec.attribute = block.attribute();
            spec.column = block.column();
            spec.encoding = block.encoding();
            spec.type = block.type();
            spec.srid = block.srid();
            result.add(spec);
        }
        return result;
    }

    private void mapOutputs(IlimapDocument document, JobConfig config) {
        config.job.outputs = new ArrayList<>();
        for (IlimapOutputBlock output : document.outputs()) {
            JobConfig.OutputSpec spec = new JobConfig.OutputSpec();
            spec.id = output.id();
            spec.path = output.path();
            spec.model = output.model();
            spec.format = output.format();
            spec.options = new LinkedHashMap<>(output.options());
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
                case IlimapJoinStmt join -> {
                    if (spec.joins == null) {
                        spec.joins = new ArrayList<>();
                    }
                    JobConfig.JoinSpec js = new JobConfig.JoinSpec();
                    js.type = join.joinType();
                    js.left = join.leftAlias();
                    js.right = join.rightAlias();
                    js.on = normalizer.normalizeForJobConfig(join.on(), symbols);
                    spec.joins.add(js);
                }
                case IlimapBagBlock bag -> {
                    if (spec.bags == null) {
                        spec.bags = new LinkedHashMap<>();
                    }
                    spec.bags.put(bag.id(), mapBag(bag, symbols));
                }
                case IlimapRefBlock ref -> {
                    if (spec.refs == null) {
                        spec.refs = new ArrayList<>();
                    }
                    JobConfig.RefMapping rm = new JobConfig.RefMapping();
                    rm.target = ref.id();
                    rm.association = ref.association();
                    rm.role = ref.role();
                    rm.required = ref.required();
                    if (ref.targetRuleId() != null) {
                        rm.targetObject = new JobConfig.RefMapping.RefTargetObject();
                        rm.targetObject.rule = ref.targetRuleId();
                        if (ref.sourceRef() != null) {
                            rm.targetObject.sourceRef = normalizer.normalizeForJobConfig(ref.sourceRef(), symbols);
                        }
                        rm.targetRule = ref.targetRuleId();
                        if (ref.sourceRef() != null) {
                            rm.sourceRef = normalizer.normalizeForJobConfig(ref.sourceRef(), symbols);
                        }
                    }
                    spec.refs.add(rm);
                }
                case IlimapCreateBlock create -> {
                    if (spec.create == null) {
                        spec.create = new ArrayList<>();
                    }
                    JobConfig.CreateSpec cs = new JobConfig.CreateSpec();
                    cs.clazz = create.targetClass();
                    if (create.assign() != null) {
                        cs.assign = mapAssignments(create.assign().assignments(), symbols);
                    }
                    spec.create.add(cs);
                }
                case IlimapLossBlock loss -> {
                    if (spec.losses == null) {
                        spec.losses = new ArrayList<>();
                    }
                    JobConfig.LossSpec ls = new JobConfig.LossSpec();
                    if (loss.sourcePath() != null) {
                        ls.sourcePath = normalizer.normalizeForJobConfig(loss.sourcePath(), symbols);
                    }
                    ls.reasonCode = loss.reasonCode();
                    ls.description = loss.description();
                    if (loss.when() != null) {
                        ls.when = normalizer.normalizeForJobConfig(loss.when(), symbols);
                    }
                    spec.losses.add(ls);
                }
                case IlimapMetadataBlock meta -> {
                    spec.metadata = new JobConfig.MetadataSpec();
                    spec.metadata.direction = meta.direction();
                    spec.metadata.roundtrip = meta.roundtrip();
                    spec.metadata.lossiness = meta.lossiness();
                }
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

    private JobConfig.BagSpec mapBag(IlimapBagBlock bag, IlimapSymbolTable symbols) {
        JobConfig.BagSpec bs = new JobConfig.BagSpec();
        bs.target = bag.targetAttribute();
        if (bag.from() != null) {
            bs.from = new JobConfig.BagFrom();
            bs.from.alias = bag.from().alias();
            bs.from.input = bag.from().inputId();
            bs.from.clazz = bag.from().sourceClass();
            if (bag.from().where() != null) {
                bs.from.where = normalizer.normalizeForJobConfig(bag.from().where(), symbols);
            }
        }
        bs.structure = bag.structure();
        bs.mode = bag.mode();
        bs.maxItems = bag.maxItems();
        if (bag.where() != null) {
            bs.where = normalizer.normalizeForJobConfig(bag.where(), symbols);
        }
        if (bag.parentRef() != null) {
            bs.parentRef = new JobConfig.BagParentRef();
            bs.parentRef.parentAlias = bag.parentRef().parentAlias();
            if ("attribute".equals(bag.parentRef().kind())) {
                bs.parentRef.attribute = bag.parentRef().name();
            } else if ("role".equals(bag.parentRef().kind())) {
                bs.parentRef.role = bag.parentRef().name();
            }
        }
        if (bag.assign() != null) {
            bs.assign = mapAssignments(bag.assign().assignments(), symbols);
        }
        if (bag.nestedBags() != null && !bag.nestedBags().isEmpty()) {
            bs.nestedBags = new LinkedHashMap<>();
            for (IlimapBagBlock nested : bag.nestedBags()) {
                bs.nestedBags.put(nested.id(), mapBag(nested, symbols));
            }
        }
        return bs;
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
