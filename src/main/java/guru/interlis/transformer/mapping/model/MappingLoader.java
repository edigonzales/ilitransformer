package guru.interlis.transformer.mapping.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;

public final class MappingLoader {
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    public JobConfig load(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), JobConfig.class);
    }
}
