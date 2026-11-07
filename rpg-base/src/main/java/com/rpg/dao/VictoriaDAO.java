package com.rpg.dao;

import com.rpg.model.RankingDTO;
import com.rpg.model.Victoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos para la tabla {@code victorias}.
 * <p>
 * Incluye el registro de nuevas victorias y consultas analiticas (ranking y
 * historial personal) basadas en JOINs entre {@code victorias}, {@code jugadores}
 * y {@code monstruos}.
 * </p>
 *
 * <h2>executeQuery vs executeUpdate — recordatorio</h2>
 * <ul>
 *   <li>{@code executeUpdate()} - INSERT/UPDATE/DELETE: retorna filas afectadas (int).</li>
 *   <li>{@code executeQuery()}  - SELECT: retorna un {@link ResultSet} con las filas.</li>
 * </ul>
 */
public class VictoriaDAO {

    // ---------------------------------------------------------------- SQL

    private static final String SQL_REGISTRAR =
            "INSERT INTO victorias (jugador_id, monstruo_id, fecha) VALUES (?, ?, ?)";

    /**
     * Ranking de jugadores: cuenta victorias por jugador y ordena de mayor a menor.
     * JOIN entre jugadores y victorias; los jugadores sin victorias no aparecen (INNER JOIN).
     */
    private static final String SQL_RANKING =
            "SELECT j.usuario, COUNT(v.id) AS total " +
            "FROM victorias v " +
            "INNER JOIN jugadores j ON v.jugador_id = j.id " +
            "GROUP BY j.id, j.usuario " +
            "ORDER BY total DESC";

    /**
     * Historial de victorias de un jugador concreto, con nombre del monstruo y fecha.
     */
    private static final String SQL_VICTORIAS_DE_JUGADOR =
            "SELECT m.nombre AS monstruo, v.fecha " +
            "FROM victorias v " +
            "INNER JOIN jugadores j  ON v.jugador_id  = j.id " +
            "INNER JOIN monstruos m ON v.monstruo_id = m.id " +
            "WHERE j.usuario = ? " +
            "ORDER BY v.fecha DESC";

    // ---------------------------------------------------------------- metodos publicos

    /**
     * Registra una nueva victoria para un jugador contra un monstruo.
     * La fecha se pasa como {@link Timestamp} desde Java para consistencia.
     *
     * @param jugadorId  id del jugador ganador
     * @param monstruoId id del monstruo derrotado
     * @return {@code true} si la insercion fue exitosa
     */
    public boolean registrar(int jugadorId, int monstruoId) {
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return false;
            try (PreparedStatement ps = conn.prepareStatement(SQL_REGISTRAR)) {
                ps.setInt(1, jugadorId);
                ps.setInt(2, monstruoId);
                ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.out.println("[VictoriaDAO] Error al registrar victoria: " + e.getMessage());
            return false;
        }
    }

    /**
     * Genera el ranking global de jugadores ordenado por total de victorias descendente.
     *
     * @return lista de {@link RankingDTO} (puede estar vacia si no hay victorias)
     */
    public List<RankingDTO> rankingPorJugador() {
        List<RankingDTO> ranking = new ArrayList<>();
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return ranking;
            try (PreparedStatement ps = conn.prepareStatement(SQL_RANKING);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ranking.add(new RankingDTO(
                            rs.getString("usuario"),
                            rs.getInt("total")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("[VictoriaDAO] Error al obtener ranking: " + e.getMessage());
        }
        return ranking;
    }

    /**
     * Obtiene el historial de victorias de un jugador especifico.
     * Retorna pares (nombre del monstruo, fecha) como objetos {@link VictoriaConNombre}.
     *
     * @param usuario nombre de usuario del jugador
     * @return lista de victorias del jugador (puede estar vacia)
     */
    public List<Victoria> victoriasDeJugador(String usuario) {
        List<Victoria> lista = new ArrayList<>();
        try (Connection conn = ConexionDB.obtenerConexion()) {
            if (conn == null) return lista;
            try (PreparedStatement ps = conn.prepareStatement(SQL_VICTORIAS_DE_JUGADOR)) {
                ps.setString(1, usuario);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Victoria v = new Victoria();
                        v.setNombreMonstruo(rs.getString("monstruo"));
                        Timestamp ts = rs.getTimestamp("fecha");
                        if (ts != null) {
                            v.setFecha(ts.toLocalDateTime());
                        }
                        lista.add(v);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[VictoriaDAO] Error al obtener victorias del jugador: " + e.getMessage());
        }
        return lista;
    }
}
