package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.expr.ExpressionCompiler;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.model.ModelRegistry;
import guru.interlis.transformer.state.OidStrategy;

import java.util.Map;

public record CompilerContext(
        JobConfig config,
        ModelRegistry modelRegistry,
        FunctionRegistry functionRegistry,
        ExpressionCompiler expressionCompiler,
        DiagnosticCollector diagnostics,
        Map<String, Map<String, String>> enumMaps,
        OidStrategy oidStrategy,
        String oidNamespace,
        Map<String, String> globalDefaults
) {}
