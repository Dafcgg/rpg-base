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

    // FIX: un unico orden de bloqueo protege ambos extremos de la relacion bidireccional.
    static final Object MEMBRESIA_LOCK = new Object();

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final ArrayList<Personaje> miembros = new ArrayList<>();

    public Gremio(String nombre) {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Gremio sin nombre");
        this.nombre = nombre;
    }

    public void agregarMiembro(Personaje personaje) {
        synchronized (MEMBRESIA_LOCK) {
            if (personaje == null || miembros.contains(personaje)) return;
            miembros.add(personaje);
            if (personaje.getGremio() != this) personaje.unirseAGremio(this);
        }
    }

    public void removerMiembro(Personaje personaje) {
        synchronized (MEMBRESIA_LOCK) {
            if (!miembros.remove(personaje)) return;
            if (personaje.getGremio() == this) personaje.abandonarGremio();
        }
    }

    public String getNombre() {
        return nombre;
    }

    public List<Personaje> getMiembros() {
        synchronized (MEMBRESIA_LOCK) {
            return List.copyOf(miembros); // FIX: no devolver una vista viva de la coleccion mutable.
        }
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        java.io.ObjectOutputStream.PutField campos = out.putFields();
        synchronized (MEMBRESIA_LOCK) {
            campos.put("nombre", nombre);
            campos.put("miembros", new ArrayList<>(miembros));
        }
        out.writeFields(); // FIX: soltar el bloqueo de membresia antes de recorrer el grafo ciclico.
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (nombre == null || nombre.isBlank() || miembros == null) {
            throw new java.io.InvalidObjectException("Gremio guardado invalido");
        }
        for (Object miembro : miembros) {
            if (!(miembro instanceof Personaje)) throw new java.io.InvalidObjectException("Miembro invalido");
        }
    }
}
