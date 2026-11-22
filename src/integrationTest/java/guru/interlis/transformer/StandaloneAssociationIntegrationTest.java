package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.api.MigrationDraftService;
import guru.interlis.transformer.api.MigrationService;
import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.PreparedJob;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.loss.LossEvent;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.MappingLoader;
import guru.interlis.transformer.model.ModelRegistry;
import guru.interlis.transformer.validation.InProcessIlivalidatorService;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StandaloneAssociationIntegrationTest {
    private static final String MODELS =
            Path.of("src/test/data/standalone-associations").toAbsolutePath().toString();
    private static final String SOURCE = "P42Source";
    private static final String TARGET = "P42Target";
    private static final String BID = "b1";
    private static final String REMOTE_BID = "b2";

    @TempDir
    Path dir;

    private Path input;
    private Path output;
    private Path mapping;
    private MigrationDraftService.Draft draft;

    @BeforeEach
    void writeValidatedSourceTransfer() throws Exception {
        input = dir.resolve("original.xtf");
        output = dir.resolve("target.xtf");
        mapping = dir.resolve("migration.ilimap");
        draft = new MigrationDraftService().create(SOURCE, TARGET, MODELS, input.toString(), output.toString());

        var site = named("Site", "s1", "First site");
        var secondSite = named("Site", "s2", "Second site");
        var route = named("Route", "r1", "Route");
        reference(route, "EmbeddedSite", "s1");
        var pointer = object("Pointer", "p1");
        reference(pointer, "Destination", "s1");
        reference(pointer, "RemoteSite", "s3");

        var writer = new InterlisIoFactory()
                .createWriter(input, draft.source().types().getTransferDescription());
        try {
            writer.write(new StartTransferEvent("test", null, null));
            writer.write(new StartBasketEvent(SOURCE + ".Data", BID));
            for (var obj : List.of(
                    site,
                    secondSite,
                    route,
                    pointer,
                    link("Identified", "id1", "s1", "identified"),
                    link("Anonymous", null, "s1", "first"),
                    link("Anonymous", null, "s2", "second"))) {
                writer.write(new ObjectEvent(obj));
            }
            writer.write(new EndBasketEvent());
            writer.write(new StartBasketEvent(SOURCE + ".Data", REMOTE_BID));
            writer.write(new ObjectEvent(named("Site", "s3", "Remote site")));
            writer.write(new EndBasketEvent());
            writer.write(new EndTransferEvent());
        } finally {
            writer.close();
        }
        assertValid(input, SOURCE);
    }

    @Test
    void migratesStandaloneLinksAndDirectReferencesThroughXtf() throws Exception {
        var diagnostics = new MigrationService().execute(request(mappingText()), ignored -> {});
        assertThat(diagnostics.hasErrors()).as("%s", diagnostics.all()).isFalse();
        assertValid(output, TARGET);

        var baskets = new ArrayList<String>();
        List<IomObject> objects = readObjects(output, draft.target().types().getTransferDescription(), baskets);
        assertThat(objects).hasSize(8);
        assertThat(baskets).containsExactlyInAnyOrder("b1", "b2");
        var localSite = byValue(objects, "Name", "First site");
        var secondSite = byValue(objects, "Name", "Second site");
        var remoteSite = byValue(objects, "Name", "Remote site");
        var route = byValue(objects, "Name", "Route");
        assertThat(localSite.getobjectoid()).isNotBlank().isNotEqualTo("s1");
        assertThat(secondSite.getobjectoid()).isNotBlank().isNotEqualTo(localSite.getobjectoid());
        assertThat(route.getobjectoid()).isNotBlank().isNotEqualTo("r1");

        var identified = byValue(objects, "Note", "IDENTIFIED");
        assertThat(identified.getobjecttag()).isEqualTo(TARGET + ".Data.Identified");
        assertThat(identified.getobjectoid()).isNotBlank().isNotEqualTo("id1");
        assertReference(identified, "IdentifiedSite", localSite);
        assertReference(identified, "IdentifiedRoute", route);

        var first = byValue(objects, "Note", "FIRST");
        var second = byValue(objects, "Note", "SECOND");
        for (var anonymous : List.of(first, second)) {
            assertThat(anonymous.getobjecttag()).isEqualTo(TARGET + ".Data.Anonymous");
            assertThat(anonymous.getobjectoid()).isNull();
            assertReference(anonymous, "AnonymousRoute", route);
        }
        assertReference(first, "AnonymousSite", localSite);
        assertReference(second, "AnonymousSite", secondSite);

        var pointer = objects.stream()
                .filter(obj -> obj.getobjecttag().endsWith(".Pointer"))
                .findFirst()
                .orElseThrow();
        assertReference(pointer, "Destination", localSite);
        assertReference(pointer, "RemoteSite", remoteSite);
        assertReference(route, "EmbeddedSite", localSite);
        assertThat(objects).noneMatch(obj -> obj.getobjecttag().endsWith(".Embedded"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            source s from original class "P42Source.Data.Site";|source s from original class "P42Source.Data.Site" where false;|ILITRF-RUN-REF-MISSING-MANDATORY
            target migrated class "P42Target.Data.Route";|target migrated class "P42Target.Data.Site";|ILITRF-RUN-REF-TYPE-MISMATCH
            ref AnonymousRoute { required; target rule mapRoute sourceRef s.AnonymousRoute; }| |ILITRF-MAP-MANDATORY-MISSING
            ref AnonymousRoute {|ref NoSuchRole {|ILITRF-MAP-UNKNOWN-ROLE
            s.AnonymousRoute;|s;|ILITRF-RUN-REF-MISSING-MANDATORY
            source s from original class "P42Source.Data.Anonymous";|source s from original class "P42Source.Data.Embedded";|ILITRF-MAP-UNSUPPORTED-FEATURE
            ref RemoteSite { target rule mapSite sourceRef s.RemoteSite; }|assign { RemoteSite = refOid(s.RemoteSite); }|ILITRF-MAP-TYPE-MISMATCH
            target migrated class "P42Target.Data.Anonymous";|target migrated class "P42Target.Data.Computed";|ILITRF-MAP-UNSUPPORTED-FEATURE
""")
    void rejectsInvalidMappingsWithoutPublishing(String from, String to, String code) throws Exception {
        String text = mappingText().replace(from, to == null ? "" : to);
        var errors = new MigrationService().execute(request(text), ignored -> {});
        assertThat(errors.all()).as("%s", errors.all()).anyMatch(d -> d.code().equals(code));
        assertThat(output).doesNotExist();
    }

    @Test
    void rejectsAmbiguousLinkEndpointWithoutPublishing() throws Exception {
        String text = mappingText();
        int start = text.indexOf("  rule mapSite");
        int end = text.indexOf("  rule mapRoute");
        var request = request(text.substring(0, start)
                + text.substring(start, end).replace("rule mapSite", "rule secondSite")
                + text.substring(start));
        var prepared = new MigrationService().prepare(request);
        var config = new MappingLoader().load(mapping);
        config.job.inputs.getFirst().path = input.toString();
        config.job.outputs.getFirst().path = output.toString();
        var ref = config.mapping.rules.stream()
                .filter(rule -> rule.id.equals("mapAnonymous"))
                .findFirst()
                .orElseThrow()
                .refs
                .getFirst();
        ref.targetRule = null;
        ref.targetObject = null;
        var registry = ModelRegistry.builder()
                .config(config)
                .modelDirs(List.of(MODELS))
                .baseDirectory(dir)
                .build();
        var plan = new MappingCompiler().compileTyped(config, registry);
        assertThat(plan.diagnostics().hasErrors())
                .as("%s", plan.diagnostics().all())
                .isFalse();
        var errors = new JobRunner(ignored -> {}).runPrepared(new PreparedJob(plan, registry, dir), prepared.options());
        assertThat(errors.all()).as("%s", errors.all()).anyMatch(d -> d.code()
                .equals(DiagnosticCode.RUN_REF_AMBIGUOUS));
        assertThat(output).doesNotExist();
    }

    @Test
    void reportsOneLossPerOidlessStandaloneAssociation() throws Exception {
        String text = mappingText();
        int start = text.indexOf("  rule mapAnonymous");
        int end = text.indexOf("  rule mapPointer", start);
        var base = request(text.substring(0, start) + """
                  rule discardAnonymous {
                    source s from original class "P42Source.Data.Anonymous";
                    loss { sourcePath s; reasonCode "CLASS_REMOVED"; description "Removed links"; }
                  }
                """ + text.substring(end));
        Path report = dir.resolve("report");
        var diagnostics = new MigrationService()
                .execute(
                        new MigrationService.Request(
                                base.mapping(), input, output, List.of(MODELS), true, false, report),
                        ignored -> {});
        assertThat(diagnostics.hasErrors()).as("%s", diagnostics.all()).isFalse();
        assertThat(readObjects(output, draft.target().types().getTransferDescription(), new ArrayList<>()))
                .extracting(IomObject::getobjecttag)
                .doesNotContain(TARGET + ".Data.Anonymous");
        var loss = new LossEvent(
                "discardAnonymous", SOURCE + ".Data.Anonymous", null, "s", "CLASS_REMOVED", "Removed links");
        assertThat(new ObjectMapper()
                        .readValue(report.resolve("lossiness-report.json").toFile(), LossEvent[].class))
                .containsExactly(loss, loss);
    }

    @Test
    void draftsOnlyUnambiguousStandaloneLinksAndUsesRefForInheritedAttributes() throws Exception {
        var link = draft.target().entity(TARGET + ".Data.Anonymous");
        assertThat(link.structure()).isFalse();
        assertThat(link.fields())
                .extracting(MigrationDraftService.Field::name)
                .containsExactlyInAnyOrder("Note", "AnonymousSite", "AnonymousRoute");
        assertThat(draft.source().entity(SOURCE + ".Data.Pointer").fields())
                .anyMatch(field -> field.name().equals("Destination")
                        && field.required()
                        && field.referenceTarget().equals(SOURCE + ".Data.Site"));
        assertThat(draft.text())
                .contains("ref Destination {", "ref AnonymousRoute {", "Note = s.Note;")
                .doesNotContain("class \"P42Target.Data.Manual\"", "class \"P42Target.Data.Embedded\"");
        assertThat(draft.differences())
                .anyMatch(difference -> difference.sourceClass().equals(SOURCE + ".Data.Manual")
                        && difference.status() == MigrationDraftService.Status.DECISION);
        var prepared = new MigrationService().prepare(request(draft.text()));
        assertThat(prepared.job().plan().diagnostics().hasErrors())
                .as("%s", prepared.job().plan().diagnostics().all())
                .isFalse();
    }

    private MigrationService.Request request(String text) throws Exception {
        Files.writeString(mapping, text);
        return new MigrationService.Request(mapping, input, output, List.of(MODELS), false, false, null);
    }

    private static String mappingText() {
        return """
                mapping v2 "Standalone links" {
                  input original { path "original.xtf"; model "P42Source"; }
                  output migrated { path "target.xtf"; model "P42Target"; }
                  oid { strategy uuid; }
                  basket preserve;
                  rule mapSite {
                    target migrated class "P42Target.Data.Site";
                    source s from original class "P42Source.Data.Site";
                    assign { Name = s.Name; }
                  }
                  rule mapRoute {
                    target migrated class "P42Target.Data.Route";
                    source s from original class "P42Source.Data.Route";
                    assign { Name = s.Name; }
                    ref EmbeddedSite { target rule mapSite sourceRef s.EmbeddedSite; }
                  }
                  rule mapIdentified {
                    target migrated class "P42Target.Data.Identified";
                    source s from original class "P42Source.Data.Identified";
                    assign { Note = upper(s.Note); }
                    ref IdentifiedSite { required; target rule mapSite sourceRef s.IdentifiedSite; }
                    ref IdentifiedRoute { required; target rule mapRoute sourceRef s.IdentifiedRoute; }
                  }
                  rule mapAnonymous {
                    target migrated class "P42Target.Data.Anonymous";
                    source s from original class "P42Source.Data.Anonymous";
                    assign { Note = upper(s.Note); }
                    ref AnonymousSite { required; target rule mapSite sourceRef s.AnonymousSite; }
                    ref AnonymousRoute { required; target rule mapRoute sourceRef s.AnonymousRoute; }
                  }
                  rule mapPointer {
                    target migrated class "P42Target.Data.Pointer";
                    source s from original class "P42Source.Data.Pointer";
                    ref Destination { required; target rule mapSite sourceRef s.Destination; }
                    ref RemoteSite { target rule mapSite sourceRef s.RemoteSite; }
                  }
                }
                """;
    }

    private static Iom_jObject object(String tag, String oid) {
        return new Iom_jObject(SOURCE + ".Data." + tag, oid);
    }

    private static Iom_jObject named(String tag, String oid, String name) {
        var object = object(tag, oid);
        object.setattrvalue("Name", name);
        return object;
    }

    private static IomObject link(String tag, String oid, String site, String note) {
        var object = object(tag, oid);
        object.setattrvalue("Note", note);
        reference(object, tag + "Site", site);
        reference(object, tag + "Route", "r1");
        return object;
    }

    private static void reference(IomObject owner, String name, String oid) {
        owner.addattrobj(name, Iom_jObject.REF).setobjectrefoid(oid);
    }

    private static void assertReference(IomObject owner, String name, IomObject target) {
        assertThat(owner.getattrvaluecount(name)).isEqualTo(1);
        var ref = owner.getattrobj(name, 0);
        assertThat(ref.getobjectrefoid()).isEqualTo(target.getobjectoid());
        assertThat(ref.getobjectrefbid()).isNull();
    }

    private static IomObject byValue(List<IomObject> objects, String name, String value) {
        return objects.stream()
                .filter(obj -> value.equals(obj.getattrvalue(name)))
                .findFirst()
                .orElseThrow();
    }

    private static List<IomObject> readObjects(Path path, TransferDescription model, List<String> baskets)
            throws Exception {
        var reader = new InterlisIoFactory().createReader(path, model);
        var objects = new ArrayList<IomObject>();
        try {
            ch.interlis.iox.IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ch.interlis.iox.ObjectEvent object) objects.add(object.getIomObject());
                if (event instanceof ch.interlis.iox.StartBasketEvent start) baskets.add(start.getBid());
                if (event instanceof ch.interlis.iox.EndTransferEvent) break;
            }
        } finally {
            reader.close();
        }
        return objects;
    }

    private static void assertValid(Path path, String model) {
        var validation = new InProcessIlivalidatorService()
                .validate(path, List.of(MODELS), List.of(model), path.resolveSibling(path.getFileName() + ".log"));
        assertThat(validation.valid()).as("%s", validation.logText()).isTrue();
    }
}
