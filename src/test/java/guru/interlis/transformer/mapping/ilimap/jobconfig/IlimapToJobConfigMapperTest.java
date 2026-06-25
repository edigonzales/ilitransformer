package guru.interlis.transformer.mapping.ilimap.jobconfig;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticResult;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticValidator;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapToJobConfigMapperTest {

    private final IlimapToJobConfigMapper mapper = new IlimapToJobConfigMapper();

    private JobConfig mapFromSource(String source) {
        IlimapDocument doc = new IlimapParser(source).parseDocument();
        IlimapSemanticResult sem = new IlimapSemanticValidator().validate(doc);
        assertThat(sem.hasErrors())
                .as("semantic validation should pass: %s", sem.diagnostics())
                .isFalse();
        return mapper.map(doc, sem.symbols(), Path.of("."));
    }

    private static final String MINIMAL = """
            mapping v2 "test-mapping" {
              input src { path "in.xtf"; model "SrcModel"; }
              output tgt { path "out.xtf"; model "TgtModel"; }
              rule r1 {
                target tgt class "TgtModel.Topic.TgtClass";
                source s from src class "SrcModel.Topic.SrcClass";
                assign { Name = s.Name; }
              }
            }
            """;

    @Test
    void mapsMinimalIlimapToJobConfig() {
        JobConfig config = mapFromSource(MINIMAL);
        assertThat(config).isNotNull();
        assertThat(config.job.inputs).hasSize(1);
        assertThat(config.job.outputs).hasSize(1);
        assertThat(config.mapping.rules).hasSize(1);
    }

    @Test
    void doesNotSetJobConfigVersionToTwo() {
        JobConfig config = mapFromSource(MINIMAL);
        assertThat(config.version).isEqualTo(1);
    }

    @Test
    void mapsJdbcInputWithConnectionAndQueries() {
        String source = """
                mapping v2 "jdbc-mapping" {
                  input db {
                    model "SrcModel";
                    format jdbc;
                    connection {
                      driver "org.sqlite.JDBC";
                      url "jdbc:sqlite:demo.sqlite";
                      userEnv "PGUSER";
                      passwordEnv "PGPW";
                      property "ApplicationName" "ilitransformer";
                    }
                    query municipalities {
                      topic "SrcModel.Topic";
                      class "SrcModel.Topic.SrcClass";
                      basketId "b1";
                      oidColumn "id";
                      sql "select id, gemeinde from municipalities";
                      column "gemeinde" "Name";
                    }
                  }
                  output tgt { path "out.xtf"; model "TgtModel"; }
                  rule r1 {
                    target tgt class "TgtModel.Topic.TgtClass";
                    source s from db class "SrcModel.Topic.SrcClass";
                    assign { Name = s.Name; }
                  }
                }
                """;

        JobConfig config = mapFromSource(source);

        assertThat(config.job.inputs).hasSize(1);
        JobConfig.InputSpec input = config.job.inputs.get(0);
        assertThat(input.format).isEqualTo("jdbc");

        assertThat(input.connection).isNotNull();
        assertThat(input.connection.driver).isEqualTo("org.sqlite.JDBC");
        assertThat(input.connection.url).isEqualTo("jdbc:sqlite:demo.sqlite");
        assertThat(input.connection.userEnv).isEqualTo("PGUSER");
        assertThat(input.connection.passwordEnv).isEqualTo("PGPW");
        assertThat(input.connection.properties).containsEntry("ApplicationName", "ilitransformer");

        assertThat(input.queries).hasSize(1);
        JobConfig.JdbcQuerySpec query = input.queries.get(0);
        assertThat(query.id).isEqualTo("municipalities");
        assertThat(query.topic).isEqualTo("SrcModel.Topic");
        assertThat(query.clazz).isEqualTo("SrcModel.Topic.SrcClass");
        assertThat(query.basketId).isEqualTo("b1");
        assertThat(query.oidColumn).isEqualTo("id");
        assertThat(query.sql).isEqualTo("select id, gemeinde from municipalities");
        assertThat(query.columns).containsEntry("gemeinde", "Name");
    }

    @Test
    void mapsJobSection() {
        String source = """
                mapping v2 "my-mapping" {
                  job {
                    name "explicit-name";
                    description "A test";
                    direction forward;
                    failPolicy lenient;
                    compileMode compatible;
                    modeldir "https://models.example.com/";
                  }
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.job.name).isEqualTo("explicit-name");
        assertThat(config.job.description).isEqualTo("A test");
        assertThat(config.job.direction).isEqualTo("forward");
        assertThat(config.job.failPolicy).isEqualTo("lenient");
        assertThat(config.mapping.compileMode).isEqualTo("compatible");
        assertThat(config.job.modeldir).containsExactly("https://models.example.com/");
    }

    @Test
    void documentNameFallsBackToJobName() {
        JobConfig config = mapFromSource(MINIMAL);
        assertThat(config.job.name).isEqualTo("test-mapping");
    }

    @Test
    void jobNameTakesPrecedenceOverDocumentName() {
        String source = """
                mapping v2 "doc-name" {
                  job { name "job-name"; }
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.job.name).isEqualTo("job-name");
    }

    @Test
    void mapsInputsAndOutputs() {
        JobConfig config = mapFromSource(MINIMAL);
        JobConfig.InputSpec input = config.job.inputs.get(0);
        assertThat(input.id).isEqualTo("src");
        assertThat(input.path).isEqualTo("in.xtf");
        assertThat(input.model).isEqualTo("SrcModel");

        JobConfig.OutputSpec output = config.job.outputs.get(0);
        assertThat(output.id).isEqualTo("tgt");
        assertThat(output.path).isEqualTo("out.xtf");
        assertThat(output.model).isEqualTo("TgtModel");
    }

    @Test
    void mapsInputOutputFormat() {
        String source = """
                mapping v2 {
                  input src { path "in.itf"; model "M"; format itf; }
                  output tgt { path "out.xtf"; model "M"; format xtf; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.job.inputs.get(0).format).isEqualTo("itf");
        assertThat(config.job.outputs.get(0).format).isEqualTo("xtf");
    }

    @Test
    void mapsInputOutputOptions() {
        String source = """
                mapping v2 {
                  input src {
                    path "in.csv";
                    model "M";
                    format csv;
                    option firstLineIsHeader true;
                    option separator ";";
                    option encoding "UTF-8";
                  }
                  output tgt {
                    path "out.xtf";
                    model "M";
                    option fetchSize 10000;
                  }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.job.inputs.get(0).options)
                .containsEntry("firstLineIsHeader", "true")
                .containsEntry("separator", ";")
                .containsEntry("encoding", "UTF-8");
        assertThat(config.job.outputs.get(0).options).containsEntry("fetchSize", "10000");
    }

    @Test
    void inputOutputOptionsDefaultToEmpty() {
        JobConfig config = mapFromSource(MINIMAL);
        assertThat(config.job.inputs.get(0).options).isEmpty();
        assertThat(config.job.outputs.get(0).options).isEmpty();
    }

    @Test
    void mapsOidStrategy() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  oid { strategy deterministicUuid; namespace "my-ns"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.mapping.oidStrategy.defaultStrategy).isEqualTo("deterministicUuid");
        assertThat(config.mapping.oidStrategy.namespace).isEqualTo("my-ns");
    }

    @Test
    void mapsBasketStrategy() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  basket byTopic;
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.mapping.basketStrategy.defaultStrategy).isEqualTo("byTopic");
    }

    @Test
    void mapsEnumEntries() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  enum StatusMap {
                    "active" => true;
                    "inactive" => false;
                    "unknown" => null;
                    #LFP3 => "lfp3";
                    42 => "forty-two";
                  }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.mapping.enums).containsKey("StatusMap");
        var entries = config.mapping.enums.get("StatusMap");
        assertThat(entries.get("active")).isEqualTo("true");
        assertThat(entries.get("inactive")).isEqualTo("false");
        assertThat(entries.get("unknown")).isNull();
        assertThat(entries.get("#LFP3")).isEqualTo("lfp3");
        assertThat(entries.get("42")).isEqualTo("forty-two");
    }

    @Test
    void mapsRuleTargetAndSource() {
        JobConfig config = mapFromSource(MINIMAL);
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.id).isEqualTo("r1");
        assertThat(rule.target.output).isEqualTo("tgt");
        assertThat(rule.target.clazz).isEqualTo("TgtModel.Topic.TgtClass");
        assertThat(rule.sources).hasSize(1);
        assertThat(rule.sources.get(0).alias).isEqualTo("s");
        assertThat(rule.sources.get(0).inputs).containsExactly("src");
        assertThat(rule.sources.get(0).clazz).isEqualTo("SrcModel.Topic.SrcClass");
    }

    @Test
    void mapsAssignments() {
        JobConfig config = mapFromSource(MINIMAL);
        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.assign).containsEntry("Name", "s.Name");
    }

    @Test
    void mapsWhereExpression() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    where s.Active == true;
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.mapping.rules.get(0).where).isEqualTo("s.Active == true");
    }

    @Test
    void mapsSourceWhereExpression() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C" where s.Type == #A;
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.mapping.rules.get(0).sources.get(0).where).isEqualTo("s.Type == #A");
    }

    @Test
    void mapsIdentityExpressions() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    identity s.Id, s.Name;
                    assign { X = s.X; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.mapping.rules.get(0).identity).isNotNull();
        assertThat(config.mapping.rules.get(0).identity.sourceKey).containsExactly("s.Id", "s.Name");
    }

    @Test
    void mapsRuleDefaults() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { X = s.X; }
                    defaults { X = "fallback"; }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.mapping.rules.get(0).defaults).containsEntry("X", "\"fallback\"");
    }

    @Test
    void normalizesEnumMapInAssign() {
        String source = """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output tgt { path "out.xtf"; model "M"; }
                  enum StatusMap { "a" => "b"; }
                  rule r1 {
                    target tgt class "M.T.C";
                    source s from src class "M.T.C";
                    assign { Status = enumMap(s.Type, StatusMap); }
                  }
                }
                """;
        JobConfig config = mapFromSource(source);
        assertThat(config.mapping.rules.get(0).assign.get("Status")).isEqualTo("enumMap(s.Type, \"StatusMap\")");
    }

    @Test
    void mapsJdbcQueryWithGeometryBlock() {
        String source = """
                mapping v2 "jdbc-geom-mapping" {
                  input db {
                    model "SrcModel";
                    format jdbc;
                    connection {
                      driver "org.sqlite.JDBC";
                      url "jdbc:sqlite:demo.sqlite";
                    }
                    query stations {
                      class "SrcModel.Topic.Station";
                      sql "select id, name, geom_wkt from stations";
                      geometry {
                        attribute "geom";
                        column "geom_wkt";
                        encoding wkt;
                        type coord;
                        srid 2056;
                      }
                    }
                  }
                  output tgt { path "out.xtf"; model "TgtModel"; }
                  rule r1 {
                    target tgt class "TgtModel.Topic.TgtClass";
                    source s from db class "SrcModel.Topic.Station";
                    assign { Name = s.name; }
                  }
                }
                """;

        JobConfig config = mapFromSource(source);

        assertThat(config.job.inputs).hasSize(1);
        JobConfig.InputSpec input = config.job.inputs.get(0);
        assertThat(input.format).isEqualTo("jdbc");
        assertThat(input.queries).hasSize(1);

        JobConfig.JdbcQuerySpec query = input.queries.get(0);
        assertThat(query.geometry).hasSize(1);

        JobConfig.JdbcGeometrySpec geometry = query.geometry.get(0);
        assertThat(geometry.attribute).isEqualTo("geom");
        assertThat(geometry.column).isEqualTo("geom_wkt");
        assertThat(geometry.encoding).isEqualTo("wkt");
        assertThat(geometry.type).isEqualTo("coord");
        assertThat(geometry.srid).isEqualTo(2056);
    }
}
