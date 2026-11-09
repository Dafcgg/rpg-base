package com.rpg.dao;

import com.rpg.model.Heroe;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class HeroeDAOTest {
    @Test
    void failedConnectionIsNotReportedAsAnEmptyDatabase() {
        HeroeDAO dao = new HeroeDAO(() -> { throw new SQLException("offline", "08001"); });
        assertThrows(PersistenciaException.class, dao::listar);
        assertThrows(PersistenciaException.class, () -> dao.buscarPorId(1));
    }

    @Test
    void closesEveryReadResourceAndMapsRows() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.rows = List.of(row("Aria"));
        List<Heroe> heroes = jdbc.dao().listar();
        assertEquals("Aria", heroes.getFirst().getNombre());
        assertTrue(jdbc.closed);
        assertEquals(1, jdbc.closedStatements);
        assertEquals(1, jdbc.closedResults);
        assertEquals(10, jdbc.statements.getFirst().timeout);
    }

    @Test
    void invalidStoredRowIsAControlledFailureAndClosesResources() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.rows = List.of(row("Invalid!"));
        assertThrows(PersistenciaException.class, () -> jdbc.dao().listar());
        assertTrue(jdbc.closed);
        assertEquals(1, jdbc.closedStatements);
        assertEquals(1, jdbc.closedResults);
    }

    @Test
    void closesStatementIfTimeoutConfigurationFailsDuringInitialization() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.failTimeout = true;
        assertThrows(PersistenciaException.class, () -> jdbc.dao().listar());
        assertTrue(jdbc.closed);
        assertEquals(1, jdbc.closedStatements);
    }

    @Test
    void searchInputCannotChangeTheSqlTemplate() {
        JdbcStub jdbc = new JdbcStub();
        String injection = "mago' OR 1=1 --";
        assertTrue(jdbc.dao().buscarPorTipo(injection).isEmpty());
        Stmt statement = jdbc.statements.getFirst();
        assertFalse(statement.sql.contains(injection));
        assertTrue(statement.sql.contains("tipo = ?"));
        assertEquals(injection, statement.parameters.get(1));
    }

    @Test
    void missingGeneratedKeyRollsBackAndDoesNotAssignAnId() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.generatedId = 0;
        Heroe heroe = newHero();
        assertThrows(PersistenciaException.class, () -> jdbc.dao().insertar(heroe));
        assertEquals(0, heroe.getId());
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.rollbacks);
        assertTrue(jdbc.closed);
        assertEquals(1, jdbc.closedStatements);
        assertEquals(1, jdbc.closedResults);
    }

    @Test
    void duplicateNameIsDistinctFromOtherSqlFailures() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.updateFailure = new SQLException("duplicate", "23000", 1062);
        PersistenciaException error = assertThrows(PersistenciaException.class,
                () -> jdbc.dao().insertar(newHero()));
        assertEquals(PersistenciaException.Motivo.DUPLICADO, error.getMotivo());
        assertEquals(1, jdbc.rollbacks);
        assertTrue(jdbc.closed);
    }

    @Test
    void insertionCommitsBeforeReturningGeneratedId() {
        JdbcStub jdbc = new JdbcStub();
        Heroe heroe = newHero();
        assertTrue(jdbc.dao().insertar(heroe));
        assertEquals(42, heroe.getId());
        assertEquals(1, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        assertFalse(jdbc.autoCommit);
    }

    @Test
    void failedCommitDoesNotAssignAnUnconfirmedId() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.failCommit = true;
        Heroe heroe = newHero();
        assertThrows(PersistenciaException.class, () -> jdbc.dao().insertar(heroe));
        assertEquals(0, heroe.getId());
        assertEquals(1, jdbc.rollbacks);
        assertTrue(jdbc.closed);
    }

    @Test
    void unchangedExistingLevelIsNotReportedAsMissingId() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.updateCount = 0;
        jdbc.rows = List.of(row("Aria"));
        assertEquals(1, jdbc.dao().actualizarNivel(1, 4));
        assertEquals(2, jdbc.statements.size());
        assertEquals(2, jdbc.closedStatements);
    }

    @Test
    void absentIdRemainsADistinctSuccessfulZeroRowResult() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.updateCount = 0;
        assertEquals(0, jdbc.dao().actualizarNivel(123, 4));
    }

    @Test
    void batchesThousandsOfIdsWithBoundedParameters() {
        JdbcStub jdbc = new JdbcStub();
        int[] ids = IntStream.concat(IntStream.rangeClosed(1, 2005), IntStream.of(1, -2, 0)).toArray();
        assertTrue(jdbc.dao().buscarPorVariosIds(ids).isEmpty());
        assertEquals(3, jdbc.statements.size());
        assertEquals(1000, jdbc.statements.get(0).parameters.size());
        assertEquals(1000, jdbc.statements.get(1).parameters.size());
        assertEquals(5, jdbc.statements.get(2).parameters.size());
        assertEquals(3, jdbc.closedStatements);
        assertEquals(3, jdbc.closedResults);
        assertTrue(jdbc.closed);
    }

    @Test
    void failureInALaterBatchCannotReturnPartialData() {
        JdbcStub jdbc = new JdbcStub();
        jdbc.rows = List.of(row("Aria"));
        jdbc.failQueryAt = 2;
        assertThrows(PersistenciaException.class,
                () -> jdbc.dao().buscarPorVariosIds(IntStream.rangeClosed(1, 1001).toArray()));
        assertTrue(jdbc.closed);
        assertEquals(2, jdbc.closedStatements);
        assertEquals(1, jdbc.closedResults);
    }

    @Test
    void invalidIdsAndLevelsNeverOpenADatabaseConnection() {
        HeroeDAO dao = new HeroeDAO(() -> { fail("Connection must not be opened"); return null; });
        assertThrows(IllegalArgumentException.class, () -> dao.buscarPorId(0));
        assertThrows(IllegalArgumentException.class, () -> dao.actualizarNivel(1, 0));
        assertThrows(IllegalArgumentException.class, () -> dao.eliminar(-1));
        assertTrue(dao.buscarPorVariosIds(new int[]{0, -1}).isEmpty());
    }

    private static Heroe newHero() { return new Heroe("Aria", "guerrero", 150, 4); }
    private static Map<String, Object> row(String nombre) {
        return Map.of("id", 1, "nombre", nombre, "tipo", "guerrero", "vida_maxima", 150, "nivel", 4);
    }

    private static final class JdbcStub implements InvocationHandler {
        boolean closed;
        boolean autoCommit = true;
        boolean failTimeout;
        boolean failCommit;
        int commits;
        int rollbacks;
        int closedStatements;
        int closedResults;
        int generatedId = 42;
        int updateCount = 1;
        int queries;
        int failQueryAt;
        SQLException updateFailure;
        List<Map<String, Object>> rows = List.of();
        final List<Stmt> statements = new ArrayList<>();

        HeroeDAO dao() { return new HeroeDAO(() -> proxy(Connection.class, this)); }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            return switch (method.getName()) {
                case "prepareStatement" -> {
                    Stmt statement = new Stmt(this, (String) args[0]);
                    statements.add(statement);
                    yield proxy(PreparedStatement.class, statement);
                }
                case "close" -> { closed = true; yield null; }
                case "setAutoCommit" -> { autoCommit = (boolean) args[0]; yield null; }
                case "commit" -> {
                    if (failCommit) { throw new SQLException("commit failed", "08006"); }
                    commits++;
                    yield null;
                }
                case "rollback" -> { rollbacks++; yield null; }
                default -> defaultValue(method.getReturnType());
            };
        }

        ResultSet result(List<Map<String, Object>> values) {
            return proxy(ResultSet.class, new InvocationHandler() {
                int index = -1;
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    return switch (method.getName()) {
                        case "next" -> ++index < values.size();
                        case "getInt", "getString" -> values.get(index).get(String.valueOf(args[0]));
                        case "close" -> { closedResults++; yield null; }
                        default -> defaultValue(method.getReturnType());
                    };
                }
            });
        }
    }

    private static final class Stmt implements InvocationHandler {
        final JdbcStub jdbc;
        final String sql;
        final Map<Integer, Object> parameters = new HashMap<>();
        int timeout;
        Stmt(JdbcStub jdbc, String sql) { this.jdbc = jdbc; this.sql = sql; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            return switch (method.getName()) {
                case "setQueryTimeout" -> {
                    if (jdbc.failTimeout) { throw new SQLException("timeout setting failed"); }
                    timeout = (int) args[0];
                    yield null;
                }
                case "setInt", "setString" -> { parameters.put((int) args[0], args[1]); yield null; }
                case "executeQuery" -> {
                    if (++jdbc.queries == jdbc.failQueryAt) { throw new SQLException("network failed"); }
                    yield jdbc.result(jdbc.rows);
                }
                case "executeUpdate" -> {
                    if (jdbc.updateFailure != null) { throw jdbc.updateFailure; }
                    yield jdbc.updateCount;
                }
                case "getGeneratedKeys" -> jdbc.result(jdbc.generatedId == 0
                        ? List.of() : List.of(Map.of("1", jdbc.generatedId)));
                case "close" -> { jdbc.closedStatements++; yield null; }
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) { return false; }
        if (type == int.class) { return 0; }
        if (type == long.class) { return 0L; }
        return null;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
