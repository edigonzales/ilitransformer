package guru.interlis.transformer.state;

import ch.interlis.iom.IomObject;
import java.util.Optional;

public record SourceRecord(
        String sourceFileId,
        String sourceBasketId,
        String sourceClass,
        IomObject sourceObject,
        ParentContext parentContext
) {
    public SourceRecord(String sourceFileId, String sourceBasketId,
                        String sourceClass, IomObject sourceObject) {
        this(sourceFileId, sourceBasketId, sourceClass, sourceObject, null);
    }

    public Optional<ParentContext> parentContextOptional() {
        return Optional.ofNullable(parentContext);
    }

    public record ParentContext(String parentOid, String parentClass) {}
}
