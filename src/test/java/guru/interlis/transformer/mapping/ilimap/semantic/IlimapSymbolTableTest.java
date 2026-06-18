package guru.interlis.transformer.mapping.ilimap.semantic;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapSymbolTableTest {

    private static final IlimapSourceRange DUMMY_RANGE =
            new IlimapSourceRange(new IlimapSourcePosition(0, 1, 1), new IlimapSourcePosition(1, 1, 2));

    private static IlimapInputBlock dummyInput(String id) {
        return new IlimapInputBlock(id, "in.xtf", "M", "xtf", DUMMY_RANGE);
    }

    private static IlimapOutputBlock dummyOutput(String id) {
        return new IlimapOutputBlock(id, "out.xtf", "M", "xtf", DUMMY_RANGE);
    }

    private static IlimapRuleBlock dummyRule(String id) {
        return new IlimapRuleBlock(id, List.of(), DUMMY_RANGE);
    }

    private static IlimapEnumBlock dummyEnum(String id) {
        return new IlimapEnumBlock(id, List.of(), DUMMY_RANGE);
    }

    @Test
    void defineAndResolveInput() {
        var table = new IlimapSymbolTable();
        var diagnostics = new DiagnosticCollector();
        var input = dummyInput("src");

        table.topLevelScope().define(new IlimapSymbol(IlimapSymbolKind.INPUT, "src", input), diagnostics);

        assertThat(diagnostics.all()).isEmpty();
        assertThat(table.resolveInput("src")).isPresent();
        assertThat(table.resolveInput("src").get().name()).isEqualTo("src");
        assertThat(table.resolveInput("src").get().kind()).isEqualTo(IlimapSymbolKind.INPUT);
    }

    @Test
    void resolveOutputAndRule() {
        var table = new IlimapSymbolTable();
        var diagnostics = new DiagnosticCollector();

        table.topLevelScope().define(new IlimapSymbol(IlimapSymbolKind.OUTPUT, "out", dummyOutput("out")), diagnostics);
        table.topLevelScope().define(new IlimapSymbol(IlimapSymbolKind.RULE, "r1", dummyRule("r1")), diagnostics);

        assertThat(diagnostics.all()).isEmpty();
        assertThat(table.resolveOutput("out")).isPresent();
        assertThat(table.resolveRule("r1")).isPresent();
        assertThat(table.resolveInput("out")).isEmpty();
    }

    @Test
    void resolveEnumMap() {
        var table = new IlimapSymbolTable();
        var diagnostics = new DiagnosticCollector();

        table.topLevelScope()
                .define(new IlimapSymbol(IlimapSymbolKind.ENUM_MAP, "MyEnum", dummyEnum("MyEnum")), diagnostics);

        assertThat(diagnostics.all()).isEmpty();
        assertThat(table.resolveEnumMap("MyEnum")).isPresent();
        assertThat(table.resolveEnumMap("NonExistent")).isEmpty();
    }

    @Test
    void detectsDuplicateIds() {
        var table = new IlimapSymbolTable();
        var diagnostics = new DiagnosticCollector();

        table.topLevelScope().define(new IlimapSymbol(IlimapSymbolKind.INPUT, "src", dummyInput("src")), diagnostics);
        table.topLevelScope().define(new IlimapSymbol(IlimapSymbolKind.INPUT, "src", dummyInput("src")), diagnostics);

        assertThat(diagnostics.all()).hasSize(1);
        assertThat(diagnostics.all().get(0).code()).isEqualTo(DiagnosticCode.ILIMAP_DUPLICATE_ID);
        assertThat(diagnostics.all().get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void ruleScopeResolvesFromTopLevel() {
        var table = new IlimapSymbolTable();
        var diagnostics = new DiagnosticCollector();
        var rule = dummyRule("r1");

        table.topLevelScope().define(new IlimapSymbol(IlimapSymbolKind.INPUT, "src", dummyInput("src")), diagnostics);

        IlimapScope ruleScope = table.scopeFor(rule);
        assertThat(ruleScope.resolve("src")).isPresent();
        assertThat(ruleScope.resolve("src").get().kind()).isEqualTo(IlimapSymbolKind.INPUT);
    }

    @Test
    void ruleScopeLocalSymbolOverridesParent() {
        var table = new IlimapSymbolTable();
        var diagnostics = new DiagnosticCollector();
        var rule = dummyRule("r1");

        table.topLevelScope().define(new IlimapSymbol(IlimapSymbolKind.INPUT, "p", dummyInput("p")), diagnostics);

        IlimapScope ruleScope = table.scopeFor(rule);
        var sourceStmt = new IlimapSourceStmt("p", List.of("p"), "M.A", null, DUMMY_RANGE);
        ruleScope.define(new IlimapSymbol(IlimapSymbolKind.SOURCE_ALIAS, "p", sourceStmt), diagnostics);

        assertThat(ruleScope.resolveLocal("p")).isPresent();
        assertThat(ruleScope.resolveLocal("p").get().kind()).isEqualTo(IlimapSymbolKind.SOURCE_ALIAS);
    }

    @Test
    void resolveReturnsEmptyForUnknownId() {
        var table = new IlimapSymbolTable();
        assertThat(table.resolveInput("nonexistent")).isEmpty();
        assertThat(table.resolveOutput("nonexistent")).isEmpty();
        assertThat(table.resolveRule("nonexistent")).isEmpty();
        assertThat(table.resolveEnumMap("nonexistent")).isEmpty();
    }
}
