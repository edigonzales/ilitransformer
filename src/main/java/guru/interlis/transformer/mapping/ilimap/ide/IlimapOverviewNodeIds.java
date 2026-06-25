package guru.interlis.transformer.mapping.ilimap.ide;

public final class IlimapOverviewNodeIds {

    public static String input(String inputId) {
        return "input:" + inputId;
    }

    public static String output(String outputId) {
        return "output:" + outputId;
    }

    public static String enumMap(String enumMapId) {
        return "enum:" + enumMapId;
    }

    public static String rule(String ruleId) {
        return "rule:" + ruleId;
    }

    private IlimapOverviewNodeIds() {}
}
