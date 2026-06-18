package guru.interlis.transformer.mapping.model;

import guru.interlis.transformer.mapping.ilimap.IlimapLoader;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class MappingLoader {

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    private final MappingFormatDetector formatDetector = new MappingFormatDetector();

    public JobConfig load(Path path) throws IOException {
        MappingFormat format = formatDetector.detect(path);
        return switch (format) {
            case YAML -> loadYaml(path);
            case ILIMAP -> new IlimapLoader().load(path);
        };
    }

    private JobConfig loadYaml(Path path) throws IOException {
        JobConfig config = objectMapper.readValue(path.toFile(), JobConfig.class);
        JobConfigNormalizer.normalize(config);
        return config;
    }

    /**
     * @deprecated Use {@link JobConfigNormalizer#normalize(JobConfig)} instead.
     */
    @Deprecated
    void normalize(JobConfig config) {
        JobConfigNormalizer.normalize(config);
    }
}
