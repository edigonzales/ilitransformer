package guru.interlis.transformer.mapping.ilimap;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.jobconfig.IlimapToJobConfigMapper;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticResult;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticValidator;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public final class IlimapLoader {

    public JobConfig load(Path path) {
        IlimapLoadResult result = loadDetailed(path);
        if (result.hasErrors()) {
            String messages = result.diagnostics().stream()
                    .filter(d -> d.severity() == guru.interlis.transformer.diag.Severity.ERROR)
                    .map(d -> d.message())
                    .collect(Collectors.joining("; "));
            throw new IlimapLoadException(path + ": " + messages);
        }
        return result.jobConfig();
    }

    public JobConfig load(String source, Path baseDirectory) {
        IlimapLoadResult result = loadDetailed(source, baseDirectory);
        if (result.hasErrors()) {
            String messages = result.diagnostics().stream()
                    .filter(d -> d.severity() == guru.interlis.transformer.diag.Severity.ERROR)
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
        return loadDetailed(source, path.toAbsolutePath().getParent());
    }

    public IlimapLoadResult loadDetailed(String source, Path baseDirectory) {
        IlimapDocument document = new IlimapParser(source).parseDocument();
        IlimapSemanticResult semanticResult = new IlimapSemanticValidator().validate(document);

        JobConfig jobConfig = null;
        if (!semanticResult.hasErrors()) {
            jobConfig = new IlimapToJobConfigMapper()
                    .map(document, semanticResult.symbols(), baseDirectory);
        }

        return new IlimapLoadResult(
                document,
                semanticResult.symbols(),
                jobConfig,
                semanticResult.diagnostics());
    }
}
