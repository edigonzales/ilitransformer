package guru.interlis.transformer.compare;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.loss.LossEvent;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;

import java.util.List;

import org.junit.jupiter.api.Test;

class SemanticTransferComparatorTest {

    private final SemanticTransferComparator comparator = new SemanticTransferComparator();

    @Test
    void matchesByBusinessKeyAndAcceptsNumericTolerance() {
        IomObject left = point("p1", "LFP-1", "2600000.000", "1200000.000");
        IomObject right = point("p2", "LFP-1", "2600000.0004", "1200000.0004");

        ComparisonProfile profile = ComparisonProfile.builder()
                .businessKey("LFP3", "Identifikator")
                .numericTolerance(0.001)
                .ignore("TransientOid")
                .build();

        ComparisonReport report = comparator.compare(List.of(left), List.of(right), profile);

        assertThat(report.equivalent()).as("%s", report.issues()).isTrue();
        assertThat(report.matchedObjectCount()).isEqualTo(1);
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void reportsScalarDifferencesOutsideTolerance() {
        IomObject left = point("p1", "LFP-1", "2600000.0", "1200000.0");
        IomObject right = point("p2", "LFP-1", "2600000.5", "1200000.0");

        ComparisonProfile profile = ComparisonProfile.builder()
                .businessKey("LFP3", "Identifikator")
                .numericTolerance(0.001)
                .build();

        ComparisonReport report = comparator.compare(List.of(left), List.of(right), profile);

        assertThat(report.equivalent()).isFalse();
        assertThat(report.errors()).anySatisfy(issue -> assertThat(issue.path()).contains("C1"));
    }

    @Test
    void comparesNestedStructuresAndReferences() {
        Iom_jObject left = point("p1", "LFP-1", "2600000.0", "1200000.0");
        Iom_jObject right = point("p2", "LFP-1", "2600000.0", "1200000.0");
        left.addattrobj("Entstehung", new Iom_jObject("REF", "nf1"));
        right.addattrobj("Entstehung", new Iom_jObject("REF", "nf1"));

        Iom_jObject leftText = new Iom_jObject("DMAVTYM_Grafik_V1_0.Textposition", null);
        leftText.setattrvalue("Ori", "90.0");
        left.addattrobj("Textposition", leftText);
        Iom_jObject rightText = new Iom_jObject("DMAVTYM_Grafik_V1_0.Textposition", null);
        rightText.setattrvalue("Ori", "90.0");
        right.addattrobj("Textposition", rightText);

        ComparisonProfile profile = ComparisonProfile.builder()
                .businessKey("LFP3", "Identifikator")
                .ignore("TransientOid")
                .build();

        ComparisonReport report = comparator.compare(List.of(left), List.of(right), profile);

        assertThat(report.equivalent()).as("%s", report.issues()).isTrue();
    }

    @Test
    void requiresExpectedLossReasons() {
        IomObject left = point("p1", "LFP-1", "2600000.0", "1200000.0");
        IomObject right = point("p2", "LFP-1", "2600000.0", "1200000.0");

        ComparisonProfile profile = ComparisonProfile.builder()
                .businessKey("LFP3", "Identifikator")
                .ignore("TransientOid")
                .expectedLossReasonCode("DMAV_ONLY")
                .build();

        ComparisonReport report = comparator.compare(
                List.of(left),
                List.of(right),
                profile,
                List.of(new LossEvent("lfp3", "Source", "1", "p.LFPArt", "DMAV_ONLY", "DMAV-only field")));

        assertThat(report.equivalent()).as("%s", report.issues()).isTrue();
        assertThat(report.observedLossReasonCodes()).contains("DMAV_ONLY");
    }

    private Iom_jObject point(String oid, String identifikator, String c1, String c2) {
        Iom_jObject point = new Iom_jObject("DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3", oid);
        point.setattrvalue("Identifikator", identifikator);
        point.setattrvalue("TransientOid", oid);
        Iom_jObject geom = new Iom_jObject("COORD", null);
        geom.setattrvalue("C1", c1);
        geom.setattrvalue("C2", c2);
        point.addattrobj("Geometrie", geom);
        return point;
    }
}
