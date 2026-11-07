package com.rpg.exceptions;

/** Excepcion no verificada: la habilidad costaria mas vida de la que se tiene. */
public class VidaInsuficienteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public VidaInsuficienteException(String nombre, int costo, int vidaActual) {
        super(nombre + " no puede pagar " + costo + " de vida (le quedan " + vidaActual + ")");
    }
}
