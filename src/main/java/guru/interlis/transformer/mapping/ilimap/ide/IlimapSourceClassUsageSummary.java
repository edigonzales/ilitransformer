package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapSourceClassUsageSummary(
        List<String> inputIds,
        String sourceClass,
        List<String> aliases,
        List<IlimapSourceAttributeUsageSummary> attributes,
        List<IlimapSourceAttributeUsageSummary> roles,
        IlimapOverviewLocation location) {

    public IlimapSourceClassUsageSummary {
        inputIds = List.copyOf(inputIds);
        aliases = List.copyOf(aliases);
        attributes = List.copyOf(attributes);
        roles = List.copyOf(roles);
    }
}
