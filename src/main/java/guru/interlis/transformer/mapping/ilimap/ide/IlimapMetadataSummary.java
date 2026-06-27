package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapMetadataSummary(
        String direction, String roundtrip, String lossiness, IlimapOverviewLocation location) {}
