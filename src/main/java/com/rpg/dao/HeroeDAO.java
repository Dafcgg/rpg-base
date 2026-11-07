package com.rpg.dao;

import com.rpg.model.Heroe;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** JDBC data access for persistent heroes. */
public class HeroeDAO {

    public static final int ERROR_BD = -1;

    private static final String INSERTAR =
            "INSERT INTO heroes (nombre, tipo, vida_maxima, nivel) VALUES (?, ?, ?, ?)";
    private static final String LISTAR =
            "SELECT id, nombre, tipo, vida_maxima, nivel FROM heroes ORDER BY id";
    private static final String BUSCAR_TIPO =
            "SELECT id, nombre, tipo, vida_maxima, nivel FROM heroes WHERE tipo = ? ORDER BY id";
    private static final String BUSCAR_ID =
            "SELECT id, nombre, tipo, vida_maxima, nivel FROM heroes WHERE id = ?";
    private static final String ACTUALIZAR_NIVEL =
            "UPDATE heroes SET nivel = ? WHERE id = ?";
    private static final String ELIMINAR =
            "DELETE FROM heroes WHERE id = ?";

    public boolean insertar(Heroe heroe) {
        Objects.requireNonNull(heroe, "heroe");
        Connection conexion = ConexionDB.obtenerConexionGrupal();
        if (conexion == null) {
            return false;
        }
        try (conexion;
             PreparedStatement sentencia = conexion.prepareStatement(INSERTAR, new String[]{"id"})) {
            sentencia.setString(1, heroe.getNombre());
            sentencia.setString(2, heroe.getTipo());
            sentencia.setInt(3, heroe.getVidaMaxima());
            sentencia.setInt(4, heroe.getNivel());
            if (sentencia.executeUpdate() != 1) {
                return false;
            }
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                if (claves.next()) {
                    heroe.setId(claves.getInt(1));
                    return true;
                }
            }
            System.err.println("[HeroeDAO] El heroe se inserto, pero no se recibio su id generado.");
            return false;
        } catch (SQLException e) {
            reportarError("insertar", e);
            return false;
        }
    }

    public List<Heroe> listar() {
        Connection conexion = ConexionDB.obtenerConexionGrupal();
        if (conexion == null) {
            return List.of();
        }
        List<Heroe> heroes = new ArrayList<>();
        try (conexion;
             PreparedStatement sentencia = conexion.prepareStatement(LISTAR);
             ResultSet resultados = sentencia.executeQuery()) {
            while (resultados.next()) {
                heroes.add(mapear(resultados));
            }
        } catch (SQLException e) {
            reportarError("listar heroes", e);
            return List.of();
        }
        return heroes;
    }

    public List<Heroe> buscarPorTipo(String tipo) {
        Objects.requireNonNull(tipo, "tipo");
        Connection conexion = ConexionDB.obtenerConexionGrupal();
        if (conexion == null) {
            return List.of();
        }
        List<Heroe> heroes = new ArrayList<>();
        try (conexion;
             PreparedStatement sentencia = conexion.prepareStatement(BUSCAR_TIPO)) {
            sentencia.setString(1, tipo);
            try (ResultSet resultados = sentencia.executeQuery()) {
                while (resultados.next()) {
                    heroes.add(mapear(resultados));
                }
            }
        } catch (SQLException e) {
            reportarError("buscar heroes por tipo", e);
            return List.of();
        }
        return heroes;
    }

    public Heroe buscarPorId(int id) {
        Connection conexion = ConexionDB.obtenerConexionGrupal();
        if (conexion == null) {
            return null;
        }
        try (conexion;
             PreparedStatement sentencia = conexion.prepareStatement(BUSCAR_ID)) {
            sentencia.setInt(1, id);
            try (ResultSet resultados = sentencia.executeQuery()) {
                return resultados.next() ? mapear(resultados) : null;
            }
        } catch (SQLException e) {
            reportarError("buscar heroe por id", e);
            return null;
        }
    }

    public int actualizarNivel(int id, int nivel) {
        Connection conexion = ConexionDB.obtenerConexionGrupal();
        if (conexion == null) {
            return ERROR_BD;
        }
        try (conexion;
             PreparedStatement sentencia = conexion.prepareStatement(ACTUALIZAR_NIVEL)) {
            sentencia.setInt(1, nivel);
            sentencia.setInt(2, id);
            return sentencia.executeUpdate();
        } catch (SQLException e) {
            reportarError("actualizar nivel", e);
            return ERROR_BD;
        }
    }

    public int eliminar(int id) {
        Connection conexion = ConexionDB.obtenerConexionGrupal();
        if (conexion == null) {
            return ERROR_BD;
        }
        try (conexion;
             PreparedStatement sentencia = conexion.prepareStatement(ELIMINAR)) {
            sentencia.setInt(1, id);
            return sentencia.executeUpdate();
        } catch (SQLException e) {
            reportarError("eliminar heroe", e);
            return ERROR_BD;
        }
    }

    private static Heroe mapear(ResultSet resultado) throws SQLException {
        return new Heroe(
                resultado.getInt("id"),
                resultado.getString("nombre"),
                resultado.getString("tipo"),
                resultado.getInt("vida_maxima"),
                resultado.getInt("nivel"));
    }

    private static void reportarError(String operacion, SQLException e) {
        System.err.printf("[HeroeDAO] Error al %s. SQLState=%s, codigo=%d.%n",
                operacion, e.getSQLState(), e.getErrorCode());
    }
}
