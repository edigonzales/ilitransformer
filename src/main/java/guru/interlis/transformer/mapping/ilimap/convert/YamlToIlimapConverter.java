package guru.interlis.transformer.mapping.ilimap.convert;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatter;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.JobConfigNormalizer;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class YamlToIlimapConverter {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final JobConfigToIlimapAstMapper astMapper = new JobConfigToIlimapAstMapper();
    private final IlimapFormatter formatter = new IlimapFormatter();

    public String convert(JobConfig config) {
        IlimapDocument document = astMapper.map(config);
        String result = formatter.format(document);
        verifyParseable(result);
        return result;
    }

    public String convert(Path yamlPath) throws IOException {
        JobConfig config = yamlMapper.readValue(yamlPath.toFile(), JobConfig.class);
        JobConfigNormalizer.normalize(config);
        return convert(config);
    }

    private void verifyParseable(String ilimapText) {
        try {
            new IlimapParser(ilimapText).parseDocument();
        } catch (Exception e) {
            throw new ConvertException("Converted ilimap output is not parseable: " + e.getMessage(), e);
        }
    }
}
