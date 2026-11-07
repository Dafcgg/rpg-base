package com.rpg.controller;

import com.rpg.dao.JugadorDAO;
import com.rpg.model.Jugador;

/**
 * Controlador de la capa de negocio para operaciones de jugadores.
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li>Validar entradas antes de delegar al DAO.</li>
 *   <li>Aplicar hashing SHA-256 a las contrasenas antes de pasarlas al DAO.</li>
 *   <li>Controlar el numero de intentos fallidos de login (maximo 3).</li>
 *   <li>Garantizar que solo admins pueden cambiar roles.</li>
 * </ul>
 * No contiene importaciones de {@code java.sql}: la logica JDBC pertenece exclusivamente a la capa DAO.
 * </p>
 */
public class JugadorController {

    private static final int MAX_INTENTOS = 3;

    private final JugadorDAO jugadorDAO;

    /** Jugador actualmente autenticado en la sesion; null si no hay sesion activa. */
    private Jugador jugadorActual;

    /** Contador de intentos fallidos de login consecutivos. */
    private int intentosFallidos;

    /** Indica si el acceso esta bloqueado por exceder los intentos permitidos. */
    private boolean bloqueado;

    public JugadorController() {
        this(null);
    }

    public JugadorController(Jugador jugadorActual) {
        this.jugadorDAO       = new JugadorDAO();
        this.jugadorActual    = jugadorActual;
        this.intentosFallidos = 0;
        this.bloqueado        = false;
    }

    // ---------------------------------------------------------------- registro

    /**
     * Registra un nuevo jugador aplicando hash SHA-256 a la contrasena.
     *
     * @param usuario     nombre de usuario deseado
     * @param contrasena  contrasena en texto plano (se hashea aqui, nunca sale sin hash)
     * @return {@code true} si el registro fue exitoso
     */
    public boolean registrar(String usuario, String contrasena) {
        if (usuario == null || usuario.isBlank()) {
            System.out.println("[JugadorController] El nombre de usuario no puede estar vacio.");
            return false;
        }
        if (contrasena == null || contrasena.length() < 4) {
            System.out.println("[JugadorController] La contrasena debe tener al menos 4 caracteres.");
            return false;
        }
        String hash   = Jugador.hashSHA256(contrasena);
        Jugador nuevo = new Jugador(usuario, hash, "jugador");
        boolean ok    = jugadorDAO.registrar(nuevo);
        if (ok) {
            System.out.println("[JugadorController] Jugador '" + usuario + "' registrado correctamente.");
        } else {
            System.out.println("[JugadorController] No se pudo registrar el jugador '" + usuario + "'.");
        }
        return ok;
    }

    // ---------------------------------------------------------------- autenticacion

    /**
     * Intenta autenticar al usuario. Si los intentos superan {@value #MAX_INTENTOS},
     * bloquea el acceso para la sesion actual.
     *
     * @param usuario    nombre de usuario
     * @param contrasena contrasena en texto plano
     * @return {@link Jugador} autenticado o {@code null} si falla o esta bloqueado
     */
    public Jugador login(String usuario, String contrasena) {
        if (bloqueado) {
            System.out.println("[JugadorController] Acceso bloqueado por multiples intentos fallidos. Reinicie la aplicacion.");
            return null;
        }
        if (usuario == null || usuario.isBlank() || contrasena == null || contrasena.isBlank()) {
            System.out.println("[JugadorController] Usuario o contrasena vacios.");
            return null;
        }

        String hash       = Jugador.hashSHA256(contrasena);
        Jugador resultado = jugadorDAO.autenticar(usuario, hash);

        if (resultado != null) {
            jugadorActual     = resultado;
            intentosFallidos  = 0;
            System.out.println("[JugadorController] Login exitoso. Bienvenido, " + resultado.getUsuario()
                    + " [" + resultado.getRol() + "]");
        } else {
            intentosFallidos++;
            int restantes = MAX_INTENTOS - intentosFallidos;
            if (restantes > 0) {
                System.out.println("[JugadorController] Credenciales incorrectas. Intentos restantes: " + restantes);
            } else {
                bloqueado = true;
                System.out.println("[JugadorController] Demasiados intentos fallidos. Acceso bloqueado.");
            }
        }
        return resultado;
    }

    /** Cierra la sesion del jugador actual. */
    public void logout() {
        if (jugadorActual != null) {
            System.out.println("[JugadorController] Sesion cerrada para: " + jugadorActual.getUsuario());
        }
        jugadorActual    = null;
        intentosFallidos = 0;
        bloqueado        = false;
    }

    // ---------------------------------------------------------------- cambio de rol

    /**
     * Cambia el rol de un jugador. Solo permitido si el jugador actual es admin.
     *
     * @param idObjetivo  id del jugador al que se le cambia el rol
     * @param nuevoRol    "admin" o "jugador"
     * @return {@code true} si el cambio fue exitoso
     */
    public boolean cambiarRol(int idObjetivo, String nuevoRol) {
        if (jugadorActual == null || !jugadorActual.esAdmin()) {
            System.out.println("[JugadorController] Solo los administradores pueden cambiar roles.");
            return false;
        }
        boolean ok = jugadorDAO.cambiarRol(idObjetivo, nuevoRol);
        if (ok) {
            System.out.println("[JugadorController] Rol del jugador id=" + idObjetivo + " cambiado a '" + nuevoRol + "'.");
        }
        return ok;
    }

    // ---------------------------------------------------------------- eliminar

    /**
     * Elimina un jugador. Solo permitido por un admin.
     *
     * @param idObjetivo id del jugador a eliminar
     * @return {@code true} si se elimino correctamente
     */
    public boolean eliminar(int idObjetivo) {
        if (jugadorActual == null || !jugadorActual.esAdmin()) {
            System.out.println("[JugadorController] Solo los administradores pueden eliminar jugadores.");
            return false;
        }
        boolean ok = jugadorDAO.eliminar(idObjetivo);
        if (ok) {
            System.out.println("[JugadorController] Jugador id=" + idObjetivo + " eliminado.");
        }
        return ok;
    }

    // ---------------------------------------------------------------- getters de sesion

    /** @return el jugador actualmente autenticado, o {@code null} si no hay sesion */
    public Jugador getJugadorActual() { return jugadorActual; }

    /** @return {@code true} si hay una sesion activa */
    public boolean haySesion()       { return jugadorActual != null; }

    /** @return {@code true} si el jugador actual tiene rol admin */
    public boolean esAdmin()         { return haySesion() && jugadorActual.esAdmin(); }

    /** @return {@code true} si el acceso esta bloqueado por intentos fallidos */
    public boolean estaBloqueado()   { return bloqueado; }
}
