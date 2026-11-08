package com.rpg.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Agregacion: disolver el escuadron no destruye a los personajes.
 *
 * <p>Implementa {@link Serializable} para permitir que el estado de un torneo
 * o escuadron pueda incluirse en un guardado de partida.</p>
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

    /**
     * Remueve del escuadron a todos los combatientes que cumplan con el predicado funcional.
     * Utiliza removeIf de Collection sin recurrir a Streams.
     *
     * @param criterio Filtro de seleccion (ej. p -> !p.estaVivo() o Personaje::estaVivo negado)
     * @return true si al menos un personaje fue removido
     */
    public boolean removerSi(Predicate<Personaje> criterio) {
        if (criterio == null) {
            return false;
        }
        return miembros.removeIf(criterio);
    }

    /**
     * Limpia los combatientes caidos del escuadron aplicando Predicate::not con estaVivo.
     *
     * @return true si se removio al menos un miembro caido
     */
    public boolean limpiarMuertos() {
        return removerSi(Predicate.not(Personaje::estaVivo));
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
