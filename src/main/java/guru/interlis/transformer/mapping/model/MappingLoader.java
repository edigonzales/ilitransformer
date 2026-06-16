package guru.interlis.transformer.mapping.model;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class MappingLoader {

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    public JobConfig load(Path path) throws IOException {
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
