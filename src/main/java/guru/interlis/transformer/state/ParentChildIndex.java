package guru.interlis.transformer.state;

import java.util.List;

public interface ParentChildIndex {
    void index(String sourceClass, String referenceAttribute, String parentOid, SourceRecord child);

    List<SourceRecord> children(String sourceClass, String referenceAttribute, String parentOid);
}
