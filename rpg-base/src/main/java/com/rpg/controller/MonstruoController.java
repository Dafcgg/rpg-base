package com.rpg.controller;

import com.rpg.dao.MonstruoDAO;
import com.rpg.model.Jugador;
import com.rpg.model.Monstruo;

import java.util.Collections;
import java.util.List;

/**
 * Controlador de negocio para la gestion del bestiario (tabla {@code monstruos}).
 * <p>
 * Aplica la regla de roles: <strong>solo usuarios con rol {@code admin} pueden
 * crear, editar o eliminar monstruos</strong>. Los jugadores normales pueden
 * consultar el bestiario sin restricciones.
 * </p>
 * <p>
 * Protege contra excepciones de clave foranea: antes de eliminar un monstruo,
 * verifica si tiene victorias asociadas mediante {@link MonstruoDAO#tieneVictoriasAsociadas(int)}
 * y bloquea la operacion mostrando un mensaje amigable.
 * </p>
 */
public class MonstruoController {

    private final MonstruoDAO monstruoDAO;
    private final Jugador     jugadorActual;

    /**
     * @param jugadorActual jugador de la sesion activa (puede ser null, pero operaciones
     *                      de escritura fallan si no hay sesion admin)
     */
    public MonstruoController(Jugador jugadorActual) {
        this.monstruoDAO  = new MonstruoDAO();
        this.jugadorActual = jugadorActual;
    }

    // ---------------------------------------------------------------- lectura (libre para todos)

    /**
     * Lista todos los monstruos del bestiario. Libre para cualquier usuario.
     *
     * @return lista de monstruos ordenada por nombre
     */
    public List<Monstruo> listarTodos() {
        List<Monstruo> lista = monstruoDAO.listarTodos();
        if (lista.isEmpty()) {
            System.out.println("[MonstruoController] No hay monstruos registrados en el bestiario.");
        }
        return lista;
    }

    /**
     * Obtiene un monstruo por su id. Libre para cualquier usuario.
     *
     * @param id clave primaria
     * @return {@link Monstruo} o {@code null} si no existe
     */
    public Monstruo obtenerPorId(int id) {
        Monstruo m = monstruoDAO.obtenerPorId(id);
        if (m == null) {
            System.out.println("[MonstruoController] No se encontro monstruo con id=" + id + ".");
        }
        return m;
    }

    /**
     * Busca monstruos por nombre (coincidencia parcial). Libre para cualquier usuario.
     *
     * @param termino texto a buscar
     * @return lista de coincidencias (puede estar vacia)
     */
    public List<Monstruo> buscarPorNombre(String termino) {
        if (termino == null || termino.isBlank()) {
            System.out.println("[MonstruoController] El termino de busqueda no puede estar vacio.");
            return Collections.emptyList();
        }
        return monstruoDAO.buscarPorNombre(termino);
    }

    /**
     * Lista monstruos con vida mayor o igual al minimo indicado. Libre para cualquier usuario.
     *
     * @param vidaMinima umbral de vida
     * @return lista filtrada y ordenada por vida descendente
     */
    public List<Monstruo> listarPorVidaMinima(int vidaMinima) {
        if (vidaMinima < 1) {
            System.out.println("[MonstruoController] La vida minima debe ser mayor que 0.");
            return Collections.emptyList();
        }
        return monstruoDAO.listarPorVidaMinima(vidaMinima);
    }

    // ---------------------------------------------------------------- escritura (solo admin)

    /**
     * Crea un nuevo monstruo en el bestiario. Requiere rol admin.
     *
     * @param nombre nombre del monstruo
     * @param vida   puntos de vida (> 0)
     * @param ataque puntos de ataque (> 0)
     * @return {@code true} si se creo correctamente
     */
    public boolean crear(String nombre, int vida, int ataque) {
        if (!verificarAdmin()) return false;
        if (nombre == null || nombre.isBlank()) {
            System.out.println("[MonstruoController] El nombre del monstruo no puede estar vacio.");
            return false;
        }
        if (vida <= 0 || ataque <= 0) {
            System.out.println("[MonstruoController] La vida y el ataque deben ser mayores que 0.");
            return false;
        }
        Monstruo nuevo = new Monstruo(nombre.trim(), vida, ataque);
        boolean ok = monstruoDAO.insertar(nuevo);
        if (ok) {
            System.out.println("[MonstruoController] Monstruo '" + nombre + "' creado con id=" + nuevo.getId() + ".");
        } else {
            System.out.println("[MonstruoController] No se pudo crear el monstruo '" + nombre + "'.");
        }
        return ok;
    }

    /**
     * Actualiza un monstruo existente. Requiere rol admin.
     *
     * @param id     id del monstruo a actualizar
     * @param nombre nuevo nombre
     * @param vida   nueva vida
     * @param ataque nuevo ataque
     * @return {@code true} si la actualizacion fue exitosa
     */
    public boolean actualizar(int id, String nombre, int vida, int ataque) {
        if (!verificarAdmin()) return false;
        if (nombre == null || nombre.isBlank()) {
            System.out.println("[MonstruoController] El nombre del monstruo no puede estar vacio.");
            return false;
        }
        if (vida <= 0 || ataque <= 0) {
            System.out.println("[MonstruoController] La vida y el ataque deben ser mayores que 0.");
            return false;
        }
        Monstruo m = new Monstruo(id, nombre.trim(), vida, ataque);
        boolean ok = monstruoDAO.actualizar(m);
        if (ok) {
            System.out.println("[MonstruoController] Monstruo id=" + id + " actualizado.");
        } else {
            System.out.println("[MonstruoController] No se pudo actualizar el monstruo id=" + id + ".");
        }
        return ok;
    }

    /**
     * Elimina un monstruo del bestiario. Requiere rol admin.
     * <p>
     * <strong>Proteccion FK:</strong> si el monstruo tiene victorias asociadas en la BD,
     * la eliminacion se bloquea con un mensaje amigable, evitando que explote una
     * {@code SQLException} de violacion de clave foranea.
     * </p>
     *
     * @param id id del monstruo a eliminar
     * @return {@code true} si se elimino correctamente
     */
    public boolean eliminar(int id) {
        if (!verificarAdmin()) return false;

        // Proteccion contra FK: verificar antes de intentar eliminar
        if (monstruoDAO.tieneVictoriasAsociadas(id)) {
            System.out.println("[MonstruoController] No se puede eliminar el monstruo id=" + id
                    + " porque tiene victorias registradas en la base de datos.");
            System.out.println("[MonstruoController] Elimine primero las victorias asociadas o reasigne el historial.");
            return false;
        }

        boolean ok = monstruoDAO.eliminar(id);
        if (ok) {
            System.out.println("[MonstruoController] Monstruo id=" + id + " eliminado del bestiario.");
        } else {
            System.out.println("[MonstruoController] No se pudo eliminar el monstruo id=" + id + ".");
        }
        return ok;
    }

    // ---------------------------------------------------------------- utilidad privada

    /**
     * Verifica que el jugador actual tiene rol admin.
     * Muestra mensaje de error si no es asi.
     *
     * @return {@code true} si el jugador actual es admin
     */
    private boolean verificarAdmin() {
        if (jugadorActual == null || !jugadorActual.esAdmin()) {
            System.out.println("[MonstruoController] Acceso denegado. Se requiere rol 'admin'.");
            return false;
        }
        return true;
    }
}
