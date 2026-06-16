package guru.interlis.transformer.app;

import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.ModelRegistry;
import java.nio.file.Path;

public record PreparedJob(
        TransformPlan plan,
        ModelRegistry modelRegistry,
        Path baseDirectory
) {}
