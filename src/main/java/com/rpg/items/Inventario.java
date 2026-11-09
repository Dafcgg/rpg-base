package com.rpg.items;

import com.rpg.exceptions.CapacidadExcedidaException;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Contenedor generico con capacidad limitada: {@code Inventario<Pocion>} solo acepta pociones.
 *
 * <p>Implementa {@link Serializable} para que el inventario del personaje se persista
 * correctamente. Los elementos {@code T} tambien deben ser serializables (hoy {@link Pocion}).</p>
 */
// TALLER ARCHIVOS: implementa Serializable; los elementos T (hoy Pocion) tambien lo necesitan.
public class Inventario<T> implements Iterable<T>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ArrayList<T> elementos = new ArrayList<>();
    private final int capacidad;

    public Inventario(int capacidad) {
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser positiva");
        }
        this.capacidad = capacidad;
    }

    public synchronized void agregar(T elemento) throws CapacidadExcedidaException {
        Objects.requireNonNull(elemento, "elemento"); // FIX: no aceptar huecos que rompan el menu de pociones.
        if (elementos.size() >= capacidad) {
            throw new CapacidadExcedidaException(capacidad);
        }
        elementos.add(elemento);
    }

    public synchronized T obtener(int indice) {
        return elementos.get(indice);
    }

    public synchronized T quitar(int indice) {
        return elementos.remove(indice);
    }

    public synchronized int tamanio() {
        return elementos.size();
    }

    public synchronized boolean estaVacio() {
        return elementos.isEmpty();
    }

    @Override
    public synchronized Iterator<T> iterator() {
        return List.copyOf(elementos).iterator(); // FIX: iterar una instantanea, sin exponer remove ni provocar CME.
    }

    @Serial
    private synchronized void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        out.defaultWriteObject();
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (capacidad <= 0 || elementos == null || elementos.size() > capacidad || elementos.contains(null)) {
            throw new java.io.InvalidObjectException("Inventario guardado invalido"); // FIX
        }
    }
}
