package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapExpressionReader;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticResult;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticValidator;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolTable;

import java.util.List;
import java.util.Objects;

public final class IlimapAnalysisService {

    private final IlimapSemanticValidator semanticValidator = new IlimapSemanticValidator();
    private final IlimapDiagnosticMapper diagnosticMapper = new IlimapDiagnosticMapper();

    public IlimapAnalysis analyze(String uri, String text, IlimapAnalysisOptions options) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(options, "options");

        IlimapLineMap lineMap = new IlimapLineMap(text);
        IlimapIdeRange fallbackRange =
                new IlimapIdeRange(new IlimapIdePosition(0, 0), new IlimapIdePosition(0, 0));

        IlimapDocument document;
        try {
            document = new IlimapParser(text).parseDocument();
        } catch (IlimapParser.ParseException e) {
            return syntaxErrorAnalysis(
                    uri,
                    text,
                    lineMap,
                    fallbackRange,
                    stripPositionPrefix(e.getMessage()),
                    e.position.line() + ":" + e.position.column());
        } catch (IlimapExpressionReader.ReaderException e) {
            return syntaxErrorAnalysis(
                    uri, text, lineMap, fallbackRange, stripPositionPrefix(e.getMessage()), e.getMessage());
        }

        IlimapSemanticResult semanticResult = semanticValidator.validate(document);
        List<IlimapIdeDiagnostic> diagnostics = options.includeSemanticDiagnostics()
                ? diagnosticMapper.map(semanticResult.diagnostics(), lineMap, fallbackRange)
                : List.of();

        return new IlimapAnalysis(uri, text, document, semanticResult.symbols(), diagnostics, lineMap);
    }

    private IlimapAnalysis syntaxErrorAnalysis(
            String uri,
            String text,
            IlimapLineMap lineMap,
            IlimapIdeRange fallbackRange,
            String message,
            String sourcePath) {
        Diagnostic diagnostic =
                new Diagnostic(DiagnosticCode.ILIMAP_SYNTAX_ERROR, Severity.ERROR, message, sourcePath, null);
        List<IlimapIdeDiagnostic> diagnostics = diagnosticMapper.map(List.of(diagnostic), lineMap, fallbackRange);
        return new IlimapAnalysis(uri, text, null, new IlimapSymbolTable(), diagnostics, lineMap);
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
