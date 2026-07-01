package guru.interlis.transformer.mapping.ilimap.ide;

import java.util.List;

public record IlimapNavigationTarget(
        boolean available,
        String message,
        String nodeId,
        IlimapOverviewLocation location,
        List<IlimapNavigationNode> related) {

    public IlimapNavigationTarget {
        if (message == null) {
            message = "";
        }
        if (nodeId == null) {
            nodeId = "";
        }
        if (related == null) {
            related = List.of();
        }
    }

    public static IlimapNavigationTarget unavailable(String nodeId, String message) {
        return new IlimapNavigationTarget(false, message, nodeId, null, List.of());
    }
}
