package guru.interlis.transformer.state;

import ch.interlis.iom.IomObject;

public record SourceRecord(
        String sourceFileId,
        String sourceBasketId,
        String sourceClass,
        IomObject sourceObject
) {
}
