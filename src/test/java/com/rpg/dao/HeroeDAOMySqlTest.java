package com.rpg.dao;

import com.rpg.model.Heroe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in integration test; its temporary table shadows, but never edits, real heroes. */
@EnabledIfEnvironmentVariable(named = "RPG_RUN_MYSQL_TESTS", matches = "true")
class HeroeDAOMySqlTest {
    @Test
    void realMySqlCrudDuplicateConstraintInvalidDataAndLostConnection() throws SQLException {
        try (Connection real = ConexionDB.obtenerConexionGrupal()) {
            try (Statement ddl = real.createStatement()) {
                ddl.executeUpdate("CREATE TEMPORARY TABLE heroes ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, nombre VARCHAR(100) NOT NULL UNIQUE,"
                        + "tipo VARCHAR(50) NOT NULL, vida_maxima INT NOT NULL, nivel INT NOT NULL,"
                        + "CHECK (tipo IN ('guerrero', 'mago')), CHECK (vida_maxima > 0), CHECK (nivel >= 1)"
                        + ") ENGINE=InnoDB");
            }
            Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close")) {
                            // Simular una conexion nueva por operacion, con autocommit por defecto.
                            if (!real.isClosed()) { real.setAutoCommit(true); }
                            return null;
                        }
                        try { return method.invoke(real, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
            HeroeDAO dao = new HeroeDAO(() -> borrowed);
            Heroe heroe = new Heroe("AuditHero", "mago", Integer.MAX_VALUE, Integer.MAX_VALUE);
            assertTrue(dao.listar().isEmpty());
            assertTrue(dao.insertar(heroe));
            assertTrue(heroe.getId() > 0);
            assertEquals(heroe, dao.buscarPorId(heroe.getId()));
            assertEquals(1, dao.buscarPorTipo("mago").size());
            assertTrue(dao.buscarPorTipo("mago' OR 1=1 --").isEmpty());
            assertEquals(1, dao.buscarPorVariosIds(new int[]{heroe.getId(), heroe.getId(), -1}).size());
            PersistenciaException duplicate = assertThrows(PersistenciaException.class,
                    () -> dao.insertar(new Heroe("AuditHero", "guerrero", 100, 1)));
            assertEquals(PersistenciaException.Motivo.DUPLICADO, duplicate.getMotivo());
            assertEquals(1, dao.listar().size());
            assertEquals(1, dao.actualizarNivel(heroe.getId(), 7));
            assertEquals(1, dao.actualizarNivel(heroe.getId(), 7));
            assertEquals(7, dao.buscarPorId(heroe.getId()).getNivel());
            assertEquals(0, dao.actualizarNivel(Integer.MAX_VALUE, 3));
            assertEquals(1, dao.eliminar(heroe.getId()));
            assertNull(dao.buscarPorId(heroe.getId()));
            assertEquals(0, dao.eliminar(heroe.getId()));
            try (Statement externalClient = real.createStatement()) {
                externalClient.executeUpdate("INSERT INTO heroes(nombre,tipo,vida_maxima,nivel)"
                        + " VALUES('Invalid!','mago',100,1)");
            }
            assertThrows(PersistenciaException.class, dao::listar);
            real.close();
            assertThrows(PersistenciaException.class, dao::listar);
        }
    }
}
