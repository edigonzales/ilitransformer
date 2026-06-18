package guru.interlis.transformer.mapping.ilimap;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.jobconfig.IlimapToJobConfigMapper;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapExpressionReader;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticResult;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticValidator;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class IlimapLoader {

    public JobConfig load(Path path) {
        IlimapLoadResult result = loadDetailed(path);
        if (result.hasErrors()) {
            String messages = result.diagnostics().stream()
                    .filter(d -> d.severity() == Severity.ERROR)
                    .map(d -> d.sourcePath() != null ? d.sourcePath() + ": " + d.message() : d.message())
                    .collect(Collectors.joining("; "));
            throw new IlimapLoadException(messages);
        }
        return result.jobConfig();
    }

    public JobConfig load(String source, Path baseDirectory) {
        IlimapLoadResult result = loadDetailed(source, baseDirectory);
        if (result.hasErrors()) {
            String messages = result.diagnostics().stream()
                    .filter(d -> d.severity() == Severity.ERROR)
                    .map(d -> d.message())
                    .collect(Collectors.joining("; "));
            throw new IlimapLoadException(messages);
        }
        return result.jobConfig();
    }

    public IlimapLoadResult loadDetailed(Path path) {
        String source;
        try {
            source = Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read ilimap file: " + path, e);
        }
        IlimapLoadResult result = loadDetailed(source, path.toAbsolutePath().getParent());
        return enrichWithFilePath(result, path.toString());
    }

    public IlimapLoadResult loadDetailed(String source, Path baseDirectory) {
        IlimapDocument document;
        try {
            document = new IlimapParser(source).parseDocument();
        } catch (IlimapParser.ParseException e) {
            String location = e.position.line() + ":" + e.position.column();
            String cleanMessage = stripPositionPrefix(e.getMessage());
            Diagnostic diag =
                    new Diagnostic(DiagnosticCode.ILIMAP_SYNTAX_ERROR, Severity.ERROR, cleanMessage, location, null);
            return new IlimapLoadResult(null, null, null, List.of(diag));
        } catch (IlimapExpressionReader.ReaderException e) {
            String cleanMessage = stripPositionPrefix(e.getMessage());
            Diagnostic diag =
                    new Diagnostic(DiagnosticCode.ILIMAP_SYNTAX_ERROR, Severity.ERROR, cleanMessage, null, null);
            return new IlimapLoadResult(null, null, null, List.of(diag));
        }

        IlimapSemanticResult semanticResult = new IlimapSemanticValidator().validate(document);

        JobConfig jobConfig = null;
        if (!semanticResult.hasErrors()) {
            jobConfig = new IlimapToJobConfigMapper().map(document, semanticResult.symbols(), baseDirectory);
        }

        return new IlimapLoadResult(document, semanticResult.symbols(), jobConfig, semanticResult.diagnostics());
    }

    private static IlimapLoadResult enrichWithFilePath(IlimapLoadResult result, String filePath) {
        if (filePath == null || result.diagnostics().isEmpty()) {
            return result;
        }
        List<Diagnostic> enriched = result.diagnostics().stream()
                .map(d -> {
                    String path = d.sourcePath() != null ? filePath + ":" + d.sourcePath() : filePath;
                    return new Diagnostic(d.code(), d.severity(), d.message(), path, d.suggestion());
                })
                .toList();
        return new IlimapLoadResult(result.document(), result.symbols(), result.jobConfig(), enriched);
    }

    private static String stripPositionPrefix(String message) {
        if (message != null && message.startsWith("at line ")) {
            int colonSpace = message.indexOf(": ");
            if (colonSpace >= 0) {
                return message.substring(colonSpace + 2);
            }
        }
        return message;
    }
}
