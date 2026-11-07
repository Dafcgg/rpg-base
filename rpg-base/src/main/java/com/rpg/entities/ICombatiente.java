package com.rpg.entities;

/** Contrato minimo de cualquier entidad que participa en un combate. */
public interface ICombatiente {

    /** Procesa un golpe entrante aplicando la mitigacion propia. */
    void defender(int danoRecibido);

    boolean estaVivo();
}
