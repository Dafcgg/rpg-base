package com.rpg.dao;

import com.rpg.model.Heroe;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO de la tabla {@code heroes}. Unica clase del modulo de heroes que usa JDBC.
 *
 * <ul>
 *   <li>{@code executeQuery()}: consultas SELECT, devuelve un ResultSet.</li>
 *   <li>{@code executeUpdate()}: INSERT/UPDATE/DELETE, devuelve las filas afectadas.</li>
 * </ul>
 * Todas las consultas usan PreparedStatement con parametros {@code ?}.
 */
public class HeroeDAO {

    /** Codigo devuelto por actualizarNivel/eliminar cuando ocurre un error de BD. */
    public static final int ERROR_BD = -1;

    private static final String SQL_INSERTAR =
            "INSERT INTO heroes (nombre, tipo, vida_maxima, nivel) VALUES (?, ?, ?, ?)";
    private static final String SQL_LISTAR =
            "SELECT id, nombre, tipo, vida_maxima, nivel FROM heroes ORDER BY id";
    private static final String SQL_POR_TIPO =
            "SELECT id, nombre, tipo, vida_maxima, nivel FROM heroes WHERE tipo = ? ORDER BY id";
    private static final String SQL_POR_ID =
            "SELECT id, nombre, tipo, vida_maxima, nivel FROM heroes WHERE id = ?";
    private static final String SQL_ACTUALIZAR_NIVEL = "UPDATE heroes SET nivel = ? WHERE id = ?";
    private static final String SQL_ELIMINAR = "DELETE FROM heroes WHERE id = ?";

    /** @return true si se inserto la fila (y el heroe recibe su id generado) */
    public boolean insertar(Heroe heroe) {
        try (Connection conn = ConexionDB.obtenerConexionGrupal()) {
            if (conn == null) {
                return false;
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, heroe.getNombre());
                ps.setString(2, heroe.getTipo());
                ps.setInt(3, heroe.getVidaMaxima());
                ps.setInt(4, heroe.getNivel());
                int filas = ps.executeUpdate();
                if (filas > 0) {
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            heroe.setId(keys.getInt(1));
                        }
                    }
                    return true;
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("[HeroeDAO] Ya existe un heroe con el nombre '" + heroe.getNombre() + "'.");
        } catch (SQLException e) {
            System.out.println("[HeroeDAO] Error al insertar heroe: " + e.getMessage());
        }
        return false;
    }

    public List<Heroe> listar() {
        List<Heroe> heroes = new ArrayList<>();
        try (Connection conn = ConexionDB.obtenerConexionGrupal()) {
            if (conn == null) {
                return heroes;
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_LISTAR);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    heroes.add(mapear(rs));
                }
            }
        } catch (SQLException e) {
            System.out.println("[HeroeDAO] Error al listar heroes: " + e.getMessage());
        }
        return heroes;
    }

    public List<Heroe> buscarPorTipo(String tipo) {
        List<Heroe> heroes = new ArrayList<>();
        try (Connection conn = ConexionDB.obtenerConexionGrupal()) {
            if (conn == null) {
                return heroes;
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_POR_TIPO)) {
                ps.setString(1, tipo);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        heroes.add(mapear(rs));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[HeroeDAO] Error al buscar por tipo: " + e.getMessage());
        }
        return heroes;
    }

    /** @return el heroe, o null si no existe o hay error */
    public Heroe obtenerPorId(int id) {
        try (Connection conn = ConexionDB.obtenerConexionGrupal()) {
            if (conn == null) {
                return null;
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_POR_ID)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapear(rs);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[HeroeDAO] Error al buscar heroe: " + e.getMessage());
        }
        return null;
    }

    /** @return filas afectadas (0 si el id no existe) o {@link #ERROR_BD} */
    public int actualizarNivel(int id, int nivel) {
        try (Connection conn = ConexionDB.obtenerConexionGrupal()) {
            if (conn == null) {
                return ERROR_BD;
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_ACTUALIZAR_NIVEL)) {
                ps.setInt(1, nivel);
                ps.setInt(2, id);
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("[HeroeDAO] Error al actualizar nivel: " + e.getMessage());
            return ERROR_BD;
        }
    }

    /** @return filas afectadas (0 si el id no existe) o {@link #ERROR_BD} */
    public int eliminar(int id) {
        try (Connection conn = ConexionDB.obtenerConexionGrupal()) {
            if (conn == null) {
                return ERROR_BD;
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_ELIMINAR)) {
                ps.setInt(1, id);
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("[HeroeDAO] Error al eliminar heroe: " + e.getMessage());
            return ERROR_BD;
        }
    }

    private Heroe mapear(ResultSet rs) throws SQLException {
        return new Heroe(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getString("tipo"),
                rs.getInt("vida_maxima"),
                rs.getInt("nivel"));
    }
}
