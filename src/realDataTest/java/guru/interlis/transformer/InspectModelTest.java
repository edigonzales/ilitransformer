package guru.interlis.transformer;

import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

@Tag("real-data")
class InspectModelTest {

    private static final String MODEL_DIR = "src/test/data/av/models/";
    private static final String DM01_MODEL = "DM01AVCH24LV95D";

    @Test
    void printDmavEnumValues() {
        inspectEnums("DMAV_Bodenbedeckung_V1_1", MODEL_DIR + ";https://models.interlis.ch",
                "DMAV_Bodenbedeckung_V1_1.Bodenbedeckung", "Bodenbedeckung",
                new String[]{"Qualitaetsstandard", "Bodenbedeckungsart", "Objektstatus"});
        inspectEnums("DM01AVCH24LV95D", MODEL_DIR,
                "DM01AVCH24LV95D.Bodenbedeckung", "BoFlaeche",
                new String[]{"Qualitaet", "Art"});
        inspectEnums("DM01AVCH24LV95D", MODEL_DIR,
                "DM01AVCH24LV95D.Bodenbedeckung", "Einzelpunkt",
                new String[]{"LageZuv"});
    }

    private void inspectEnums(String modelName, String modelDir, String topicName, String tableName, String[] attrNames) {
        IliModelService modelService = new IliModelService();
        IliModelCompileResult result = modelService.compileModel(modelName, modelDir);
        if (result.hasErrors()) {
            System.out.println("FAILED: " + modelName + " " + result.diagnostics().all());
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
                                String name = (String) attr.getClass().getMethod("getName").invoke(attr);
                                if (!name.equals(wanted)) continue;
                                Object domain = attr.getClass().getMethod("getDomain").invoke(attr);
                                System.out.println(modelName + "." + tableName + "." + wanted + " domain=" + domain);
                                if (domain != null && domain.getClass().getSimpleName().contains("Enumeration")) {
                                    try {
                                        Object[] values = (Object[]) domain.getClass().getMethod("getValues").invoke(domain);
                                        for (Object v : values) {
                                            String vname = (String) v.getClass().getMethod("getName").invoke(v);
                                            System.out.println("  ENUM: " + vname);
                                        }
                                    } catch (Exception e) {
                                        System.out.println("  (enum introspection error)");
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    void printDmavBodenbedeckungTables() {
        IliModelService modelService = new IliModelService();
        IliModelCompileResult result = modelService.compileModel("DMAV_Bodenbedeckung_V1_1",
                MODEL_DIR + ";https://models.interlis.ch");
        if (result.hasErrors()) {
            throw new RuntimeException("Model compile failed: " + result.diagnostics().all());
        }
        TransferDescription td = result.transferDescription();

        var topic = td.getElement("DMAV_Bodenbedeckung_V1_1.Bodenbedeckung");
        if (topic instanceof Topic t) {
            Iterator<ch.interlis.ili2c.metamodel.Element> it = t.iterator();
            while (it.hasNext()) {
                ch.interlis.ili2c.metamodel.Element el = it.next();
                if (el instanceof Table tbl) {
                    System.out.println("Table: " + tbl.getName());
                    Iterator<Extendable> attrIt = tbl.getAttributes();
                    while (attrIt.hasNext()) {
                        Extendable attr = attrIt.next();
                        try {
                            java.lang.reflect.Method m = attr.getClass().getMethod("getName");
                            String name = (String) m.invoke(attr);
                            String domain = "";
                            try {
                                java.lang.reflect.Method dm = attr.getClass().getMethod("getDomain");
                                Object d = dm.invoke(attr);
                                if (d != null) domain = " domain=" + d.toString();
                            } catch (Exception ignored) {}
                            System.out.println("  Attr: " + name + domain);
                        } catch (Exception e) {
                            System.out.println("  Attr: (unknown " + attr.getClass().getSimpleName() + ")");
                        }
                    }
                }
            }
        }
    }

    @Test
    void printBodenbedeckungTables() {
        IliModelService modelService = new IliModelService();
        IliModelCompileResult result = modelService.compileModel(DM01_MODEL, MODEL_DIR);
        if (result.hasErrors()) {
            throw new RuntimeException("Model compile failed");
        }
        TransferDescription td = result.transferDescription();

        var topic = td.getElement("DM01AVCH24LV95D.Bodenbedeckung");
        if (topic instanceof Topic t) {
            Iterator<ch.interlis.ili2c.metamodel.Element> it = t.iterator();
            while (it.hasNext()) {
                ch.interlis.ili2c.metamodel.Element el = it.next();
                if (el instanceof Table tbl) {
                    System.out.println("Table: " + tbl.getName());
                    Iterator<Extendable> attrIt = tbl.getAttributes();
                    while (attrIt.hasNext()) {
                        Extendable attr = attrIt.next();
                        try {
                            java.lang.reflect.Method m = attr.getClass().getMethod("getName");
                            String name = (String) m.invoke(attr);
                            String domain = "";
                            try {
                                java.lang.reflect.Method dm = attr.getClass().getMethod("getDomain");
                                Object d = dm.invoke(attr);
                                if (d != null) domain = " domain=" + d.toString();
                            } catch (Exception ignored) {}
                            System.out.println("  Attr: " + name + domain);
                        } catch (Exception e) {
                            System.out.println("  Attr: (unknown " + attr.getClass().getSimpleName() + ")");
                        }
                    }
                }
            }
        }
    }
}
