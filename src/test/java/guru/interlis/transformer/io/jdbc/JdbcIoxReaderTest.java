package guru.interlis.transformer.io.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.FormatOpenContext;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.InputBinding;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartBasketEvent;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcIoxReaderTest {

    private static final String CLASS = "DemoJdbcSource.Data.Municipality";

    private String url;

    @BeforeEach
    void createDatabase(@TempDir Path tempDir) throws Exception {
        url = "jdbc:sqlite:" + tempDir.resolve("demo.sqlite");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE municipalities (id INTEGER PRIMARY KEY, bfsnr INTEGER, name TEXT, population INTEGER)");
            statement.executeUpdate("INSERT INTO municipalities VALUES (1, 2601, 'Solothurn', 17000)");
            statement.executeUpdate("INSERT INTO municipalities VALUES (2, 2610, 'Olten', 19000)");
        }
    }

    @Test
    void mapsScalarJdbcRowsToIomObjects() throws Exception {
        List<IomObject> objects = readObjects(query("municipalities", "id", "b1"));

        assertThat(objects).hasSize(2);
        IomObject first = objects.get(0);
        assertThat(first.getobjecttag()).isEqualTo(CLASS);
        assertThat(first.getattrvalue("bfsnr")).isEqualTo("2601");
        assertThat(first.getattrvalue("name")).isEqualTo("Solothurn");
        assertThat(first.getattrvalue("population")).isEqualTo("17000");
        // the oid column is the identity, not a model attribute
        assertThat(first.getattrvalue("id")).isNull();
    }

    @Test
    void usesOidColumnWhenConfigured() throws Exception {
        List<IomObject> objects = readObjects(query("municipalities", "id", "b1"));

        assertThat(objects).extracting(IomObject::getobjectoid).containsExactly("1", "2");
    }

    @Test
    void generatesSyntheticOidWhenNoOidColumnConfigured() throws Exception {
        List<IomObject> objects = readObjects(query("municipalities", null, "b1"));

        assertThat(objects).extracting(IomObject::getobjectoid).containsExactly("municipalities.1", "municipalities.2");
    }

    @Test
    void emitsBasketWithConfiguredBid() throws Exception {
        List<String> bids = new ArrayList<>();
        IoxReader reader =
                JdbcIoxReader.open(binding(List.of(query("municipalities", "id", "demo-basket"))), context());
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof StartBasketEvent basket) {
                    bids.add(basket.getBid());
                }
            }
        } finally {
            reader.close();
        }
        assertThat(bids).containsExactly("demo-basket");
    }

    @Test
    void failsIfJdbcInputHasNoQueries() {
        assertThatThrownBy(() -> JdbcIoxReader.open(binding(List.of()), context()))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("no queries");
    }

    // -- helpers --------------------------------------------------------------

    private List<IomObject> readObjects(JobConfig.JdbcQuerySpec query) throws Exception {
        List<IomObject> objects = new ArrayList<>();
        IoxReader reader = JdbcIoxReader.open(binding(List.of(query)), context());
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent objectEvent) {
                    objects.add(objectEvent.getIomObject());
                }
            }
        } finally {
            reader.close();
        }
        return objects;
    }

    private JobConfig.JdbcQuerySpec query(String id, String oidColumn, String basketId) {
        JobConfig.JdbcQuerySpec query = new JobConfig.JdbcQuerySpec();
        query.id = id;
        query.clazz = CLASS;
        query.topic = "DemoJdbcSource.Data";
        query.basketId = basketId;
        query.oidColumn = oidColumn;
        query.sql = "select id, bfsnr, name, population from municipalities order by id";
        return query;
    }

    private InputBinding binding(List<JobConfig.JdbcQuerySpec> queries) {
        JobConfig.JdbcConnectionSpec connection = new JobConfig.JdbcConnectionSpec();
        connection.url = url;
        return new InputBinding("db", null, "DemoJdbcSource", "jdbc", Map.of(), null, null, connection, queries);
    }

    private FormatOpenContext context() {
        return new FormatOpenContext(null, null, null);
    }
}
