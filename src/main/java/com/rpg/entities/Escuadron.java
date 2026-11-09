package com.rpg.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Escuadron sin nombre"); // FIX
        this.nombre = nombre;
    }

    public synchronized boolean agregar(Personaje personaje) {
        if (personaje == null || miembros.contains(personaje)) {
            return false;
        }
        return miembros.add(personaje);
    }

    public synchronized boolean remover(Personaje personaje) {
        return miembros.remove(personaje);
    }

    /**
     * Remueve del escuadron a todos los combatientes que cumplan con el predicado funcional.
     * Utiliza removeIf de Collection sin recurrir a Streams de forma sincronizada.
     *
     * @param criterio Filtro de seleccion (ej. p -> !p.estaVivo() o Personaje::estaVivo negado)
     * @return true si al menos un personaje fue removido
     */
    public synchronized boolean removerSi(Predicate<Personaje> criterio) {
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
    public synchronized boolean limpiarMuertos() {
        return removerSi(Predicate.not(Personaje::estaVivo));
    }

    public synchronized int contarVivos() {
        int vivos = 0;
        for (Personaje personaje : miembros) {
            if (personaje.estaVivo()) {
                vivos++;
            }
        }
        return vivos;
    }

    public synchronized void disolver() {
        miembros.clear();
    }

    public String getNombre() {
        return nombre;
    }

    public synchronized List<Personaje> getMiembros() {
        return Collections.unmodifiableList(new ArrayList<>(miembros));
    }

    /**
     * Retorna una copia instantanea en Stream para evitar ConcurrentModificationException si otros hilos mutan miembros.
     */
    public synchronized Stream<Personaje> stream() {
        return new ArrayList<>(miembros).stream();
    }

    /**
     * Evalua si queda al menos un combatiente con vida mediante Stream.anyMatch().
     */
    public synchronized boolean tieneMiembrosVivos() {
        return stream().anyMatch(Personaje::estaVivo);
    }

    /**
     * Obtiene los nombres de los miembros vivos usando lista.stream(), filter(),
     * peek() para logging silencioso de depuracion, map() y collect(Collectors.toUnmodifiableList()).
     */
    public synchronized List<String> obtenerNombresVivos(boolean debug) {
        Stream<Personaje> flujo = stream().filter(Personaje::estaVivo);
        if (debug) {
            flujo = flujo.peek(p -> System.out.println("[Debug-Escuadron] Procesando: " + p.getNombre()));
        }
        return flujo.map(Personaje::getNombre)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Aplana una lista de escuadrones mediante flatMap() a un solo flujo continuo de combatientes vivos.
     */
    public static List<Personaje> aplanarCombatientesVivos(List<Escuadron> escuadrones) {
        if (escuadrones == null || escuadrones.isEmpty()) {
            return List.of();
        }
        return escuadrones.stream()
                .filter(java.util.Objects::nonNull) // FIX: tolerar escuadrones ausentes como la lista ausente.
                .flatMap(Escuadron::stream)
                .filter(Personaje::estaVivo)
                .collect(Collectors.toList());
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        java.io.ObjectOutputStream.PutField campos = out.putFields();
        synchronized (this) {
            campos.put("nombre", nombre);
            campos.put("miembros", new ArrayList<>(miembros));
        }
        out.writeFields(); // FIX: serializar instantanea sin mantener un bloqueo sobre otros personajes.
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (nombre == null || nombre.isBlank() || miembros == null) {
            throw new java.io.InvalidObjectException("Escuadron guardado invalido");
        }
        for (Object miembro : miembros) {
            if (!(miembro instanceof Personaje)) throw new java.io.InvalidObjectException("Miembro invalido");
        }
    }
}
