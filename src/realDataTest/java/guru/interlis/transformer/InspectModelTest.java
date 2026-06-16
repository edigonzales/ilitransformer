package guru.interlis.transformer;

import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;

import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;

import java.util.Iterator;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("real-data")
class InspectModelTest {

    private static final String MODEL_DIR = "src/test/data/av/models/";
    private static final String DM01_MODEL = "DM01AVCH24LV95D";

    @Test
    void printDmavEnumValues() {
        inspectEnums(
                "DMAV_Bodenbedeckung_V1_1",
                MODEL_DIR + ";https://models.interlis.ch",
                "DMAV_Bodenbedeckung_V1_1.Bodenbedeckung",
                "Bodenbedeckung",
                new String[] {"Qualitaetsstandard", "Bodenbedeckungsart", "Objektstatus"});
        inspectEnums("DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "BoFlaeche", new String[] {
            "Qualitaet", "Art"
        });
        inspectEnums(
                "DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "Einzelpunkt", new String[] {"LageZuv"
                });
    }

    private void inspectEnums(
            String modelName, String modelDir, String topicName, String tableName, String[] attrNames) {
        IliModelService modelService = new IliModelService();
        IliModelCompileResult result = modelService.compileModel(modelName, modelDir);
        if (result.hasErrors()) {
            System.out.println(
                    "FAILED: " + modelName + " " + result.diagnostics().all());
            return;
        }
        TransferDescription td = result.transferDescription();
        var topic = td.getElement(topicName);
        if (topic instanceof Topic t) {
            Iterator<ch.interlis.ili2c.metamodel.Element> it = t.iterator();
            while (it.hasNext()) {
                ch.interlis.ili2c.metamodel.Element el = it.next();
                if (el instanceof Table tbl && tbl.getName().equals(tableName)) {
                    for (String wanted : attrNames) {
                        Iterator<Extendable> attrIt = tbl.getAttributes();
                        while (attrIt.hasNext()) {
                            Extendable attr = attrIt.next();
                            try {
                                String name = (String)
                                        attr.getClass().getMethod("getName").invoke(attr);
                                if (!name.equals(wanted)) continue;
                                Object domain =
                                        attr.getClass().getMethod("getDomain").invoke(attr);
                                System.out.println(modelName + "." + tableName + "." + wanted + " domain=" + domain);
                                if (domain != null
                                        && domain.getClass().getSimpleName().contains("Enumeration")) {
                                    try {
                                        Object[] values = (Object[]) domain.getClass()
                                                .getMethod("getValues")
                                                .invoke(domain);
                                        for (Object v : values) {
                                            String vname = (String) v.getClass()
                                                    .getMethod("getName")
                                                    .invoke(v);
                                            System.out.println("  ENUM: " + vname);
                                        }
                                    } catch (Exception e) {
                                        System.out.println("  (enum introspection error)");
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void printDmavBodenbedeckungTables() {
        inspectTable(
                "DMAV_Bodenbedeckung_V1_1",
                MODEL_DIR + ";https://models.interlis.ch",
                "DMAV_Bodenbedeckung_V1_1.Bodenbedeckung",
                "Bodenbedeckung");
        inspectTable(
                "DMAV_Bodenbedeckung_V1_1",
                MODEL_DIR + ";https://models.interlis.ch",
                "DMAV_Bodenbedeckung_V1_1.Bodenbedeckung",
                "Objektnummer");
        inspectTable(
                "DMAV_Bodenbedeckung_V1_1",
                MODEL_DIR + ";https://models.interlis.ch",
                "DMAV_Bodenbedeckung_V1_1.Bodenbedeckung",
                "Symbolposition");
        inspectTable(
                "DMAV_Bodenbedeckung_V1_1",
                MODEL_DIR + ";https://models.interlis.ch",
                "DMAV_Bodenbedeckung_V1_1.Bodenbedeckung",
                "Textposition");
    }

    @Test
    void printDm01BoFlaecheTables() {
        inspectTable("DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "BoFlaeche");
        inspectTable("DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "Gebaeudenummer");
        inspectTable("DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "GebaeudenummerPos");
        inspectTable("DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "Objektname");
        inspectTable("DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "ObjektnamePos");
        inspectTable("DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "BoFlaecheSymbol");
    }

    private void inspectTable(String modelName, String modelDir, String topicName, String tableName) {
        IliModelService modelService = new IliModelService();
        IliModelCompileResult result = modelService.compileModel(modelName, modelDir);
        if (result.hasErrors()) {
            System.out.println(
                    "FAILED: " + modelName + " " + result.diagnostics().all());
            return;
        }
        TransferDescription td = result.transferDescription();
        var topic = td.getElement(topicName);
        if (topic instanceof Topic t) {
            Iterator<ch.interlis.ili2c.metamodel.Element> it = t.iterator();
            while (it.hasNext()) {
                ch.interlis.ili2c.metamodel.Element el = it.next();
                if (el instanceof Table tbl && tbl.getName().equals(tableName)) {
                    System.out.println("Table: " + modelName + "." + tableName);
                    Iterator<Extendable> attrIt = tbl.getAttributes();
                    while (attrIt.hasNext()) {
                        Extendable attr = attrIt.next();
                        try {
                            String name = (String)
                                    attr.getClass().getMethod("getName").invoke(attr);
                            Object domain = null;
                            String domainStr = "";
                            try {
                                domain = attr.getClass().getMethod("getDomain").invoke(attr);
                            } catch (Exception ignored) {
                            }
                            if (domain != null) {
                                domainStr = " domain=" + domain.getClass().getSimpleName() + ":" + domain;
                                // For BagOfType / CompositionType get component type
                                try {
                                    if (domain.getClass().getSimpleName().contains("BagOf")
                                            || domain.getClass().getSimpleName().contains("Composition")) {
                                        Object comp = domain.getClass()
                                                .getMethod("getComponentType")
                                                .invoke(domain);
                                        if (comp != null) {
                                            domainStr += " component=" + ((Table) comp).getName();
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                                // For ReferenceType get table ref
                                try {
                                    if (domain.getClass().getSimpleName().contains("Reference")) {
                                        Object ref = domain.getClass()
                                                .getMethod("getReferred")
                                                .invoke(domain);
                                        if (ref != null) {
                                            domainStr += " refers=" + ((Table) ref).getName();
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                            System.out.println("  Attr: " + name + domainStr);
                        } catch (Exception e) {
                            System.out.println(
                                    "  Attr: (unknown " + attr.getClass().getSimpleName() + ")");
                        }
                    }
                }
            }
        }
    }

    @Test
    void printBodenbedeckungTables() {
        inspectTable("DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "BBNachfuehrung");
        inspectTable("DM01AVCH24LV95D", MODEL_DIR, "DM01AVCH24LV95D.Bodenbedeckung", "Einzelpunkt");
    }
}
