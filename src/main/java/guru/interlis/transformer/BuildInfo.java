package guru.interlis.transformer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Build metadata (name, version, full git commit hash) loaded from a generated
 * {@code build-info.properties} resource.
 *
 * <p>Used for the INTERLIS transfer sender identifier ({@link #senderId()}) and the
 * CLI version output ({@link #versionLine()}). When the generated resource is absent
 * (for example when running directly from an IDE without the {@code generateBuildInfo}
 * Gradle task), robust defaults are used so that callers never fail.
 */
public final class BuildInfo {

    private static final String RESOURCE = "/guru/interlis/transformer/build-info.properties";
    static final String DEFAULT_NAME = "ilitransformer";
    static final String DEFAULT_VERSION = "0.0.0-dev";
    static final String UNKNOWN_COMMIT = "unknown";

    private static final BuildInfo INSTANCE = load();

    private final String name;
    private final String version;
    private final String gitCommit;

    private BuildInfo(String name, String version, String gitCommit) {
        this.name = name;
        this.version = version;
        this.gitCommit = gitCommit;
    }

    public static BuildInfo get() {
        return INSTANCE;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public String gitCommit() {
        return gitCommit;
    }

    /** Sender identifier in the form {@code ilitransformer-<version>-<gitCommit>}. */
    public String senderId() {
        return name + "-" + version + "-" + gitCommit;
    }

    /** Human-readable version line for the CLI, e.g. {@code ilitransformer 0.1.0 (<hash>)}. */
    public String versionLine() {
        return name + " " + version + " (" + gitCommit + ")";
    }

    private static BuildInfo load() {
        Properties props = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // fall through to defaults
        }
        return fromProperties(props);
    }

    static BuildInfo fromProperties(Properties props) {
        String name = nonBlank(props.getProperty("name"), DEFAULT_NAME);
        String version = nonBlank(props.getProperty("version"), defaultVersion());
        String gitCommit = nonBlank(props.getProperty("gitCommit"), UNKNOWN_COMMIT);
        return new BuildInfo(name, version, gitCommit);
    }

    private static String defaultVersion() {
        Package pkg = BuildInfo.class.getPackage();
        String implVersion = pkg != null ? pkg.getImplementationVersion() : null;
        return nonBlank(implVersion, DEFAULT_VERSION);
    }

    private static String nonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
