package com.rpg.controller;

import com.rpg.dao.HeroeDAO;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;
import com.rpg.model.Heroe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroeControllerTest {

    private final HeroeController controller = new HeroeController(new HeroeDAO());

    @Test
    void mapsWarriorTypeHealthAndCombatLevel() {
        Heroe heroe = new Heroe(1, "Aria", "guerrero", 155, 5);

        Personaje personaje = controller.convertirAEntity(heroe);
        Guerrero nivelUno = new Guerrero("Aria");

        assertInstanceOf(Guerrero.class, personaje);
        assertEquals(155, personaje.getVidaMaxima());
        assertEquals(155, personaje.getVida());
        assertEquals(5, personaje.getNivel());
        assertTrue(personaje.calcularDanio() > nivelUno.calcularDanio());
    }

    @Test
    void mapsMageTypeHealthAndCombatLevel() {
        Heroe heroe = new Heroe(2, "Merlin", "mago", 110, 3);

        Personaje personaje = controller.convertirAEntity(heroe);
        Mago nivelUno = new Mago("Merlin");

        assertInstanceOf(Mago.class, personaje);
        assertEquals(110, personaje.getVidaMaxima());
        assertEquals(110, personaje.getVida());
        assertEquals(3, personaje.getNivel());
        assertTrue(personaje.calcularDanio() > nivelUno.calcularDanio());
    }
}
