package guru.interlis.transformer.mapping.plan;

import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.TransferDescription;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record InputBinding(
        String inputId,
        Path path,
        String declaredModelName,
        String format,
        Map<String, String> options,
        TransferDescription transferDescription,
        TypeSystemFacade typeSystem,
        JobConfig.JdbcConnectionSpec connection,
        List<JobConfig.JdbcQuerySpec> queries) {

    public InputBinding {
        options = options == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
        queries = queries == null ? List.of() : List.copyOf(queries);
    }

    /**
     * Backward-compatible constructor for non-JDBC bindings (no connection/queries). Keeps existing
     * call sites that predate the JDBC format support working unchanged.
     */
    public InputBinding(
            String inputId,
            Path path,
            String declaredModelName,
            String format,
            Map<String, String> options,
            TransferDescription transferDescription,
            TypeSystemFacade typeSystem) {
        this(inputId, path, declaredModelName, format, options, transferDescription, typeSystem, null, null);
    }
}
