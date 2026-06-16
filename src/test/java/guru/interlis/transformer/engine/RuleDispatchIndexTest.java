package guru.interlis.transformer.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RuleDispatchIndexTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade flatTs;
    private static TypeSystemFacade nestedTs;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        IliModelCompileResult flatResult = service.compileModel(MODELDIR + "bag-flat-source.ili", MODELDIR);
        if (flatResult.hasErrors()) {
            fail("Flat model compile errors: " + flatResult.diagnostics().all());
        }
        flatTs = new TypeSystemFacade(flatResult.transferDescription());

        IliModelCompileResult nestedResult = service.compileModel(MODELDIR + "bag-nested-target.ili", MODELDIR);
        if (nestedResult.hasErrors()) {
            fail("Nested model compile errors: " + nestedResult.diagnostics().all());
        }
        nestedTs = new TypeSystemFacade(nestedResult.transferDescription());
    }

    @Test
    void rulesForMatchingInputAndClass() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-embed-test.yaml");
        RuleDispatchIndex index = RuleDispatchIndex.build(plan);

        RulePlan rule = plan.rules().get(0);
        var sourcePlan = rule.sources().get(0);
        String scopedClass = TypeSystemFacade.getScopedName(sourcePlan.sourceClass());
        String inputId = sourcePlan.inputIds().get(0);

        java.util.List<RulePlan> matches = index.rulesFor(inputId, scopedClass);
        assertThat(matches).isNotEmpty();
    }

    @Test
    void unknownInputReturnsEmpty() throws Exception {
        TransformPlan plan = compileMapping("src/test/resources/mappings/bag-embed-test.yaml");
        RuleDispatchIndex index = RuleDispatchIndex.build(plan);

        assertThat(index.rulesFor("unknownInput", "UnknownClass")).isEmpty();
    }

    @Test
    void emptyPlanReturnsEmpty() {
        TransformPlan plan = new TransformPlan(
                "test",
                "forward",
                guru.interlis.transformer.mapping.plan.FailPolicy.STRICT,
                guru.interlis.transformer.mapping.plan.CompileMode.STRICT,
                java.util.List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                new guru.interlis.transformer.diag.DiagnosticCollector(),
                new guru.interlis.transformer.mapping.plan.OidPlan(
                        guru.interlis.transformer.state.OidStrategy.INTEGER, "ns"),
                new guru.interlis.transformer.mapping.plan.BasketPlan(
                        guru.interlis.transformer.state.BasketStrategy.PRESERVE),
                java.util.Map.of());

        RuleDispatchIndex index = RuleDispatchIndex.build(plan);

        assertThat(index.rulesFor("input1", "SomeClass")).isEmpty();
        assertThat(index.embedBagsFor("input1", "SomeClass")).isEmpty();
        assertThat(index.allExpandBags()).isEmpty();
    }

    private TransformPlan compileMapping(String mappingPath) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(Path.of(mappingPath).toFile(), JobConfig.class);
        Map<String, TypeSystemFacade> sourceTs = Map.of(
                "BagFlatSource", flatTs,
                "BagNestedTarget", nestedTs);
        Map<String, TypeSystemFacade> targetTs = Map.of(
                "BagFlatSource", flatTs,
                "BagNestedTarget", nestedTs);
        return new MappingCompiler().compileTyped(config, sourceTs, targetTs);
    }
}
