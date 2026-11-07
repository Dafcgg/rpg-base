package com.rpg.exceptions;

/** Base de las excepciones verificadas del dominio del juego. */
public abstract class JuegoException extends Exception {

    private static final long serialVersionUID = 1L;

    protected JuegoException(String mensaje) {
        super(mensaje);
    }
}
