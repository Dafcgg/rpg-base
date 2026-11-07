package com.rpg.controller;

import com.rpg.dao.MonstruoDAO;
import com.rpg.dao.VictoriaDAO;
import com.rpg.model.Jugador;
import com.rpg.model.Monstruo;
import com.rpg.model.RankingDTO;
import com.rpg.model.Victoria;

import java.util.Collections;
import java.util.List;

/**
 * Controlador de negocio para el registro y consulta de victorias.
 * <p>
 * Orquesta la interaccion entre {@link VictoriaDAO} y {@link MonstruoDAO} para:
 * <ul>
 *   <li>Registrar una victoria cuando el jugador derrota a un monstruo.</li>
 *   <li>Intentar registrar automaticamente si el nombre del enemigo coincide
 *       con un monstruo de la BD (integracion con el juego existente).</li>
 *   <li>Mostrar el ranking global y el historial personal.</li>
 * </ul>
 * </p>
 */
public class VictoriaController {

    private final VictoriaDAO  victoriaDAO;
    private final MonstruoDAO  monstruoDAO;
    private final Jugador      jugadorActual;

    public VictoriaController(Jugador jugadorActual) {
        this.victoriaDAO  = new VictoriaDAO();
        this.monstruoDAO  = new MonstruoDAO();
        this.jugadorActual = jugadorActual;
    }

    // ---------------------------------------------------------------- registro de victoria

    /**
     * Registra una victoria del jugador actual contra el monstruo con el id indicado.
     *
     * @param monstruoId id del monstruo derrotado
     * @return {@code true} si se registro correctamente
     */
    public boolean registrarVictoria(int monstruoId) {
        if (jugadorActual == null) {
            System.out.println("[VictoriaController] No hay sesion activa. Inicia sesion para registrar victorias.");
            return false;
        }
        boolean ok = victoriaDAO.registrar(jugadorActual.getId(), monstruoId);
        if (ok) {
            System.out.println("[VictoriaController] Victoria registrada en la BD para '"
                    + jugadorActual.getUsuario() + "' (monstruoId=" + monstruoId + ").");
        } else {
            System.out.println("[VictoriaController] No se pudo registrar la victoria en la BD.");
        }
        return ok;
    }

    /**
     * Intenta registrar automaticamente una victoria buscando al enemigo por nombre en la BD.
     * <p>
     * Este metodo es el punto de integracion con el sistema de combate existente
     * ({@code MotorJuego}): cuando el jugador gana un combate rapido, se pasa el nombre
     * del enemigo y este metodo lo busca en la tabla {@code monstruos}. Si existe un
     * registro coincidente, registra la victoria automaticamente.
     * </p>
     *
     * @param nombreEnemigo nombre exacto del enemigo derrotado (tal como aparece en Bestiario)
     * @return {@code true} si se encontro el monstruo en BD y se registro la victoria
     */
    public boolean intentarRegistrarPorNombre(String nombreEnemigo) {
        if (jugadorActual == null) {
            // Silencioso: si no hay sesion, simplemente no registra
            return false;
        }
        if (nombreEnemigo == null || nombreEnemigo.isBlank()) {
            return false;
        }
        // Buscar el monstruo por nombre exacto en la BD
        List<Monstruo> coincidencias = monstruoDAO.buscarPorNombre(nombreEnemigo);
        for (Monstruo m : coincidencias) {
            if (m.getNombre().equalsIgnoreCase(nombreEnemigo)) {
                return registrarVictoria(m.getId());
            }
        }
        // No hay monstruo coincidente en la BD (puede ser un enemigo solo en el juego local)
        return false;
    }

    // ---------------------------------------------------------------- consultas

    /**
     * Obtiene el ranking global de jugadores por victorias.
     *
     * @return lista de {@link RankingDTO} ordenada de mayor a menor victorias
     */
    public List<RankingDTO> obtenerRanking() {
        List<RankingDTO> ranking = victoriaDAO.rankingPorJugador();
        if (ranking.isEmpty()) {
            System.out.println("[VictoriaController] Aun no hay victorias registradas en la BD.");
        }
        return ranking;
    }

    /**
     * Obtiene el historial de victorias del jugador actualmente autenticado.
     *
     * @return lista de victorias con nombre de monstruo y fecha
     */
    public List<Victoria> obtenerHistorialPropio() {
        if (jugadorActual == null) {
            System.out.println("[VictoriaController] No hay sesion activa.");
            return Collections.emptyList();
        }
        return victoriaDAO.victoriasDeJugador(jugadorActual.getUsuario());
    }

    /**
     * Obtiene el historial de victorias de cualquier jugador (admin puede consultar a otros).
     *
     * @param usuario nombre del jugador a consultar
     * @return lista de victorias (puede estar vacia)
     */
    public List<Victoria> obtenerHistorialDeJugador(String usuario) {
        if (usuario == null || usuario.isBlank()) {
            System.out.println("[VictoriaController] Nombre de usuario invalido.");
            return Collections.emptyList();
        }
        return victoriaDAO.victoriasDeJugador(usuario);
    }
}
