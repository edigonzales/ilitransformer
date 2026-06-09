package guru.interlis.transformer;

import guru.interlis.transformer.model.IliModelService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class CheckedInModelsCompileTest {

    private static final String AV_MODELDIR = "src/test/data/av/models/;https://models.interlis.ch";
    private static final String LOCAL_MODELDIR = "src/test/data/models/";

    @Test
    void allLocalModelsCompile() throws Exception {
        var svc = new IliModelService();
        List<String> failures = new ArrayList<>();

        for (String modelName : localModelNames()) {
            var result = svc.compileModel(modelName, LOCAL_MODELDIR);
            if (result.hasErrors()) {
                String msg = result.diagnostics().all().get(0).message();
                failures.add(modelName + ": " + msg);
            }
        }

        assertThat(failures).as("All local .ili models must compile without errors").isEmpty();
    }

    @Test
    void avModelsCompile() throws Exception {
        var svc = new IliModelService();
        List<String> failures = new ArrayList<>();

        for (String modelName : avModelNames()) {
            var result = svc.compileModel(modelName, AV_MODELDIR);
            if (result.hasErrors()) {
                String msg = result.diagnostics().all().get(0).message();
                failures.add(modelName + ": " + msg);
            }
        }

        assertThat(failures).as("All AV .ili models must compile without errors").isEmpty();
    }

    private static List<String> localModelNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of(LOCAL_MODELDIR))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".ili")).toList()) {
                String content = Files.readString(file);
                for (String line : content.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("MODEL ")) {
                        String name = line.substring(6).trim();
                        int space = name.indexOf(' ');
                        if (space > 0) name = name.substring(0, space);
                        int paren = name.indexOf('(');
                        if (paren > 0) name = name.substring(0, paren);
                        names.add(name.trim());
                        break;
                    }
                }
            }
        }
        return names;
    }

    private static List<String> avModelNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("src/test/data/av/models/"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".ili")).toList()) {
                String content = Files.readString(file);
                for (String line : content.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("MODEL ")) {
                        String name = line.substring(6).trim();
                        int space = name.indexOf(' ');
                        if (space > 0) name = name.substring(0, space);
                        int paren = name.indexOf('(');
                        if (paren > 0) name = name.substring(0, paren);
                        names.add(name.trim());
                        break;
                    }
                }
            }
        }
        return names;
    }
}
