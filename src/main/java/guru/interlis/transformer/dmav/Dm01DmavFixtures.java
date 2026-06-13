package guru.interlis.transformer.dmav;

import guru.interlis.transformer.model.ExtractionRequest;

import java.nio.file.Path;
import java.util.List;

public final class Dm01DmavFixtures {

    private static final List<String> LFP3_TARGET_CLASSES =
            List.of("LFP3Nachfuehrung", "LFP3", "LFP3Pos", "LFP3Symbol");
    private static final List<String> HFP3_TARGET_CLASSES =
            List.of("HFP3Nachfuehrung", "HFP3", "HFP3Pos");
    private static final List<String> BB_DM01_TARGET_CLASSES =
            List.of("Bodenbedeckung.BBNachfuehrung", "Bodenbedeckung.BoFlaeche",
                    "Bodenbedeckung.ProjBoFlaeche", "Bodenbedeckung.Gebaeudenummer",
                    "Bodenbedeckung.GebaeudenummerPos", "Bodenbedeckung.Objektname",
                    "Bodenbedeckung.ObjektnamePos", "Bodenbedeckung.BoFlaecheSymbol",
                    "Bodenbedeckung.Einzelpunkt");
    private static final List<String> BB_DMAV_TARGET_CLASSES =
            List.of("Bodenbedeckung.BBNachfuehrung", "Bodenbedeckung.Bodenbedeckung",
                    "Bodenbedeckung.Messpunkt");
    private static final List<String> EO_DM01_TARGET_CLASSES =
            List.of("Einzelobjekte.EONachfuehrung", "Einzelobjekte.Einzelobjekt",
                    "Einzelobjekte.Flaechenelement", "Einzelobjekte.Linienelement",
                    "Einzelobjekte.Punktelement", "Einzelobjekte.Objektname",
                    "Einzelobjekte.Objektnummer", "Einzelobjekte.Einzelpunkt");
    private static final List<String> EO_DMAV_TARGET_CLASSES =
            List.of("Einzelobjekte.EONachfuehrung", "Einzelobjekte.Einzelobjekt",
                    "Einzelobjekte.Messpunkt");

    public static final TopicFixtureSpec LFP3 = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_LFP3,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_LFP3_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            LFP3_TARGET_CLASSES,
            LFP3_TARGET_CLASSES,
            2,
            200,
            true
    );

    public static final TopicFixtureSpec HFP3 = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_HFP3,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_LFP3_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            HFP3_TARGET_CLASSES,
            HFP3_TARGET_CLASSES,
            2,
            200,
            true
    );

    public static final TopicFixtureSpec BB = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_BB,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_BB_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            BB_DM01_TARGET_CLASSES,
            BB_DMAV_TARGET_CLASSES,
            2,
            200,
            true
    );

    public static final TopicFixtureSpec EO = new TopicFixtureSpec(
            Dm01DmavPaths.TOPIC_EO,
            Dm01DmavPaths.DM01_MODEL,
            Dm01DmavPaths.DMAV_EO_MODEL,
            Dm01DmavPaths.DMAV_UMBRELLA_MODEL,
            EO_DM01_TARGET_CLASSES,
            EO_DMAV_TARGET_CLASSES,
            2,
            200,
            true
    );

    private Dm01DmavFixtures() {}

    public static TopicFixtureSpec topic(String topicId) {
        return switch (topicId) {
            case Dm01DmavPaths.TOPIC_LFP3 -> LFP3;
            case Dm01DmavPaths.TOPIC_HFP3 -> HFP3;
            case Dm01DmavPaths.TOPIC_BB -> BB;
            case Dm01DmavPaths.TOPIC_EO -> EO;
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
            boolean includeBidirectional
    ) {

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
                    targetDir
            );
        }

        public ExtractionRequest dmavExtractionRequest(Path targetDir) {
            return new ExtractionRequest(
                    dmavSeedClasses,
                    Dm01DmavPaths.defaultModelDirs(),
                    maxDepth,
                    maxObjects,
                    includeBidirectional,
                    targetDir
            );
        }
    }
}
