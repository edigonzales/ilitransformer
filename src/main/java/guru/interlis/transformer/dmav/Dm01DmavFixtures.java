package guru.interlis.transformer.dmav;

import guru.interlis.transformer.model.ExtractionRequest;

import java.nio.file.Path;
import java.util.List;

public final class Dm01DmavFixtures {

    private static final List<String> LFP3_TARGET_CLASSES =
            List.of("LFP3Nachfuehrung", "LFP3", "LFP3Pos", "LFP3Symbol");
    private static final List<String> HFP3_TARGET_CLASSES = List.of("HFP3Nachfuehrung", "HFP3", "HFP3Pos");
    private static final List<String> BB_DM01_TARGET_CLASSES = List.of(
            "Bodenbedeckung.BBNachfuehrung",
            "Bodenbedeckung.BoFlaeche",
            "Bodenbedeckung.ProjBoFlaeche",
            "Bodenbedeckung.Gebaeudenummer",
            "Bodenbedeckung.GebaeudenummerPos",
            "Bodenbedeckung.Objektname",
            "Bodenbedeckung.ObjektnamePos",
            "Bodenbedeckung.BoFlaecheSymbol",
            "Bodenbedeckung.Einzelpunkt");
    private static final List<String> BB_DMAV_TARGET_CLASSES =
            List.of("Bodenbedeckung.BBNachfuehrung", "Bodenbedeckung.Bodenbedeckung", "Bodenbedeckung.Messpunkt");
    private static final List<String> EO_DM01_TARGET_CLASSES = List.of(
            "Einzelobjekte.EONachfuehrung", "Einzelobjekte.Einzelobjekt",
            "Einzelobjekte.Flaechenelement", "Einzelobjekte.Linienelement",
            "Einzelobjekte.Punktelement", "Einzelobjekte.Objektname",
            "Einzelobjekte.Objektnummer", "Einzelobjekte.Einzelpunkt");
    private static final List<String> EO_DMAV_TARGET_CLASSES =
            List.of("Einzelobjekte.EONachfuehrung", "Einzelobjekte.Einzelobjekt", "Einzelobjekte.Messpunkt");
    private static final List<String> GS_DM01_TARGET_CLASSES = List.of(
            "Liegenschaften.LSNachfuehrung", "Liegenschaften.Grenzpunkt",
            "Liegenschaften.Grundstueck", "Liegenschaften.Liegenschaft");
    private static final List<String> GS_DMAV_TARGET_CLASSES = List.of(
            "Grundstuecke.GSNachfuehrung", "Grundstuecke.Grenzpunkt",
            "Grundstuecke.Grundstueck", "Grundstuecke.Liegenschaft");
    private static final List<String> NOMENKLATUR_DM01_TARGET_CLASSES = List.of(
            "Nomenklatur.NKNachfuehrung", "Nomenklatur.Flurname",
            "Nomenklatur.Flurname_Geometrie", "Nomenklatur.FlurnamePos",
            "Nomenklatur.Ortsname", "Nomenklatur.OrtsnamePos",
            "Nomenklatur.Gelaendename", "Nomenklatur.GelaendenamePos");
    private static final List<String> NOMENKLATUR_DMAV_TARGET_CLASSES = List.of(
            "Nomenklatur.NKNachfuehrung", "Nomenklatur.Flurname",
            "Nomenklatur.Ortsname", "Nomenklatur.Gelaendename");
    private static final List<String> TOLERANZSTUFEN_DM01_TARGET_CLASSES =
            List.of("TSEinteilung.Toleranzstufe", "TSEinteilung.ToleranzstufePos");
    private static final List<String> TOLERANZSTUFEN_DMAV_TARGET_CLASSES =
            List.of("Toleranzstufen.TSNachfuehrung", "Toleranzstufen.Toleranzstufe");
    private static final List<String> GA_DM01_TARGET_CLASSES = List.of(
            "Gebaeudeadressen.GEBNachfuehrung",
            "Gebaeudeadressen.Lokalisation",
            "Gebaeudeadressen.LokalisationsName",
            "Gebaeudeadressen.LokalisationsNamePos",
            "Gebaeudeadressen.BenanntesGebiet",
            "Gebaeudeadressen.Strassenstueck",
            "Gebaeudeadressen.Gebaeudeeingang",
            "Gebaeudeadressen.HausnummerPos",
            "Gebaeudeadressen.GebaeudeName",
            "Gebaeudeadressen.GebaeudeNamePos",
            "Gebaeudeadressen.GebaeudeBeschreibung");
    private static final List<String> GA_DMAV_TARGET_CLASSES = List.of(
            "Gebaeudeadressen.GANachfuehrung", "Gebaeudeadressen.Lokalisation", "Gebaeudeadressen.Gebaeudeeingang");
    private static final List<String> HOHEITSGRENZEN_DM01_TARGET_CLASSES = List.of(
            "Gemeindegrenzen.GEMNachfuehrung",
            "Gemeindegrenzen.Gemeinde",
            "Gemeindegrenzen.ProjGemeindegrenze",
            "Gemeindegrenzen.Gemeindegrenze",
            "Bezirksgrenzen.Bezirksgrenzabschnitt",
            "Kantonsgrenzen.Kantonsgrenzabschnitt");
    private static final List<String> HOHEITSGRENZEN_DMAV_TARGET_CLASSES = List.of(
            "HoheitsgrenzenAV.HHGNachfuehrung",
            "HoheitsgrenzenAV.Gemeinde",
            "HoheitsgrenzenAV.ProjGemeindegrenzabschnitt",
            "HoheitsgrenzenAV.Gemeindegrenze",
            "HoheitsgrenzenAV.Bezirksgrenzabschnitt",
            "HoheitsgrenzenAV.Kantonsgrenzabschnitt");
    private static final List<String> FIXPUNKTELV_DM01_TARGET_CLASSES =
            List.of("FixpunkteKategorie1.LFP1", "FixpunkteKategorie1.HFP1");
    private static final List<String> FIXPUNKTELV_DMAV_TARGET_CLASSES = List.of("FixpunkteLV.LFP1", "FixpunkteLV.HFP1");
    private static final List<String> FPDS2_DM01_TARGET_CLASSES = List.of(
            "FixpunkteKategorie2.LFP2", "FixpunkteKategorie2.HFP2",
            "FixpunkteKategorie2.LFP2Nachfuehrung", "FixpunkteKategorie2.HFP2Nachfuehrung");
    private static final List<String> FPDS2_DMAV_TARGET_CLASSES =
            List.of("FPDS2.Fixpunkt", "FPDS2.FixpunktVersion", "FPDS2.FixpunkteNachfuehrung");
    private static final List<String> HOHEITSGRENZENLV_DM01_TARGET_CLASSES =
            List.of("Landesgrenzen.Landesgrenzabschnitt");
    private static final List<String> HOHEITSGRENZENLV_DMAV_TARGET_CLASSES = List.of("HoheitsgrenzenLV.Landesgrenze");
    private static final List<String> PLZORTSCHAFT_DM01_TARGET_CLASSES =
            List.of("PLZOrtschaft.Ortschaft", "PLZOrtschaft.OrtschaftsName", "PLZOrtschaft.PLZ6");
    private static final List<String> PLZORTSCHAFT_DMAV_TARGET_CLASSES =
            List.of("PLZ_Ortschaft.Ortschaft", "PLZ_Ortschaft.PLZ");
    private static final List<String> DBV_DM01_TARGET_CLASSES = List.of(
            "Rutschgebiete.Rutschung", "Rutschgebiete.RutschungPos");
    private static final List<String> DBV_DMAV_TARGET_CLASSES = List.of(
            "DauerndeBodenverschiebungen.DBVNachfuehrung",
            "DauerndeBodenverschiebungen.DauerndeBodenverschiebung");

    public static final TopicFixtureSpec LFP3 = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_LFP3,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_LFP3_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            LFP3_TARGET_CLASSES,
            LFP3_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec HFP3 = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_HFP3,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_LFP3_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            HFP3_TARGET_CLASSES,
            HFP3_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec BB = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_BB,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_BB_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            BB_DM01_TARGET_CLASSES,
            BB_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec EO = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_EO,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_EO_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            EO_DM01_TARGET_CLASSES,
            EO_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec GS = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_GS,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_GS_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            GS_DM01_TARGET_CLASSES,
            GS_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec NOMENKLATUR = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_NOMENKLATUR,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_NOMENKLATUR_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            NOMENKLATUR_DM01_TARGET_CLASSES,
            NOMENKLATUR_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec TOLERANZSTUFEN = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_TOLERANZSTUFEN,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_TOLERANZSTUFEN_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            TOLERANZSTUFEN_DM01_TARGET_CLASSES,
            TOLERANZSTUFEN_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec GA = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_GA,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_GA_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            GA_DM01_TARGET_CLASSES,
            GA_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec HOHEITSGRENZEN = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_HOHEITSGRENZEN,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_HOHEITSGRENZEN_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            HOHEITSGRENZEN_DM01_TARGET_CLASSES,
            HOHEITSGRENZEN_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec FIXPUNKTELV = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_FIXPUNKTELV,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_FIXPUNKTELV_MODEL,
            Dm01DmavPaths.DMAV_FIXPUNKTELV_MODEL,
            FIXPUNKTELV_DM01_TARGET_CLASSES,
            FIXPUNKTELV_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec FPDS2 = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_FPDS2,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_FPDS2_MODEL,
            Dm01DmavPaths.DMAV_FPDS2_MODEL,
            FPDS2_DM01_TARGET_CLASSES,
            FPDS2_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec HOHEITSGRENZENLV = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_HOHEITSGRENZENLV,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_HOHEITSGRENZENLV_MODEL,
            Dm01DmavPaths.DMAV_HOHEITSGRENZENLV_MODEL,
            HOHEITSGRENZENLV_DM01_TARGET_CLASSES,
            HOHEITSGRENZENLV_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec PLZORTSCHAFT = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_PLZORTSCHAFT,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_PLZORTSCHAFT_MODEL,
            Dm01DmavPaths.DMAV_PLZORTSCHAFT_MODEL,
            PLZORTSCHAFT_DM01_TARGET_CLASSES,
            PLZORTSCHAFT_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    public static final TopicFixtureSpec DBV = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_DBV,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_DBV_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            DBV_DM01_TARGET_CLASSES,
            DBV_DMAV_TARGET_CLASSES,
            2,
            200,
            true);

    private Dm01DmavFixtures() {}

    public static TopicFixtureSpec topic(String topicId) {
        return switch (topicId) {
            case Dm01DmavPaths.TOPIC_LFP3 -> LFP3;
            case Dm01DmavPaths.TOPIC_HFP3 -> HFP3;
            case Dm01DmavPaths.TOPIC_BB -> BB;
            case Dm01DmavPaths.TOPIC_EO -> EO;
            case Dm01DmavPaths.TOPIC_GS -> GS;
            case Dm01DmavPaths.TOPIC_NOMENKLATUR -> NOMENKLATUR;
            case Dm01DmavPaths.TOPIC_TOLERANZSTUFEN -> TOLERANZSTUFEN;
            case Dm01DmavPaths.TOPIC_GA -> GA;
            case Dm01DmavPaths.TOPIC_HOHEITSGRENZEN -> HOHEITSGRENZEN;
            case Dm01DmavPaths.TOPIC_FIXPUNKTELV -> FIXPUNKTELV;
            case Dm01DmavPaths.TOPIC_FPDS2 -> FPDS2;
            case Dm01DmavPaths.TOPIC_HOHEITSGRENZENLV -> HOHEITSGRENZENLV;
            case Dm01DmavPaths.TOPIC_PLZORTSCHAFT -> PLZORTSCHAFT;
            case Dm01DmavPaths.TOPIC_DBV -> DBV;
            default -> throw new IllegalArgumentException("Unknown DM01/DMAV topic: " + topicId);
        };
    }

    public static ExtractionRequest lfp3Dm01ExtractionRequest(Path targetDir) {
        return LFP3.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest lfp3DmavExtractionRequest(Path targetDir) {
        return LFP3.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest hfp3Dm01ExtractionRequest(Path targetDir) {
        return HFP3.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest hfp3DmavExtractionRequest(Path targetDir) {
        return HFP3.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest bbDm01ExtractionRequest(Path targetDir) {
        return BB.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest bbDmavExtractionRequest(Path targetDir) {
        return BB.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest eoDm01ExtractionRequest(Path targetDir) {
        return EO.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest eoDmavExtractionRequest(Path targetDir) {
        return EO.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest gsDm01ExtractionRequest(Path targetDir) {
        return GS.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest gsDmavExtractionRequest(Path targetDir) {
        return GS.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest nomenklaturDm01ExtractionRequest(Path targetDir) {
        return NOMENKLATUR.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest nomenklaturDmavExtractionRequest(Path targetDir) {
        return NOMENKLATUR.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest toleranzstufenDm01ExtractionRequest(Path targetDir) {
        return TOLERANZSTUFEN.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest toleranzstufenDmavExtractionRequest(Path targetDir) {
        return TOLERANZSTUFEN.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest gaDm01ExtractionRequest(Path targetDir) {
        return GA.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest gaDmavExtractionRequest(Path targetDir) {
        return GA.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest hoheitsgrenzenDm01ExtractionRequest(Path targetDir) {
        return HOHEITSGRENZEN.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest hoheitsgrenzenDmavExtractionRequest(Path targetDir) {
        return HOHEITSGRENZEN.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest fixpunktelvDm01ExtractionRequest(Path targetDir) {
        return FIXPUNKTELV.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest fixpunktelvDmavExtractionRequest(Path targetDir) {
        return FIXPUNKTELV.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest fpds2Dm01ExtractionRequest(Path targetDir) {
        return FPDS2.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest fpds2DmavExtractionRequest(Path targetDir) {
        return FPDS2.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest hoheitsgrenzenlvDm01ExtractionRequest(Path targetDir) {
        return HOHEITSGRENZENLV.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest hoheitsgrenzenlvDmavExtractionRequest(Path targetDir) {
        return HOHEITSGRENZENLV.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest plzortschaftDm01ExtractionRequest(Path targetDir) {
        return PLZORTSCHAFT.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest plzortschaftDmavExtractionRequest(Path targetDir) {
        return PLZORTSCHAFT.dmavExtractionRequest(targetDir);
    }

    public static ExtractionRequest dbvDm01ExtractionRequest(Path targetDir) {
        return DBV.dm01ExtractionRequest(targetDir);
    }

    public static ExtractionRequest dbvDmavExtractionRequest(Path targetDir) {
        return DBV.dmavExtractionRequest(targetDir);
    }

    public static boolean isLfp3RelevantClass(String className) {
        if (className == null) {
            return false;
        }
        String upper = className.toUpperCase();
        return upper.contains("LFP3") || upper.contains("FIXPUNKT");
    }

    public record TopicFixtureSpec(
            String topicId,
            String dm01Model,
            String dmavMinimalModel,
            String dmavRealExtractModel,
            List<String> dm01SeedClasses,
            List<String> dmavSeedClasses,
            int maxDepth,
            int maxObjects,
            boolean includeBidirectional) {

        public Path fixtureDir() {
            return Dm01DmavPaths.fixtureDir(topicId);
        }

        public Path dm01MinimalFixture() {
            return Dm01DmavPaths.dm01MinimalFixture(topicId);
        }

        public Path dmavMinimalFixture() {
            return Dm01DmavPaths.dmavMinimalFixture(topicId);
        }

        public Path dm01RealExtractFixture() {
            return Dm01DmavPaths.dm01RealExtractFixture(topicId);
        }

        public Path dmavRealExtractFixture() {
            return Dm01DmavPaths.dmavRealExtractFixture(topicId);
        }

        public Path dm01ToDmavProfile() {
            return Dm01DmavPaths.dm01ToDmavProfile(topicId);
        }

        public Path dmavToDm01Profile() {
            return Dm01DmavPaths.dmavToDm01Profile(topicId);
        }

        public ExtractionRequest dm01ExtractionRequest(Path targetDir) {
            return new ExtractionRequest(
                    dm01SeedClasses,
                    Dm01DmavPaths.localModelDirs(),
                    maxDepth,
                    maxObjects,
                    includeBidirectional,
                    targetDir);
        }

        public ExtractionRequest dmavExtractionRequest(Path targetDir) {
            return new ExtractionRequest(
                    dmavSeedClasses,
                    Dm01DmavPaths.defaultModelDirs(),
                    maxDepth,
                    maxObjects,
                    includeBidirectional,
                    targetDir);
        }
    }
}
