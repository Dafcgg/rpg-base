package com.rpg.entities;

import com.rpg.entities.enums.TipoEfecto;

import java.io.Serial;
import java.io.Serializable;

/**
 * Efecto periodico (DoT o HoT): cambia la vida un valor fijo en cada tick durante cierta duracion.
 *
 * <p>Implementa {@link Serializable} porque es referenciado desde {@link Personaje} (campo
 * {@code efectos}) y desde {@link com.rpg.items.Pocion} (campo {@code efecto}),
 * ambas clases serializables.</p>
 */
public class EstadoEfecto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final TipoEfecto tipo;
    private final int valorPorTick;
    private int turnosRestantes;

    public EstadoEfecto(String nombre, TipoEfecto tipo, int valorPorTick, int duracion) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.valorPorTick = valorPorTick;
        this.turnosRestantes = duracion;
    }

    public EstadoEfecto copia() {
        return new EstadoEfecto(nombre, tipo, valorPorTick, turnosRestantes);
    }

    public void consumirTurno() {
        turnosRestantes--;
    }

    /** Marca la copia como terminada (usado al cancelar el hilo del efecto). */
    public void finalizar() {
        turnosRestantes = 0;
    }

    public boolean haTerminado() {
        return turnosRestantes <= 0;
    }

    public boolean esDanio() {
        return tipo == TipoEfecto.DANIO_CONTINUO;
    }

    public String getNombre() {
        return nombre;
    }

    public int getValorPorTick() {
        return valorPorTick;
    }

    public int getTurnosRestantes() {
        return turnosRestantes;
    }
}
