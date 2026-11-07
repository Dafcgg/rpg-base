package com.rpg.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agregación: disolver el escuadron no destruye a los personajes.
 *
 * <p>Implementa {@link Serializable} para permitir que el estado de un torneo
 * o escuadrón pueda incluirse en un guardado de partida.</p>
 */
public class Escuadron implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String nombre;
    private final ArrayList<Personaje> miembros = new ArrayList<>();

    public Escuadron(String nombre) {
        this.nombre = nombre;
    }

    public boolean agregar(Personaje personaje) {
        if (personaje == null || miembros.contains(personaje)) {
            return false;
        }
        return miembros.add(personaje);
    }

    public boolean remover(Personaje personaje) {
        return miembros.remove(personaje);
    }

    public int contarVivos() {
        int vivos = 0;
        for (Personaje personaje : miembros) {
            if (personaje.estaVivo()) {
                vivos++;
            }
        }
        return vivos;
    }

    public void disolver() {
        miembros.clear();
    }

    public String getNombre() {
        return nombre;
    }

    public List<Personaje> getMiembros() {
        return Collections.unmodifiableList(miembros);
    }
}
