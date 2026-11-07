package com.rpg.items;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.enums.RaridadItem;

import java.io.Serial;
import java.io.Serializable;

/**
 * Poción consumible del inventario del jugador.
 *
 * <p>Implementa {@link Serializable} porque forma parte del inventario del personaje,
 * que se incluye en el grafo de serialización al guardar la partida.</p>
 */
// TALLER ARCHIVOS: implementa Serializable (guarda un EstadoEfecto, que también debe serlo).
public class Pocion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final int curacion;
    private final RaridadItem raridad;
    private final EstadoEfecto efecto;
    private final boolean purifica;

    public Pocion(String nombre, int curacion, RaridadItem raridad) {
        this(nombre, curacion, raridad, null, false);
    }

    /**
     * @param efecto   efecto que se aplica al usarla (por ejemplo un HoT), o null
     * @param purifica si es true, elimina los efectos de dano continuo (antidoto)
     */
    public Pocion(String nombre, int curacion, RaridadItem raridad, EstadoEfecto efecto, boolean purifica) {
        this.nombre = nombre;
        this.curacion = curacion;
        this.raridad = raridad;
        this.efecto = efecto;
        this.purifica = purifica;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCuracionReal() {
        return (int) (curacion * raridad.getMultiplicadorStat());
    }

    public EstadoEfecto getEfecto() {
        return efecto;
    }

    public boolean isPurifica() {
        return purifica;
    }
}
