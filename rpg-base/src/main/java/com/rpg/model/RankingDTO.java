package com.rpg.model;

/**
 * Data Transfer Object para el ranking de victorias por jugador.
 * <p>
 * Generado por {@code VictoriaDAO.rankingPorJugador()} a partir de un
 * {@code SELECT … JOIN … GROUP BY} entre las tablas {@code jugadores} y {@code victorias}.
 * No mapea directamente a una tabla de la BD.
 * </p>
 */
public class RankingDTO {

    private final String usuario;
    private final int    totalVictorias;

    public RankingDTO(String usuario, int totalVictorias) {
        this.usuario        = usuario;
        this.totalVictorias = totalVictorias;
    }

    public String getUsuario()       { return usuario; }
    public int getTotalVictorias()   { return totalVictorias; }

    @Override
    public String toString() {
        return String.format("%-20s %d victorias", usuario, totalVictorias);
    }
}
