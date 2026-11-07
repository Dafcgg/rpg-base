package com.rpg.entities;

import com.rpg.entities.enums.RaridadItem;

import java.io.Serial;
import java.io.Serializable;

/**
 * Arma equipable por un {@link Personaje}.
 *
 * <p>Implementa {@link Serializable} porque {@link Personaje#getArma()} es parte del
 * grafo de objetos que se serializa al guardar una partida.</p>
 */
public class Arma implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final int danioBase;
    private final RaridadItem raridad;

    public Arma(String nombre, int danioBase, RaridadItem raridad) {
        if (danioBase < 0) {
            throw new IllegalArgumentException("El danio del arma no puede ser negativo");
        }
        this.nombre = nombre;
        this.danioBase = danioBase;
        this.raridad = raridad;
    }

    public double getDanioEfectivo() {
        return danioBase * raridad.getMultiplicadorStat();
    }

    public String getNombre() {
        return nombre;
    }
}
