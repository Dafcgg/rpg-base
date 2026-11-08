package com.rpg.core;

import com.rpg.entities.Personaje;

/**
 * Interfaz funcional propia para definir y ejecutar acciones de combate dinamicas
 * entre personajes atacante y defensor.
 */
@FunctionalInterface
public interface AccionCombate {

    /**
     * Ejecuta una accion ofensiva o defensiva entre un atacante y un defensor.
     *
     * @param atacante Personaje que ejecuta la accion.
     * @param defensor Personaje que recibe o interactua con la accion.
     * @return Mensaje descriptivo del resultado del combate para el log.
     */
    String ejecutar(Personaje atacante, Personaje defensor);
}
