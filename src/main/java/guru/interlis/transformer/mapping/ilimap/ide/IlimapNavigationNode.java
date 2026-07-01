package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapNavigationNode(
        String nodeId,
        String kind,
        String label,
        String detail,
        IlimapOverviewLocation location,
        List<String> relatedNodeIds) {

    public IlimapNavigationNode {
        if (nodeId == null) {
            nodeId = "";
        }
        if (kind == null) {
            kind = "unknown";
        }
        if (label == null) {
            label = "";
        }
        if (relatedNodeIds == null) {
            relatedNodeIds = List.of();
        }
    }
}
