package guru.interlis.transformer.engine;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom_j.Iom_jObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DeterministicOutputOrderTest {

    @Test
    void objectsSortedByClassThenOidWithinBasket() {
        Map<String, Map<String, List<ch.interlis.iom.IomObject>>> objects = new LinkedHashMap<>();

        Map<String, List<ch.interlis.iom.IomObject>> basket1 = new LinkedHashMap<>();
        List<ch.interlis.iom.IomObject> list1 = new ArrayList<>();
        list1.add(new Iom_jObject("Model.Topic.ClassB", "3"));
        list1.add(new Iom_jObject("Model.Topic.ClassA", "2"));
        list1.add(new Iom_jObject("Model.Topic.ClassA", "1"));
        basket1.put("Model.Topic::b1", list1);
        objects.put("out1", basket1);

        for (var basketEntry : basket1.entrySet()) {
            List<ch.interlis.iom.IomObject> sorted = new ArrayList<>(basketEntry.getValue());
            sorted.sort(Comparator.comparing(ch.interlis.iom.IomObject::getobjecttag)
                    .thenComparing(ch.interlis.iom.IomObject::getobjectoid));

            assertThat(sorted).hasSize(3);
            assertThat(sorted.get(0).getobjecttag()).isEqualTo("Model.Topic.ClassA");
            assertThat(sorted.get(0).getobjectoid()).isEqualTo("1");
            assertThat(sorted.get(1).getobjecttag()).isEqualTo("Model.Topic.ClassA");
            assertThat(sorted.get(1).getobjectoid()).isEqualTo("2");
            assertThat(sorted.get(2).getobjecttag()).isEqualTo("Model.Topic.ClassB");
            assertThat(sorted.get(2).getobjectoid()).isEqualTo("3");
        }
    }

    @Test
    void sortIsDeterministicWhenRepeated() {
        List<ch.interlis.iom.IomObject> list1 = new ArrayList<>();
        list1.add(new Iom_jObject("Model.Topic.ClassB", "3"));
        list1.add(new Iom_jObject("Model.Topic.ClassA", "2"));
        list1.add(new Iom_jObject("Model.Topic.ClassA", "1"));

        List<ch.interlis.iom.IomObject> list2 = new ArrayList<>();
        list2.add(new Iom_jObject("Model.Topic.ClassA", "1"));
        list2.add(new Iom_jObject("Model.Topic.ClassB", "3"));
        list2.add(new Iom_jObject("Model.Topic.ClassA", "2"));

        Comparator<ch.interlis.iom.IomObject> cmp = Comparator.comparing(ch.interlis.iom.IomObject::getobjecttag)
                .thenComparing(ch.interlis.iom.IomObject::getobjectoid);

        var sorted1 = new ArrayList<>(list1);
        sorted1.sort(cmp);
        var sorted2 = new ArrayList<>(list2);
        sorted2.sort(cmp);

        for (int i = 0; i < 3; i++) {
            assertThat(sorted1.get(i).getobjectoid()).isEqualTo(sorted2.get(i).getobjectoid());
            assertThat(sorted1.get(i).getobjecttag()).isEqualTo(sorted2.get(i).getobjecttag());
        }
    }
}
