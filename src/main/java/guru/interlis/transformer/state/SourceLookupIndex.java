package guru.interlis.transformer.state;

public interface SourceLookupIndex {

    String OBJECT_OID_ATTRIBUTE = "__objectOid";

    void index(SourceRecord record);

    java.util.List<SourceRecord> lookup(LookupKey key);
}
