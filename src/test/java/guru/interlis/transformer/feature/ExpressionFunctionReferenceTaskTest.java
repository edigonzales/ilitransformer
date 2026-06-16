package guru.interlis.transformer.feature;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.expr.FunctionRegistry;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ExpressionFunctionReferenceTaskTest {

    @Test
    void generatesMarkdownContainingRegisteredFunctions() throws Exception {
        Path markdownPath = Files.createTempFile("expr-func-ref", ".md");
        try {
            ExpressionFunctionReferenceTask.main(new String[] {"--markdown", markdownPath.toString()});

            String markdown = Files.readString(markdownPath);
            FunctionRegistry registry = FunctionRegistry.defaultRegistry();

            for (var def : registry.all().values()) {
                assertThat(markdown)
                        .as("markdown must contain function '%s'", def.name())
                        .contains("`" + def.name() + "`");
                assertThat(markdown)
                        .as("markdown must mention return type for '%s'", def.name())
                        .contains(def.returnType().name());
            }
        } finally {
            Files.deleteIfExists(markdownPath);
        }
    }

    @Test
    void generatesJsonContainingNameReturnTypeAndDeterminism() throws Exception {
        Path jsonPath = Files.createTempFile("expr-func-ref", ".json");
        try {
            ExpressionFunctionReferenceTask.main(new String[] {"--json", jsonPath.toString()});

            String json = Files.readString(jsonPath);
            FunctionRegistry registry = FunctionRegistry.defaultRegistry();

            for (var def : registry.all().values()) {
                assertThat(json).contains("\"name\": \"" + def.name() + "\"");
                assertThat(json)
                        .contains("\"returnType\": \"" + def.returnType().name() + "\"");
                assertThat(json).contains("\"deterministic\": " + (def.deterministic() ? "true" : "false"));
            }
        } finally {
            Files.deleteIfExists(jsonPath);
        }
    }

    @Test
    void generatesBothMarkdownAndJsonWhenBothSpecified() throws Exception {
        Path markdownPath = Files.createTempFile("expr-func-ref", ".md");
        Path jsonPath = Files.createTempFile("expr-func-ref", ".json");
        try {
            ExpressionFunctionReferenceTask.main(
                    new String[] {"--markdown", markdownPath.toString(), "--json", jsonPath.toString()});

            assertThat(Files.readString(markdownPath)).isNotEmpty();
            assertThat(Files.readString(jsonPath)).isNotEmpty();
        } finally {
            Files.deleteIfExists(markdownPath);
            Files.deleteIfExists(jsonPath);
        }
    }
}
