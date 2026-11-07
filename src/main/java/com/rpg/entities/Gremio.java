package com.rpg.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agregacion: el gremio guarda referencias a personajes que existen por su cuenta.
 *
 * <p>Implementa {@link Serializable} porque es referenciado desde {@link Personaje#getGremio()}
 * y se incluye en el grafo de serializacion de la partida.</p>
 *
 * <p><strong>Ciclo de referencia:</strong> {@code Gremio} referencia a {@code List<Personaje>}
 * y cada {@code Personaje} referencia de vuelta a su {@code Gremio}. Java Serialization
 * maneja estos ciclos correctamente mediante su grafo interno de objetos ya escritos,
 * evitando recursion infinita y duplicacion.</p>
 */
public class Gremio implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final ArrayList<Personaje> miembros = new ArrayList<>();

    public Gremio(String nombre) {
        this.nombre = nombre;
    }

    public void agregarMiembro(Personaje personaje) {
        if (personaje == null || miembros.contains(personaje)) {
            return;
        }
        miembros.add(personaje);
        if (personaje.getGremio() != this) {
            personaje.unirseAGremio(this);
        }
    }

    public void removerMiembro(Personaje personaje) {
        if (!miembros.remove(personaje)) {
            return;
        }
        if (personaje.getGremio() == this) {
            personaje.abandonarGremio();
        }
    }

    public String getNombre() {
        return nombre;
    }

    public List<Personaje> getMiembros() {
        return Collections.unmodifiableList(miembros);
    }
}
