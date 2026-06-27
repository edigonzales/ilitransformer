package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.Objects;

public record IlimapCodeLensSummary(IlimapOverviewLocation location, String title, String command, String ruleId) {

    public IlimapCodeLensSummary {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(ruleId, "ruleId");
    }
}
