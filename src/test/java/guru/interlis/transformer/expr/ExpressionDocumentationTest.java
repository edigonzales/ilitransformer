package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.expr.builtins.DateFunctions;
import guru.interlis.transformer.expr.builtins.EnumFunctions;
import guru.interlis.transformer.expr.builtins.GeometryFunctions;
import guru.interlis.transformer.expr.builtins.LookupFunctions;
import guru.interlis.transformer.expr.builtins.MathFunctions;
import guru.interlis.transformer.expr.builtins.RefFunctions;
import guru.interlis.transformer.expr.builtins.StringFunctions;
import guru.interlis.transformer.expr.FunctionDef;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ExpressionDocumentationTest {

    private static FunctionRegistry defaultRegistry() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        StringFunctions.registerAll(registry);
        DateFunctions.registerAll(registry);
        EnumFunctions.registerAll(registry);
        RefFunctions.registerAll(registry);
        MathFunctions.registerAll(registry);
        LookupFunctions.registerAll(registry);
        GeometryFunctions.registerAll(registry);
        return registry;
    }

    private static String expressionsDoc() throws Exception {
        return Files.readString(Path.of("docs/expressions.md"));
    }

    @Test
    void allRegisteredFunctionsMentionedInDocs() throws Exception {
        FunctionRegistry registry = defaultRegistry();
        String docs = expressionsDoc();
        for (FunctionDef def : registry.all().values()) {
            assertThat(docs)
                    .as("Function '%s' must be mentioned in docs/expressions.md", def.name())
                    .contains("`" + def.name() + "`");
        }
    }

    @Test
    void comparisonOperatorsAreNotDeclaredUnsupported() throws Exception {
        String docs = expressionsDoc();
        assertThat(docs)
                .as("Comparison operators must not be listed as unsupported/planned")
                .doesNotContain("Comparison operators (`>`, `<`, `>=`, `<=`)");
    }

    @Test
    void mathSectionDoesNotListUnregisteredFunctionsAsBuiltins() {
        FunctionRegistry registry = defaultRegistry();
        assertThat(registry.resolve("round")).isEmpty();
        assertThat(registry.resolve("abs")).isEmpty();

        assertThat(registry.resolve("div")).isPresent();
        assertThat(registry.resolve("mul")).isPresent();
    }

    @Test
    void unregisteredMathFunctionsNotInBuiltinTable() throws Exception {
        String docs = expressionsDoc();
        assertThat(docs).doesNotContain("| `round` | `(value, scale) → number` | Rounds");
        assertThat(docs).doesNotContain("| `abs` | `(value) → number` | Absolute value |");
    }

    @Test
    void enumMapIsNotDescribedAsStub() throws Exception {
        String docs = expressionsDoc();
        assertThat(docs)
                .as("enumMap must not be described as stub")
                .doesNotContain("(stub)");
    }
}
