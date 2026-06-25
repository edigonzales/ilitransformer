package guru.interlis.transformer.io.jdbc;

import guru.interlis.transformer.io.FormatOpenContext;
import guru.interlis.transformer.io.FormatOptions;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.InputBinding;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.DefaultIoxFactoryCollection;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a generic JDBC input as an IOX event stream. Each configured query becomes one basket; each
 * result row becomes one {@link ObjectEvent} carrying a flat {@link Iom_jObject} tagged with the
 * query's source class. Scalar columns are mapped via {@link JdbcValueMapper}; geometry columns
 * declared in {@link JobConfig.JdbcGeometrySpec} are processed by {@link JdbcGeometryConverter} and
 * attached as sub-objects.
 *
 * <p>Event order:
 *
 * <pre>
 * StartTransferEvent
 *   StartBasketEvent (query 1) Object* EndBasketEvent
 *   StartBasketEvent (query 2) Object* EndBasketEvent
 * EndTransferEvent
 * null
 * </pre>
 *
 * <p>No format-specific logic leaks into the engine: the engine only ever sees the IOX events.
 */
public final class JdbcIoxReader implements IoxReader {

    private enum State {
        START,
        BEFORE_QUERY,
        INSIDE_QUERY,
        DONE
    }

    private final Connection connection;
    private final List<JobConfig.JdbcQuerySpec> queries;
    private final List<PreparedStatement> statements;
    private final JdbcValueMapper valueMapper;
    private final JdbcGeometryConverter geometryConverter;

    private State state = State.START;
    private int queryIndex = 0;

    private JobConfig.JdbcQuerySpec currentQuery;
    private ResultSet currentResultSet;
    private int oidColumnIndex = -1;
    private final List<int[]> attributeColumns = new ArrayList<>();
    private final List<String> attributeNames = new ArrayList<>();
    private final List<GeomColumn> currentGeomColumns = new ArrayList<>();
    private long currentRowNumber = 0;
    private String currentBid;
    private String currentOidPrefix;

    private IoxFactoryCollection factory;

    private static final class GeomColumn {
        final int columnIndex;
        final String attributeName;
        final JobConfig.JdbcGeometrySpec spec;

        GeomColumn(int columnIndex, String attributeName, JobConfig.JdbcGeometrySpec spec) {
            this.columnIndex = columnIndex;
            this.attributeName = attributeName;
            this.spec = spec;
        }
    }

    private JdbcIoxReader(
            Connection connection,
            List<JobConfig.JdbcQuerySpec> queries,
            List<PreparedStatement> statements,
            JdbcValueMapper valueMapper,
            JdbcGeometryConverter geometryConverter) {
        this.connection = connection;
        this.queries = queries;
        this.statements = statements;
        this.valueMapper = valueMapper;
        this.geometryConverter = geometryConverter;
    }

    /** Validates the configuration, opens the connection and prepares every query statement. */
    public static JdbcIoxReader open(InputBinding binding, FormatOpenContext context) {
        List<JobConfig.JdbcQuerySpec> queries = binding.queries();
        if (queries == null || queries.isEmpty()) {
            throw new JdbcMappingException("JDBC input '" + binding.inputId()
                    + "' has no queries. Add at least one query with class and sql.");
        }
        for (JobConfig.JdbcQuerySpec query : queries) {
            if (query.clazz == null || query.clazz.isBlank()) {
                throw new JdbcMappingException("JDBC query '" + queryLabel(query) + "' in input '" + binding.inputId()
                        + "' has no 'class'. Add the scoped source class name, e.g. 'Model.Topic.Class'.");
            }
            if (query.sql == null || query.sql.isBlank()) {
                throw new JdbcMappingException(
                        "JDBC query '" + queryLabel(query) + "' in input '" + binding.inputId() + "' has no 'sql'.");
            }
        }

        FormatOptions options = FormatOptions.of(binding.options());
        boolean allowBase64Blob = "base64".equalsIgnoreCase(options.getOrDefault("blobEncoding", ""));

        JdbcConnectionFactory connectionFactory = new JdbcConnectionFactory();
        Connection connection =
                connectionFactory.open(binding.connection(), context != null ? context.baseDirectory() : null);

        List<PreparedStatement> statements = new ArrayList<>();
        try {
            for (JobConfig.JdbcQuerySpec query : queries) {
                try {
                    statements.add(connection.prepareStatement(query.sql));
                } catch (SQLException e) {
                    throw new JdbcQueryException(
                            "JDBC query '" + queryLabel(query) + "' in input '" + binding.inputId()
                                    + "' could not be prepared: " + e.getMessage(),
                            e);
                }
            }
        } catch (RuntimeException e) {
            for (PreparedStatement statement : statements) {
                JdbcResourceCloser.closeQuietly(statement);
            }
            JdbcResourceCloser.closeQuietly(connection);
            throw e;
        }

        return new JdbcIoxReader(
                connection, queries, statements, new JdbcValueMapper(allowBase64Blob), new JdbcGeometryConverter());
    }

    @Override
    public IoxEvent read() throws IoxException {
        switch (state) {
            case START:
                state = State.BEFORE_QUERY;
                return new StartTransferEvent();
            case BEFORE_QUERY:
                if (queryIndex >= queries.size()) {
                    state = State.DONE;
                    return new EndTransferEvent();
                }
                return startCurrentBasket();
            case INSIDE_QUERY:
                return readRowOrEndBasket();
            case DONE:
            default:
                return null;
        }
    }

    private StartBasketEvent startCurrentBasket() throws IoxException {
        currentQuery = queries.get(queryIndex);
        currentOidPrefix = currentQuery.id != null && !currentQuery.id.isBlank() ? currentQuery.id : "q" + queryIndex;
        currentBid = currentQuery.basketId != null && !currentQuery.basketId.isBlank()
                ? currentQuery.basketId
                : "b" + (queryIndex + 1);
        currentRowNumber = 0;

        PreparedStatement statement = statements.get(queryIndex);
        try {
            currentResultSet = statement.executeQuery();
        } catch (SQLException e) {
            throw new IoxException(
                    "JDBC query '" + queryLabel(currentQuery) + "' failed to execute: " + e.getMessage(), e);
        }
        prepareColumnMapping();

        state = State.INSIDE_QUERY;
        return new StartBasketEvent(basketType(currentQuery), currentBid);
    }

    private void prepareColumnMapping() throws IoxException {
        attributeColumns.clear();
        attributeNames.clear();
        oidColumnIndex = -1;
        currentGeomColumns.clear();
        try {
            ResultSetMetaData metaData = currentResultSet.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<JobConfig.JdbcGeometrySpec> geometrySpecs =
                    (currentQuery.geometry != null && !currentQuery.geometry.isEmpty())
                            ? currentQuery.geometry
                            : List.of();

            String oidColumn = currentQuery.oidColumn;
            for (int i = 1; i <= columnCount; i++) {
                String label = metaData.getColumnLabel(i);
                if (oidColumn != null && oidColumn.equalsIgnoreCase(label)) {
                    oidColumnIndex = i;
                    continue;
                }
                JobConfig.JdbcGeometrySpec geomSpec = findGeometrySpec(geometrySpecs, label);
                if (geomSpec != null) {
                    currentGeomColumns.add(new GeomColumn(i, geomSpec.attribute, geomSpec));
                    continue;
                }
                String attrName =
                        currentQuery.columns != null ? currentQuery.columns.getOrDefault(label, label) : label;
                attributeColumns.add(new int[] {i});
                attributeNames.add(attrName);
            }
            if (oidColumn != null && !oidColumn.isBlank() && oidColumnIndex == -1) {
                throw new JdbcMappingException("oidColumn '" + oidColumn + "' of JDBC query '"
                        + queryLabel(currentQuery) + "' is not in the result set.");
            }
        } catch (SQLException e) {
            throw new IoxException(
                    "Could not read result metadata for JDBC query '" + queryLabel(currentQuery) + "': "
                            + e.getMessage(),
                    e);
        }
    }

    private static JobConfig.JdbcGeometrySpec findGeometrySpec(
            List<JobConfig.JdbcGeometrySpec> specs, String columnLabel) {
        for (JobConfig.JdbcGeometrySpec spec : specs) {
            if (spec.column != null && spec.column.equalsIgnoreCase(columnLabel)) {
                return spec;
            }
        }
        return null;
    }

    private IoxEvent readRowOrEndBasket() throws IoxException {
        try {
            if (currentResultSet.next()) {
                currentRowNumber++;
                return new ObjectEvent(mapCurrentRow());
            }
        } catch (SQLException e) {
            throw new IoxException(
                    "Failed to read rows for JDBC query '" + queryLabel(currentQuery) + "': " + e.getMessage(), e);
        }

        JdbcResourceCloser.closeQuietly(currentResultSet);
        currentResultSet = null;
        queryIndex++;
        state = State.BEFORE_QUERY;
        return new EndBasketEvent();
    }

    private IomObject mapCurrentRow() throws IoxException {
        try {
            String oid = resolveOid();
            Iom_jObject object = new Iom_jObject(currentQuery.clazz, oid);
            for (int idx = 0; idx < attributeColumns.size(); idx++) {
                int column = attributeColumns.get(idx)[0];
                Object value = currentResultSet.getObject(column);
                valueMapper.applyScalarValue(object, attributeNames.get(idx), value);
            }
            for (GeomColumn geomCol : currentGeomColumns) {
                Object rawValue = currentResultSet.getObject(geomCol.columnIndex);
                if (rawValue != null) {
                    IomObject geom = geometryConverter.convertToGeometry(rawValue, geomCol.spec);
                    if (geom != null) {
                        object.addattrobj(geomCol.attributeName, geom);
                    }
                }
            }
            return object;
        } catch (SQLException e) {
            throw new IoxException(
                    "Failed to map row " + currentRowNumber + " of JDBC query '" + queryLabel(currentQuery) + "': "
                            + e.getMessage(),
                    e);
        }
    }

    private String resolveOid() throws SQLException {
        if (oidColumnIndex > 0) {
            Object raw = currentResultSet.getObject(oidColumnIndex);
            String oid = valueMapper.toIoxScalar(raw);
            if (oid != null && !oid.isBlank()) {
                return oid;
            }
        }
        return currentOidPrefix + "." + currentRowNumber;
    }

    private static String basketType(JobConfig.JdbcQuerySpec query) {
        if (query.topic != null && !query.topic.isBlank()) {
            return query.topic;
        }
        String clazz = query.clazz;
        int lastDot = clazz.lastIndexOf('.');
        return lastDot > 0 ? clazz.substring(0, lastDot) : clazz;
    }

    private static String queryLabel(JobConfig.JdbcQuerySpec query) {
        if (query.id != null && !query.id.isBlank()) {
            return query.id;
        }
        return query.clazz != null ? query.clazz : "<unnamed>";
    }

    @Override
    public void close() throws IoxException {
        JdbcResourceCloser.closeQuietly(currentResultSet);
        currentResultSet = null;
        for (PreparedStatement statement : statements) {
            JdbcResourceCloser.closeQuietly(statement);
        }
        JdbcResourceCloser.closeQuietly(connection);
        state = State.DONE;
    }

    @Override
    public void setFactory(IoxFactoryCollection factory) throws IoxException {
        this.factory = factory;
    }

    @Override
    public IoxFactoryCollection getFactory() throws IoxException {
        if (factory == null) {
            factory = new DefaultIoxFactoryCollection();
        }
        return factory;
    }

    @Override
    public IomObject createIomObject(String type, String oid) throws IoxException {
        return new Iom_jObject(type, oid);
    }
}
