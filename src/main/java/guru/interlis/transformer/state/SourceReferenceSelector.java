package guru.interlis.transformer.state;

public record SourceReferenceSelector(
        String inputId, String basketId, String expectedSourceClass, String referencedSourceOid) {}
