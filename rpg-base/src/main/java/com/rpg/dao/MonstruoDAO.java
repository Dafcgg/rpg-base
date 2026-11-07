package com.rpg.dao;

import com.rpg.model.Monstruo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos para la tabla {@code monstruos}.
 * <p>
 * Proporciona CRUD completo mas busquedas especializadas.
 * Todas las consultas parametrizadas usan {@link PreparedStatement}.
 * Los metodos retornan POJOs o colecciones de POJOs; <strong>nunca</strong>
 * un {@link ResultSet} abierto (lo que violaria la separacion de capas MVC).
 * </p>
 *
 * <h2>executeQuery vs executeUpdate</h2>
 * <ul>
 *   <li>{@code executeQuery()} - usado con SELECT: retorna un {@link ResultSet}
 *       con las filas encontradas. No modifica datos.</li>
 *   <li>{@code executeUpdate()} - usado con INSERT, UPDATE, DELETE: retorna un
 *       {@code int} con el numero de filas afectadas. Modifica datos en la BD.</li>
 * </ul>
 */
public class MonstruoDAO {

    // ---------------------------------------------------------------- SQL

    private static final String SQL_INSERTAR =
            "INSERT INTO monstruos (nombre, vida, ataque) VALUES (?, ?, ?)";

    private static final String SQL_OBTENER_POR_ID =
            "SELECT id, nombre, vida, ataque FROM monstruos WHERE id = ?";

    private static final String SQL_LISTAR_TODOS =
            "SELECT id, nombre, vida, ataque FROM monstruos ORDER BY nombre";

    private static final String SQL_ACTUALIZAR =
            "UPDATE monstruos SET nombre = ?, vida = ?, ataque = ? WHERE id = ?";

    private static final String SQL_ELIMINAR =
            "DELETE FROM monstruos WHERE id = ?";

    private static final String SQL_BUSCAR_POR_NOMBRE =
            "SELECT id, nombre, vida, ataque FROM monstruos WHERE nombre LIKE ?";

    private static final String SQL_LISTAR_POR_VIDA_MINIMA =
            "SELECT id, nombre, vida, ataque FROM monstruos WHERE vida >= ? ORDER BY vida DESC";

    private static final String SQL_TIENE_VICTORIAS =
            "SELECT COUNT(*) FROM victorias WHERE monstruo_id = ?";

    // ---------------------------------------------------------------- metodos publicos

    /**
     * Inserta un nuevo monstruo en la tabla.
     *
     * @param monstruo POJO con nombre, vida y ataque
     * @return {@code true} si la insercion fue exitosa
     */
    public boolean insertar(Monstruo monstruo) {
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, monstruo.getNombre());
                ps.setInt(2, monstruo.getVida());
                ps.setInt(3, monstruo.getAtaque());
                int filas = ps.executeUpdate();
                if (filas > 0) {
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            monstruo.setId(keys.getInt(1));
                        }
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            System.out.println("[MonstruoDAO] Error al insertar monstruo: " + e.getMessage());
        }
        return false;
    }

    /**
     * Obtiene un monstruo por su identificador unico.
     *
     * @param id clave primaria del monstruo
     * @return {@link Monstruo} encontrado o {@code null} si no existe
     */
    public Monstruo obtenerPorId(int id) {
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement(SQL_OBTENER_POR_ID)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapearFila(rs);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[MonstruoDAO] Error al obtener monstruo por id: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lista todos los monstruos ordenados por nombre.
     *
     * @return lista (posiblemente vacia) de monstruos
     */
    public List<Monstruo> listarTodos() {
        List<Monstruo> lista = new ArrayList<>();
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return lista;
            try (PreparedStatement ps = conn.prepareStatement(SQL_LISTAR_TODOS);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearFila(rs));
                }
            }
        } catch (SQLException e) {
            System.out.println("[MonstruoDAO] Error al listar monstruos: " + e.getMessage());
        }
        return lista;
    }

    /**
     * Actualiza nombre, vida y ataque de un monstruo existente.
     *
     * @param monstruo POJO con id y nuevos valores
     * @return {@code true} si se actualizo al menos una fila
     */
    public boolean actualizar(Monstruo monstruo) {
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(SQL_ACTUALIZAR)) {
                ps.setString(1, monstruo.getNombre());
                ps.setInt(2, monstruo.getVida());
                ps.setInt(3, monstruo.getAtaque());
                ps.setInt(4, monstruo.getId());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.out.println("[MonstruoDAO] Error al actualizar monstruo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Elimina un monstruo por su id.
     * <strong>Debe llamarse solo despues de verificar {@link #tieneVictoriasAsociadas(int)}.</strong>
     *
     * @param id clave primaria del monstruo
     * @return {@code true} si se elimino correctamente
     */
    public boolean eliminar(int id) {
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(SQL_ELIMINAR)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.out.println("[MonstruoDAO] Error al eliminar monstruo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Busca monstruos cuyo nombre contenga el termino indicado (busqueda parcial, case-insensitive
     * en MySQL con collation utf8mb4_unicode_ci).
     *
     * @param termino texto a buscar dentro del nombre
     * @return lista de monstruos que coinciden (puede estar vacia)
     */
    public List<Monstruo> buscarPorNombre(String termino) {
        List<Monstruo> lista = new ArrayList<>();
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return lista;
            try (PreparedStatement ps = conn.prepareStatement(SQL_BUSCAR_POR_NOMBRE)) {
                ps.setString(1, "%" + termino + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(mapearFila(rs));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[MonstruoDAO] Error al buscar por nombre: " + e.getMessage());
        }
        return lista;
    }

    /**
     * Lista monstruos cuya vida es mayor o igual al minimo indicado, ordenados de mayor a menor vida.
     *
     * @param vidaMinima umbral minimo de puntos de vida
     * @return lista de monstruos filtrados
     */
    public List<Monstruo> listarPorVidaMinima(int vidaMinima) {
        List<Monstruo> lista = new ArrayList<>();
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return lista;
            try (PreparedStatement ps = conn.prepareStatement(SQL_LISTAR_POR_VIDA_MINIMA)) {
                ps.setInt(1, vidaMinima);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(mapearFila(rs));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[MonstruoDAO] Error al listar por vida minima: " + e.getMessage());
        }
        return lista;
    }

    /**
     * Comprueba si existe al menos una victoria asociada a este monstruo en la tabla
     * {@code victorias}. Usado por el Controller antes de intentar eliminar para evitar
     * que explote una {@code SQLException} de clave foranea.
     *
     * @param id identificador del monstruo
     * @return {@code true} si hay victorias referenciando este monstruo
     */
    public boolean tieneVictoriasAsociadas(int id) {
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(SQL_TIENE_VICTORIAS)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[MonstruoDAO] Error al verificar victorias asociadas: " + e.getMessage());
        }
        return false;
    }

    // ---------------------------------------------------------------- metodo privado

    /**
     * Mapea la fila actual del {@link ResultSet} a un objeto {@link Monstruo}.
     * No avanza el cursor; el llamador debe haber invocado {@code rs.next()} antes.
     */
    private Monstruo mapearFila(ResultSet rs) throws SQLException {
        return new Monstruo(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getInt("vida"),
                rs.getInt("ataque")
        );
    }
}
