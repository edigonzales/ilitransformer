package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingSummaryParams;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapRuleDetailParams;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapSourceAttributeUsageSummary;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapValidateMappingParams;

import java.lang.reflect.Method;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.junit.jupiter.api.Test;

class IlimapMappingSummaryLspTest {

    private static final String URI = "file:///mapping.ilimap";

    @Test
    void exposesMappingSummaryCustomRequest() throws NoSuchMethodException {
        Method method = IlimapLanguageServer.class.getMethod("mappingSummary", IlimapMappingSummaryParams.class);
        JsonRequest request = method.getAnnotation(JsonRequest.class);

        assertThat(request).isNotNull();
        assertThat(request.value()).isEqualTo("ilimap/mappingSummary");
        assertThat(request.useSegment()).isFalse();
    }

    @Test
    void exposesValidateMappingCustomRequest() throws NoSuchMethodException {
        Method method = IlimapLanguageServer.class.getMethod("validateMapping", IlimapValidateMappingParams.class);
        JsonRequest request = method.getAnnotation(JsonRequest.class);

        assertThat(request).isNotNull();
        assertThat(request.value()).isEqualTo("ilimap/validateMapping");
        assertThat(request.useSegment()).isFalse();
    }

    @Test
    void exposesRuleDetailCustomRequest() throws NoSuchMethodException {
        Method method = IlimapLanguageServer.class.getMethod("ruleDetail", IlimapRuleDetailParams.class);
        JsonRequest request = method.getAnnotation(JsonRequest.class);

        assertThat(request).isNotNull();
        assertThat(request.value()).isEqualTo("ilimap/ruleDetail");
        assertThat(request.useSegment()).isFalse();
    }

    @Test
    void returnsSummaryForOpenDocument() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server = new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());
        textDocumentService.didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, validMapping())));

        var summary = server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();

        assertThat(summary.available()).isTrue();
        assertThat(summary.mappingName()).isEqualTo("Profile");
        assertThat(summary.inputCount()).isEqualTo(1);
        assertThat(summary.outputCount()).isEqualTo(1);
        assertThat(summary.ruleCount()).isEqualTo(1);
        assertThat(summary.rules()).singleElement().satisfies(rule -> {
            assertThat(rule.id()).isEqualTo("r1");
            assertThat(rule.status()).isEqualTo("ok");
        });
        assertThat(summary.coverageAvailable()).isFalse();
        assertThat(summary.coverageMessage()).contains("Save or Validate Mapping");
    }

    @Test
    void summaryUsesCachedModelAwareDiagnosticsAfterValidation() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server = new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());
        String source = modelAwareMappingWithMissingSourceAttribute();
        textDocumentService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));

        var fastSummary =
                server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();
        var validation = server.validateMapping(new IlimapValidateMappingParams(URI, source, 1))
                .join();
        var cachedModelAwareSummary =
                server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();

        assertThat(fastSummary.errorCount()).isZero();
        assertThat(validation.available()).isTrue();
        assertThat(validation.diagnosticCount()).isGreaterThan(0);
        assertThat(cachedModelAwareSummary.errorCount()).isGreaterThan(0);
        assertThat(cachedModelAwareSummary.rules())
                .singleElement()
                .extracting(rule -> rule.status())
                .isEqualTo("error");
    }

    @Test
    void summaryUsesCachedModelAwareCoverageAfterValidation() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server = new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());
        String source = modelAwareMappingWithPartialAssignments();
        textDocumentService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));

        var fastSummary =
                server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();
        server.validateMapping(new IlimapValidateMappingParams(URI, source, 1)).join();
        var cachedModelAwareSummary =
                server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();

        assertThat(fastSummary.coverageAvailable()).isFalse();
        assertThat(cachedModelAwareSummary.coverageAvailable()).isTrue();
        assertThat(cachedModelAwareSummary.classCoverage()).anySatisfy(coverage -> {
            assertThat(coverage.outputId()).isEqualTo("out");
            assertThat(coverage.className()).isEqualTo("TestModel.TestTopic.TestClass");
            assertThat(coverage.targeted()).isTrue();
            assertThat(coverage.assignedAttributeCount()).isEqualTo(1);
            assertThat(coverage.mandatoryMissingCount()).isZero();
        });
        assertThat(cachedModelAwareSummary.ruleCoverage()).singleElement().satisfies(ruleCoverage -> {
            assertThat(ruleCoverage.ruleId()).isEqualTo("r1");
            assertThat(ruleCoverage.directAssignmentCount()).isEqualTo(1);
            assertThat(ruleCoverage.attributes())
                    .filteredOn(attribute -> attribute.name().equals("Name"))
                    .singleElement()
                    .satisfies(attribute -> assertThat(attribute.assigned()).isTrue());
            assertThat(ruleCoverage.attributes())
                    .filteredOn(attribute -> attribute.name().equals("Beschreibung"))
                    .singleElement()
                    .satisfies(attribute -> assertThat(attribute.assigned()).isFalse());
            assertThat(ruleCoverage.sources()).singleElement().satisfies(sourceUsage -> assertThat(
                            sourceUsage.usedAttributes())
                    .contains("Name"));
        });
    }

    @Test
    void classCoverageExcludesImportedDependencyModelsAfterValidation() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server = new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());
        String source = avMapping();
        textDocumentService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));

        server.validateMapping(new IlimapValidateMappingParams(URI, source, 1)).join();
        var summary = server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();

        assertThat(summary.coverageAvailable()).isTrue();
        assertThat(summary.classCoverage())
                .extracting(coverage -> coverage.className())
                .allSatisfy(className -> assertThat(className).startsWith("DM01AVCH24LV95D."))
                .noneSatisfy(className -> assertThat(className).startsWith("CoordSys."));
    }

    @Test
    void populatesCoverageAttributeStatusAndGroupedSourceUsageAfterValidation() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server = new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());
        String source = modelAwareMappingWithPartialAssignments();
        textDocumentService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
        server.validateMapping(new IlimapValidateMappingParams(URI, source, 1)).join();

        var summary = server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();

        assertThat(summary.ruleCoverage()).singleElement().satisfies(ruleCoverage -> {
            assertThat(ruleCoverage.attributes())
                    .filteredOn(attribute -> attribute.name().equals("Name"))
                    .singleElement()
                    .satisfies(name -> {
                        assertThat(name.status()).isEqualTo("mapped");
                        assertThat(name.expression()).isEqualTo("s.Name");
                        assertThat(name.sourceSummary()).isEqualTo("s.Name");
                    });
            assertThat(ruleCoverage.attributes())
                    .filteredOn(attribute -> attribute.name().equals("Beschreibung"))
                    .singleElement()
                    .satisfies(beschreibung -> assertThat(beschreibung.status()).isEqualTo("unknown"));
        });

        assertThat(summary.sourceUsage()).singleElement().satisfies(group -> {
            assertThat(group.sourceClass()).isEqualTo("TestModel.TestTopic.TestClass");
            assertThat(group.inputIds()).containsExactly("src");
            assertThat(group.aliases()).containsExactly("s");
            assertThat(group.attributes())
                    .filteredOn(attribute -> attribute.name().equals("Name"))
                    .singleElement()
                    .satisfies(name -> {
                        assertThat(name.kind()).isEqualTo("attribute");
                        assertThat(name.status()).isEqualTo("used");
                        assertThat(name.usedBy()).isNotEmpty();
                    });
            assertThat(group.attributes())
                    .filteredOn(attribute -> attribute.status().equals("unused"))
                    .extracting(IlimapSourceAttributeUsageSummary::name)
                    .contains("Beschreibung", "Anzahl", "Aktiv");
        });
    }

    @Test
    void marksMissingMandatoryCoverageAttributeAfterValidation() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server = new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());
        String source = modelAwareMappingWithoutMandatoryAssignment();
        textDocumentService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
        server.validateMapping(new IlimapValidateMappingParams(URI, source, 1)).join();

        var summary = server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();

        assertThat(summary.ruleCoverage()).singleElement().satisfies(ruleCoverage -> assertThat(
                        ruleCoverage.attributes())
                .filteredOn(attribute -> attribute.name().equals("Name"))
                .singleElement()
                .satisfies(name -> {
                    assertThat(name.assigned()).isFalse();
                    assertThat(name.status()).isEqualTo("missing");
                }));
    }

    @Test
    void returnsUnavailableSummaryForUnopenedDocument() {
        IlimapLanguageServer server =
                new IlimapLanguageServer(new IlimapTextDocumentService(), new IlimapWorkspaceService());

        var summary = server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();

        assertThat(summary.available()).isFalse();
        assertThat(summary.message()).contains("No open ILIMAP document");
        assertThat(summary.inputCount()).isZero();
        assertThat(summary.rules()).isEmpty();
    }

    @Test
    void returnsUnavailableValidationResultForUnopenedDocumentWithoutText() {
        IlimapLanguageServer server =
                new IlimapLanguageServer(new IlimapTextDocumentService(), new IlimapWorkspaceService());

        var result = server.validateMapping(new IlimapValidateMappingParams(URI, null, null))
                .join();

        assertThat(result.available()).isFalse();
        assertThat(result.message()).contains("No open ILIMAP document");
        assertThat(result.diagnosticCount()).isZero();
    }

    private static String validMapping() {
        return """
                mapping v2 "Profile" {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String modelAwareMappingWithMissingSourceAttribute() {
        return """
                mapping v2 "Profile" {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.TestTopic.TestClass";
                    source s from src class "TestModel.TestTopic.TestClass";
                    assign {
                      Name = s.Missing;
                    }
                  }
                }
                """;
    }

    private static String modelAwareMappingWithPartialAssignments() {
        return """
                mapping v2 "Profile" {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.TestTopic.TestClass";
                    source s from src class "TestModel.TestTopic.TestClass";
                    assign {
                      Name = s.Name;
                    }
                  }
                }
                """;
    }

    private static String avMapping() {
        return """
                mapping v2 "Profile" {
                  job {
                    modeldir "src/test/data/av/models/";
                  }
                  input src { path "in.itf"; model "DM01AVCH24LV95D"; }
                  output out { path "out.itf"; model "DM01AVCH24LV95D"; }
                  rule r1 {
                    target out class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
                    source p from src class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
                  }
                }
                """;
    }

    private static String modelAwareMappingWithoutMandatoryAssignment() {
        return """
                mapping v2 "Profile" {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.TestTopic.TestClass";
                    source s from src class "TestModel.TestTopic.TestClass";
                    assign {
                      Beschreibung = s.Name;
                    }
                  }
                }
                """;
    }
}
