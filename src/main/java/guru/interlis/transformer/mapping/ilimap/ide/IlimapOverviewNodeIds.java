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

    public static String ruleTarget(String ruleId) {
        return "rule:" + ruleId + ":target";
    }

    public static String ruleTargetAttribute(String ruleId, String attribute) {
        return "rule:" + ruleId + ":target:" + attribute;
    }

    public static String ruleSource(String ruleId, String alias) {
        return "rule:" + ruleId + ":source:" + alias;
    }

    public static String ruleSourceMember(String ruleId, String alias, String member) {
        return "rule:" + ruleId + ":source:" + alias + ":member:" + member;
    }

    public static String ruleAssignment(String ruleId, String targetAttribute) {
        return "rule:" + ruleId + ":assign:" + targetAttribute;
    }

    public static String ruleBag(String ruleId, String bagId) {
        return "rule:" + ruleId + ":bag:" + bagId;
    }

    public static String ruleRef(String ruleId, String refId) {
        return "rule:" + ruleId + ":ref:" + refId;
    }

    public static String ruleLoss(String ruleId, int index) {
        return "rule:" + ruleId + ":loss:" + index;
    }

    private IlimapOverviewNodeIds() {}
}
