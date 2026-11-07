package com.rpg.dao;

import com.rpg.model.Jugador;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Acceso a datos para la tabla {@code jugadores}.
 * <p>
 * <strong>Regla de oro:</strong> el 100% de las sentencias SQL usan {@link PreparedStatement}
 * con parametros {@code ?} para evitar inyeccion SQL. Nunca se concatena texto del usuario.
 * </p>
 * <p>
 * Cada metodo gestiona su propio {@link Connection} dentro de un bloque
 * {@code try-with-resources}, garantizando la liberacion de todos los recursos JDBC
 * ({@code Connection}, {@code PreparedStatement}, {@code ResultSet}) aunque ocurra
 * una excepcion.
 * </p>
 */
public class JugadorDAO {

    // ---------------------------------------------------------------- SQL

    private static final String SQL_REGISTRAR =
            "INSERT INTO jugadores (usuario, clave, rol) VALUES (?, ?, 'jugador')";

    private static final String SQL_AUTENTICAR =
            "SELECT id, usuario, clave, rol FROM jugadores WHERE usuario = ? AND clave = ?";

    private static final String SQL_CAMBIAR_ROL =
            "UPDATE jugadores SET rol = ? WHERE id = ?";

    private static final String SQL_ELIMINAR =
            "DELETE FROM jugadores WHERE id = ?";

    private static final String SQL_EXISTE_USUARIO =
            "SELECT COUNT(*) FROM jugadores WHERE usuario = ?";

    // ---------------------------------------------------------------- metodos publicos

    /**
     * Registra un nuevo jugador en la base de datos con rol 'jugador'.
     * La clave almacenada es el hash SHA-256 de la contrasena original.
     *
     * @param jugador POJO con usuario y claveHash ya calculado
     * @return {@code true} si la insercion fue exitosa
     */
    public boolean registrar(Jugador jugador) {
        if (existeUsuario(jugador.getUsuario())) {
            System.out.println("[JugadorDAO] El usuario '" + jugador.getUsuario() + "' ya existe.");
            return false;
        }
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(SQL_REGISTRAR)) {
                ps.setString(1, jugador.getUsuario());
                ps.setString(2, jugador.getClaveHash());
                int filas = ps.executeUpdate();
                return filas > 0;
            }
        } catch (SQLException e) {
            System.out.println("[JugadorDAO] Error al registrar jugador: " + e.getMessage());
            return false;
        }
    }

    /**
     * Autentica un jugador verificando usuario y hash SHA-256 de la clave.
     * <p>
     * Gracias al uso de {@link PreparedStatement}, intentos de inyeccion SQL como
     * {@code admin' OR 1=1 -- } son tratados como literales de texto y NO producen
     * acceso no autorizado.
     * </p>
     *
     * @param usuario   nombre de usuario introducido
     * @param claveHash hash SHA-256 de la contrasena introducida
     * @return {@link Jugador} autenticado, o {@code null} si las credenciales no coinciden
     */
    public Jugador autenticar(String usuario, String claveHash) {
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return null;
            try (PreparedStatement ps = conn.prepareStatement(SQL_AUTENTICAR)) {
                ps.setString(1, usuario);
                ps.setString(2, claveHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Jugador j = new Jugador();
                        j.setId(rs.getInt("id"));
                        j.setUsuario(rs.getString("usuario"));
                        j.setClaveHash(rs.getString("clave"));
                        j.setRol(rs.getString("rol"));
                        return j;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[JugadorDAO] Error al autenticar: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cambia el rol de un jugador (solo llamado por usuarios admin desde el Controller).
     *
     * @param id  identificador del jugador
     * @param rol nuevo rol: "admin" o "jugador"
     * @return {@code true} si se actualizo al menos una fila
     */
    public boolean cambiarRol(int id, String rol) {
        if (!rol.equals("admin") && !rol.equals("jugador")) {
            System.out.println("[JugadorDAO] Rol invalido: " + rol + ". Solo se permite 'admin' o 'jugador'.");
            return false;
        }
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(SQL_CAMBIAR_ROL)) {
                ps.setString(1, rol);
                ps.setInt(2, id);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.out.println("[JugadorDAO] Error al cambiar rol: " + e.getMessage());
            return false;
        }
    }

    /**
     * Elimina un jugador de la base de datos por su id.
     * Si el jugador tiene victorias asociadas, MySQL lanzara una excepcion de FK
     * que se captura y muestra de forma amigable.
     *
     * @param id identificador del jugador a eliminar
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
            System.out.println("[JugadorDAO] Error al eliminar jugador (posible restriccion FK): " + e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------- metodos privados

    /**
     * Verifica si ya existe un jugador con el nombre de usuario dado.
     *
     * @param usuario nombre a verificar
     * @return {@code true} si ya existe
     */
    private boolean existeUsuario(String usuario) {
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(SQL_EXISTE_USUARIO)) {
                ps.setString(1, usuario);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[JugadorDAO] Error al verificar usuario: " + e.getMessage());
        }
        return false;
    }
}
