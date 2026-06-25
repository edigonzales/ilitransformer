package guru.interlis.transformer.mapping.plan;

import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.TransferDescription;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record InputBinding(
        String inputId,
        Path path,
        String declaredModelName,
        TransferFormat format,
        Map<String, String> options,
        TransferDescription transferDescription,
        TypeSystemFacade typeSystem) {

    public InputBinding {
        options = options == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }
}
