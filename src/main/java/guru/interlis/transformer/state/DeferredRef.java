package guru.interlis.transformer.state;

public record DeferredRef(
        String ownerTargetClass,
        String ownerTargetOid,
        String ownerAttribute,
        String sourceClass,
        String sourceReferencedOid,
        String sourceFileId,
        String sourceBasketId,
        String expectedTargetClass
) {
}
