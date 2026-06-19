package guru.interlis.transformer.mapping.ilimap.convert;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.util.*;

import org.junit.jupiter.api.Test;

class JobConfigToIlimapAstMapperTest {

    private final JobConfigToIlimapAstMapper mapper = new JobConfigToIlimapAstMapper();

    private JobConfig minimalConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job = new JobConfig.JobSection();
        config.job.name = "test-mapping";
        config.job.inputs = new ArrayList<>();
        config.job.outputs = new ArrayList<>();
        config.mapping = new JobConfig.MappingSection();
        config.mapping.rules = new ArrayList<>();

        JobConfig.InputSpec input = new JobConfig.InputSpec();
        input.id = "src";
        input.path = "in.xtf";
        input.model = "SrcModel";
        input.format = "xtf";
        config.job.inputs.add(input);

        JobConfig.OutputSpec output = new JobConfig.OutputSpec();
        output.id = "tgt";
        output.path = "out.xtf";
        output.model = "TgtModel";
        output.format = "xtf";
        config.job.outputs.add(output);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.output = "tgt";
        rule.target.clazz = "TgtModel.Topic.TgtClass";
        rule.sources = new ArrayList<>();
        JobConfig.SourceSpec source = new JobConfig.SourceSpec();
        source.alias = "s";
        source.inputs = List.of("src");
        source.clazz = "SrcModel.Topic.SrcClass";
        rule.sources.add(source);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("Name", "s.Name");
        config.mapping.rules.add(rule);

        return config;
    }

    @Test
    void mapsMinimalJobConfigToDocument() {
        IlimapDocument doc = mapper.map(minimalConfig());

        assertThat(doc.formatVersion()).isEqualTo(IlimapFormatVersion.V2);
        assertThat(doc.name()).isEqualTo("test-mapping");
        assertThat(doc.inputs()).hasSize(1);
        assertThat(doc.inputs().get(0).id()).isEqualTo("src");
        assertThat(doc.outputs()).hasSize(1);
        assertThat(doc.outputs().get(0).id()).isEqualTo("tgt");
        assertThat(doc.rules()).hasSize(1);
        assertThat(doc.rules().get(0).id()).isEqualTo("r1");
    }

    @Test
    void mapsJobBlock() {
        JobConfig config = minimalConfig();
        config.job.description = "A test";
        config.job.direction = "forward";
        config.job.failPolicy = "lenient";
        config.mapping.compileMode = "compatible";
        config.job.modeldir = List.of("https://models.example.com/");

        IlimapDocument doc = mapper.map(config);

        assertThat(doc.job()).isNotNull();
        assertThat(doc.job().description()).isEqualTo("A test");
        assertThat(doc.job().direction()).isEqualTo("forward");
        assertThat(doc.job().failPolicy()).isEqualTo("lenient");
        assertThat(doc.job().compileMode()).isEqualTo("compatible");
        assertThat(doc.job().modeldirs()).containsExactly("https://models.example.com/");
    }

    @Test
    void omitsJobBlockWhenAllDefaults() {
        JobConfig config = minimalConfig();
        config.job.name = "test";
        config.job.description = null;
        config.job.direction = null;
        config.job.failPolicy = "strict";
        config.mapping.compileMode = "strict";
        config.job.modeldir = null;

        IlimapDocument doc = mapper.map(config);
        assertThat(doc.job()).isNull();
    }

    @Test
    void mapsOidStrategy() {
        JobConfig config = minimalConfig();
        config.mapping.oidStrategy = new JobConfig.OidStrategySpec();
        config.mapping.oidStrategy.defaultStrategy = "deterministicUuid";
        config.mapping.oidStrategy.namespace = "my-ns";

        IlimapDocument doc = mapper.map(config);

        assertThat(doc.oid()).isNotNull();
        assertThat(doc.oid().strategy()).isEqualTo("deterministicUuid");
        assertThat(doc.oid().namespace()).isEqualTo("my-ns");
    }

    @Test
    void omitsOidBlockWhenDefault() {
        JobConfig config = minimalConfig();
        config.mapping.oidStrategy = new JobConfig.OidStrategySpec();
        config.mapping.oidStrategy.defaultStrategy = "integer";

        IlimapDocument doc = mapper.map(config);
        assertThat(doc.oid()).isNull();
    }

    @Test
    void mapsBasketStrategy() {
        JobConfig config = minimalConfig();
        config.mapping.basketStrategy = new JobConfig.BasketStrategySpec();
        config.mapping.basketStrategy.defaultStrategy = "byTopic";

        IlimapDocument doc = mapper.map(config);

        assertThat(doc.basket()).isNotNull();
        assertThat(doc.basket().strategy()).isEqualTo("byTopic");
    }

    @Test
    void omitsBasketWhenDefault() {
        JobConfig config = minimalConfig();
        config.mapping.basketStrategy = new JobConfig.BasketStrategySpec();
        config.mapping.basketStrategy.defaultStrategy = "preserve";

        IlimapDocument doc = mapper.map(config);
        assertThat(doc.basket()).isNull();
    }

    @Test
    void mapsEnumEntries() {
        JobConfig config = minimalConfig();
        config.mapping.enums = new LinkedHashMap<>();
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("active", "true");
        entries.put("inactive", "false");
        entries.put("count", "42");
        entries.put("label", "text");
        config.mapping.enums.put("StatusMap", entries);

        IlimapDocument doc = mapper.map(config);

        assertThat(doc.enums()).hasSize(1);
        IlimapEnumBlock enumBlock = doc.enums().get(0);
        assertThat(enumBlock.id()).isEqualTo("StatusMap");
        assertThat(enumBlock.entries()).hasSize(4);

        assertThat(enumBlock.entries().get(0).source()).isInstanceOf(IlimapLiteral.StringLit.class);
        assertThat(enumBlock.entries().get(0).target()).isInstanceOf(IlimapLiteral.BooleanLit.class);
        assertThat(enumBlock.entries().get(1).target()).isInstanceOf(IlimapLiteral.BooleanLit.class);
        assertThat(enumBlock.entries().get(2).target()).isInstanceOf(IlimapLiteral.NumberLit.class);
        assertThat(enumBlock.entries().get(3).target()).isInstanceOf(IlimapLiteral.StringLit.class);
    }

    @Test
    void mapsRuleWithAllElements() {
        JobConfig config = minimalConfig();
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        rule.where = "s.Active == true";
        rule.identity = new JobConfig.IdentitySpec();
        rule.identity.sourceKey = List.of("s.Id", "s.Name");
        rule.defaults = new LinkedHashMap<>();
        rule.defaults.put("Name", "\"fallback\"");

        rule.joins = new ArrayList<>();
        JobConfig.JoinSpec join = new JobConfig.JoinSpec();
        join.type = "inner";
        join.left = "s";
        join.right = "t";
        join.on = "s.Id == t.Id";
        rule.joins.add(join);

        IlimapDocument doc = mapper.map(config);
        IlimapRuleBlock ruleBlock = doc.rules().get(0);

        List<IlimapRuleElement> elements = ruleBlock.elements();
        assertThat(elements).anyMatch(e -> e instanceof IlimapTargetStmt);
        assertThat(elements).anyMatch(e -> e instanceof IlimapSourceStmt);
        assertThat(elements).anyMatch(e -> e instanceof IlimapWhereStmt);
        assertThat(elements).anyMatch(e -> e instanceof IlimapJoinStmt);
        assertThat(elements).anyMatch(e -> e instanceof IlimapIdentityStmt);
        assertThat(elements).anyMatch(e -> e instanceof IlimapAssignmentBlock);
        assertThat(elements).anyMatch(e -> e instanceof IlimapDefaultsBlock);

        IlimapJoinStmt joinStmt = elements.stream()
                .filter(e -> e instanceof IlimapJoinStmt)
                .map(e -> (IlimapJoinStmt) e)
                .findFirst()
                .orElseThrow();
        assertThat(joinStmt.joinType()).isEqualTo("inner");
        assertThat(joinStmt.leftAlias()).isEqualTo("s");
        assertThat(joinStmt.rightAlias()).isEqualTo("t");
        assertThat(joinStmt.on().text()).isEqualTo("s.Id == t.Id");
    }

    @Test
    void mapsBagWithNestedBagsAndParentRef() {
        JobConfig config = minimalConfig();
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        rule.bags = new LinkedHashMap<>();

        JobConfig.BagSpec bag = new JobConfig.BagSpec();
        bag.from = new JobConfig.BagFrom();
        bag.from.alias = "c";
        bag.from.input = "src";
        bag.from.clazz = "SrcModel.Topic.Child";
        bag.structure = "TgtModel.Topic.Position";
        bag.mode = "embed";
        bag.maxItems = 5;
        bag.parentRef = new JobConfig.BagParentRef();
        bag.parentRef.attribute = "ParentId";
        bag.parentRef.parentAlias = "s";
        bag.assign = new LinkedHashMap<>();
        bag.assign.put("X", "c.X");

        bag.nestedBags = new LinkedHashMap<>();
        JobConfig.BagSpec nested = new JobConfig.BagSpec();
        nested.structure = "TgtModel.Topic.Sub";
        nested.assign = new LinkedHashMap<>();
        nested.assign.put("Z", "c.Z");
        bag.nestedBags.put("SubBag", nested);

        rule.bags.put("Positions", bag);

        IlimapDocument doc = mapper.map(config);
        IlimapBagBlock bagBlock = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapBagBlock)
                .map(e -> (IlimapBagBlock) e)
                .findFirst()
                .orElseThrow();

        assertThat(bagBlock.id()).isEqualTo("Positions");
        assertThat(bagBlock.from().alias()).isEqualTo("c");
        assertThat(bagBlock.structure()).isEqualTo("TgtModel.Topic.Position");
        assertThat(bagBlock.mode()).isEqualTo("embed");
        assertThat(bagBlock.maxItems()).isEqualTo(5);
        assertThat(bagBlock.parentRef().kind()).isEqualTo("attribute");
        assertThat(bagBlock.parentRef().name()).isEqualTo("ParentId");
        assertThat(bagBlock.parentRef().parentAlias()).isEqualTo("s");
        assertThat(bagBlock.nestedBags()).hasSize(1);
        assertThat(bagBlock.nestedBags().get(0).id()).isEqualTo("SubBag");
    }

    @Test
    void mapsRefWithTargetObjectAndSourceRef() {
        JobConfig config = minimalConfig();
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        rule.refs = new ArrayList<>();

        JobConfig.RefMapping ref = new JobConfig.RefMapping();
        ref.target = "Entstehung";
        ref.association = "Entstehung_LFP3";
        ref.role = "Entstehung";
        ref.required = true;
        ref.targetObject = new JobConfig.RefMapping.RefTargetObject();
        ref.targetObject.rule = "lfp3-nachfuehrung";
        ref.targetObject.sourceRef = "p.Entstehung";
        rule.refs.add(ref);

        IlimapDocument doc = mapper.map(config);
        IlimapRefBlock refBlock = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapRefBlock)
                .map(e -> (IlimapRefBlock) e)
                .findFirst()
                .orElseThrow();

        assertThat(refBlock.id()).isEqualTo("Entstehung");
        assertThat(refBlock.association()).isEqualTo("Entstehung_LFP3");
        assertThat(refBlock.role()).isEqualTo("Entstehung");
        assertThat(refBlock.required()).isTrue();
        assertThat(refBlock.targetRuleId()).isEqualTo("lfp3-nachfuehrung");
        assertThat(refBlock.sourceRef().text()).isEqualTo("p.Entstehung");
    }

    @Test
    void mapsCreateBlock() {
        JobConfig config = minimalConfig();
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        rule.create = new ArrayList<>();

        JobConfig.CreateSpec create = new JobConfig.CreateSpec();
        create.clazz = "TgtModel.Topic.Extra";
        create.assign = new LinkedHashMap<>();
        create.assign.put("Type", "\"default\"");
        rule.create.add(create);

        IlimapDocument doc = mapper.map(config);
        IlimapCreateBlock createBlock = doc.rules().get(0).elements().stream()
                .filter(e -> e instanceof IlimapCreateBlock)
                .map(e -> (IlimapCreateBlock) e)
                .findFirst()
                .orElseThrow();

        assertThat(createBlock.targetClass()).isEqualTo("TgtModel.Topic.Extra");
        assertThat(createBlock.assign().assignments()).hasSize(1);
    }

    @Test
    void mapsLossAndMetadata() {
        JobConfig config = minimalConfig();
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);

        rule.losses = new ArrayList<>();
        JobConfig.LossSpec loss = new JobConfig.LossSpec();
        loss.sourcePath = "s.OldField";
        loss.reasonCode = "REMOVED";
        loss.description = "Field removed in new model";
        rule.losses.add(loss);

        rule.metadata = new JobConfig.MetadataSpec();
        rule.metadata.direction = "forward";
        rule.metadata.roundtrip = "no";
        rule.metadata.lossiness = "lossy";

        IlimapDocument doc = mapper.map(config);
        List<IlimapRuleElement> elements = doc.rules().get(0).elements();

        IlimapLossBlock lossBlock = elements.stream()
                .filter(e -> e instanceof IlimapLossBlock)
                .map(e -> (IlimapLossBlock) e)
                .findFirst()
                .orElseThrow();
        assertThat(lossBlock.sourcePath().text()).isEqualTo("s.OldField");
        assertThat(lossBlock.reasonCode()).isEqualTo("REMOVED");

        IlimapMetadataBlock metaBlock = elements.stream()
                .filter(e -> e instanceof IlimapMetadataBlock)
                .map(e -> (IlimapMetadataBlock) e)
                .findFirst()
                .orElseThrow();
        assertThat(metaBlock.direction()).isEqualTo("forward");
        assertThat(metaBlock.lossiness()).isEqualTo("lossy");
    }

    @Test
    void enumLiteralDetection() {
        assertThat(JobConfigToIlimapAstMapper.parseLiteral("true")).isInstanceOf(IlimapLiteral.BooleanLit.class);
        assertThat(JobConfigToIlimapAstMapper.parseLiteral("false")).isInstanceOf(IlimapLiteral.BooleanLit.class);
        assertThat(JobConfigToIlimapAstMapper.parseLiteral("42")).isInstanceOf(IlimapLiteral.NumberLit.class);
        assertThat(JobConfigToIlimapAstMapper.parseLiteral("3.14")).isInstanceOf(IlimapLiteral.NumberLit.class);
        assertThat(JobConfigToIlimapAstMapper.parseLiteral("text")).isInstanceOf(IlimapLiteral.StringLit.class);
        assertThat(JobConfigToIlimapAstMapper.parseLiteral(null)).isInstanceOf(IlimapLiteral.NullLit.class);
        assertThat(JobConfigToIlimapAstMapper.parseLiteral("null")).isInstanceOf(IlimapLiteral.NullLit.class);
    }

    @Test
    void denormalizesEnumMapWithDoubleQuotes() {
        Set<String> enumNames = Set.of("StatusMap");
        String result = JobConfigToIlimapAstMapper.denormalizeEnumMap("enumMap(s.Type, \"StatusMap\")", enumNames);
        assertThat(result).isEqualTo("enumMap(s.Type, StatusMap)");
    }

    @Test
    void denormalizesEnumMapWithSingleQuotes() {
        Set<String> enumNames = Set.of("StatusMap");
        String result = JobConfigToIlimapAstMapper.denormalizeEnumMap("enumMap(s.Type, 'StatusMap')", enumNames);
        assertThat(result).isEqualTo("enumMap(s.Type, StatusMap)");
    }

    @Test
    void doesNotDenormalizeUnknownEnumMap() {
        Set<String> enumNames = Set.of("StatusMap");
        String input = "enumMap(s.Type, \"UnknownMap\")";
        String result = JobConfigToIlimapAstMapper.denormalizeEnumMap(input, enumNames);
        assertThat(result).isEqualTo(input);
    }

    @Test
    void denormalizesMultipleEnumMapsInExpression() {
        Set<String> enumNames = Set.of("MapA", "MapB");
        String result = JobConfigToIlimapAstMapper.denormalizeEnumMap(
                "coalesce(enumMap(s.X, \"MapA\"), enumMap(s.Y, \"MapB\"))", enumNames);
        assertThat(result).isEqualTo("coalesce(enumMap(s.X, MapA), enumMap(s.Y, MapB))");
    }

    @Test
    void mapsTopLevelDefaults() {
        JobConfig config = minimalConfig();
        config.mapping.defaults = new LinkedHashMap<>();
        config.mapping.defaults.put("Description", "\"\"");

        IlimapDocument doc = mapper.map(config);

        assertThat(doc.defaults()).isNotNull();
        assertThat(doc.defaults().assignments()).hasSize(1);
        assertThat(doc.defaults().assignments().get(0).targetAttribute()).isEqualTo("Description");
    }
}
