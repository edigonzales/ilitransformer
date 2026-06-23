package guru.interlis.transformer.app;

import guru.interlis.transformer.BuildInfo;

import picocli.CommandLine.IVersionProvider;

/** Supplies the CLI version line from {@link BuildInfo}. */
public final class BuildInfoVersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] {BuildInfo.get().versionLine()};
    }
}
