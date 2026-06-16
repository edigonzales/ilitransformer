package guru.interlis.transformer.state;

public record LookupKey(String inputId, String sourceClass, String attribute, CanonicalValue value) {}
