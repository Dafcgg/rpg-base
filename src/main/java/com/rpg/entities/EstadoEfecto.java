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
        // FIX: rechazar efectos que fallarian despues dentro de un hilo de combate.
        if (nombre == null || nombre.isBlank() || tipo == null || valorPorTick < 0 || duracion < 0) {
            throw new IllegalArgumentException("Efecto invalido");
        }
        this.nombre = nombre;
        this.tipo = tipo;
        this.valorPorTick = valorPorTick;
        this.turnosRestantes = duracion;
    }

    public synchronized EstadoEfecto copia() {
        return new EstadoEfecto(nombre, tipo, valorPorTick, turnosRestantes);
    }

    public synchronized void consumirTurno() {
        if (turnosRestantes > 0) turnosRestantes--; // FIX: mantener el invariante de duracion >= 0.
    }

    /** Marca la copia como terminada (usado al cancelar el hilo del efecto). */
    public synchronized void finalizar() {
        turnosRestantes = 0;
    }

    public synchronized boolean haTerminado() {
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

    public synchronized int getTurnosRestantes() {
        return turnosRestantes;
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (nombre == null || nombre.isBlank() || tipo == null || valorPorTick < 0 || turnosRestantes < 0) {
            throw new java.io.InvalidObjectException("Efecto guardado invalido"); // FIX
        }
    }

    @Serial
    private synchronized void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        out.defaultWriteObject();
    }
}
