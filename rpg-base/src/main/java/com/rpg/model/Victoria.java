package com.rpg.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * POJO que representa un registro de la tabla {@code victorias}.
 * Contiene el id del jugador, el id del monstruo y la fecha del combate.
 * Soporta un campo opcional {@code nombreMonstruo} para transportar
 * el nombre del enemigo en consultas con JOIN sin depender de clases internas del DAO.
 */
public class Victoria {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int           id;
    private int           jugadorId;
    private int           monstruoId;
    private String        nombreMonstruo; // Nombre del monstruo recuperado en JOINs
    private LocalDateTime fecha;

    // ---------------------------------------------------------------- constructores

    public Victoria() {}

    public Victoria(int jugadorId, int monstruoId) {
        this.jugadorId      = jugadorId;
        this.monstruoId     = monstruoId;
        this.nombreMonstruo = null;
        this.fecha          = LocalDateTime.now();
    }

    public Victoria(int jugadorId, int monstruoId, String nombreMonstruo, LocalDateTime fecha) {
        this.jugadorId      = jugadorId;
        this.monstruoId     = monstruoId;
        this.nombreMonstruo = nombreMonstruo;
        this.fecha          = fecha;
    }

    // ---------------------------------------------------------------- getters / setters

    public int getId()                       { return id; }
    public void setId(int id)                { this.id = id; }

    public int getJugadorId()                { return jugadorId; }
    public void setJugadorId(int jid)        { this.jugadorId = jid; }

    public int getMonstruoId()               { return monstruoId; }
    public void setMonstruoId(int mid)       { this.monstruoId = mid; }

    public String getNombreMonstruo()        { return nombreMonstruo; }
    public void setNombreMonstruo(String n)  { this.nombreMonstruo = n; }

    public LocalDateTime getFecha()          { return fecha; }
    public void setFecha(LocalDateTime f)    { this.fecha = f; }

    public String getFechaFormateada() {
        return fecha != null ? fecha.format(FORMATO) : "desconocida";
    }

    @Override
    public String toString() {
        if (nombreMonstruo != null) {
            return String.format("  - %-18s  %s", nombreMonstruo, getFechaFormateada());
        }
        return "Victoria{jugadorId=" + jugadorId + ", monstruoId=" + monstruoId
                + ", fecha=" + getFechaFormateada() + "}";
    }
}
