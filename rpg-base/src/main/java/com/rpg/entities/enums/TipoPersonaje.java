package com.rpg.entities.enums;

public enum TipoPersonaje {
    GUERRERO(4, 5),
    MAGO(8, 0),
    MONSTRUO(0, 0);

    private final int bonificadorAtaque;
    private final int bonificadorDefensa;

    TipoPersonaje(int bonificadorAtaque, int bonificadorDefensa) {
        this.bonificadorAtaque = bonificadorAtaque;
        this.bonificadorDefensa = bonificadorDefensa;
    }

    public int getBonificadorAtaque() {
        return bonificadorAtaque;
    }

    public int getBonificadorDefensa() {
        return bonificadorDefensa;
    }
}
