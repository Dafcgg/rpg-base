package com.rpg.exceptions;

/** Se lanza al agregar un elemento a un inventario que ya esta lleno. */
public class CapacidadExcedidaException extends JuegoException {

    private static final long serialVersionUID = 1L;

    public CapacidadExcedidaException(int capacidad) {
        super("Inventario lleno (capacidad: " + capacidad + ")");
    }
}
