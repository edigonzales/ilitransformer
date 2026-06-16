package guru.interlis.transformer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class FixtureUpdateSupport {

    private static final String UPDATE_FIXTURES_PROPERTY = "updateFixtures";

    private FixtureUpdateSupport() {}

    static boolean syncCheckedInFixture(Path generatedFixture, Path checkedInFixture) throws IOException {
        if (!Boolean.parseBoolean(System.getProperty(UPDATE_FIXTURES_PROPERTY, "false"))) {
            return false;
        }
        Files.createDirectories(checkedInFixture.getParent());
        Files.copy(generatedFixture, checkedInFixture, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }
}
