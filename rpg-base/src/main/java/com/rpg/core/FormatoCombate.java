package com.rpg.core;

import com.rpg.entities.Personaje;

public final class FormatoCombate {

    private static final int ANCHO_BARRA = 10;
    private static final char CASILLA_LLENA = '#';
    private static final char CASILLA_VACIA = '-';

    private FormatoCombate() {
    }

    /** Barra de vida tipo [#####-----]. */
    public static String barraVida(int vida, int vidaMaxima) {
        int llenas = (int) Math.round((double) vida / vidaMaxima * ANCHO_BARRA);
        if (vida > 0) {
            llenas = Math.max(llenas, 1);
        }
        StringBuilder barra = new StringBuilder("[");
        for (int i = 0; i < ANCHO_BARRA; i++) {
            barra.append(i < llenas ? CASILLA_LLENA : CASILLA_VACIA);
        }
        return barra.append("]").toString();
    }

    public static String resumen(Personaje personaje) {
        return String.format("%-12s %s %3d/%-3d %s", personaje.getNombre(),
                barraVida(personaje.getVida(), personaje.getVidaMaxima()),
                personaje.getVida(), personaje.getVidaMaxima(), personaje.getEstado());
    }
}
