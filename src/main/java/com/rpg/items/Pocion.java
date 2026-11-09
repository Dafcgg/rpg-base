package com.rpg.items;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.enums.RaridadItem;

import java.io.Serial;
import java.io.Serializable;

/**
 * Pocion consumible del inventario del jugador.
 *
 * <p>Implementa {@link Serializable} porque forma parte del inventario del personaje,
 * que se incluye en el grafo de serializacion al guardar la partida.</p>
 */
// TALLER ARCHIVOS: implementa Serializable (guarda un EstadoEfecto, que tambien debe serlo).
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
        if (nombre == null || nombre.isBlank() || curacion < 0 || raridad == null) {
            throw new IllegalArgumentException("Pocion invalida"); // FIX: impedir curaciones negativas y NPE tardios.
        }
        this.nombre = nombre;
        this.curacion = curacion;
        this.raridad = raridad;
        this.efecto = efecto == null ? null : efecto.copia();
        this.purifica = purifica;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCuracionReal() {
        return (int) (curacion * raridad.getMultiplicadorStat());
    }

    public EstadoEfecto getEfecto() {
        return efecto == null ? null : efecto.copia(); // FIX: proteger el efecto de la pocion de mutacion externa.
    }

    public boolean isPurifica() {
        return purifica;
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (nombre == null || nombre.isBlank() || curacion < 0 || raridad == null) {
            throw new java.io.InvalidObjectException("Pocion guardada invalida"); // FIX
        }
    }
}
