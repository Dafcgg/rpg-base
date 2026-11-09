package com.rpg.controller;

import com.rpg.dao.HeroeDAO;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;
import com.rpg.model.Heroe;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void unavailableDatabaseDoesNotAnnounceMissingHeroesAndEveryOperationReturnsSafely() {
        HeroeController failing = new HeroeController(new HeroeDAO(() -> {
            throw new SQLException("offline", "08001");
        }));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            assertTrue(failing.listar().isEmpty());
            assertTrue(failing.buscarPorTipo("mago").isEmpty());
            assertTrue(failing.buscarPorIds(new int[]{1}).isEmpty());
            assertNull(failing.buscarPorId(1));
            assertFalse(failing.insertar("Aria", "guerrero", 100, 1));
            assertEquals(HeroeDAO.ERROR_BD, failing.actualizarNivel(1, 4));
            assertEquals(HeroeDAO.ERROR_BD, failing.eliminar(1));
        } finally {
            System.setOut(previous);
            failing.close();
        }
        String messages = output.toString(StandardCharsets.UTF_8);
        assertFalse(messages.contains("No hay heroes"));
        assertFalse(messages.contains("no existe un heroe"));
        assertTrue(messages.contains("Fallo la operacion de BD"));
        assertFalse(messages.contains("offline"));
    }

    @Test
    void invalidDataIsRejectedBeforeAnyDatabaseAccess() {
        AtomicInteger connections = new AtomicInteger();
        HeroeController validating = new HeroeController(new HeroeDAO(() -> {
            connections.incrementAndGet();
            throw new SQLException("must not be called");
        }));
        assertFalse(validating.insertar("Bad!", "mago", 100, 1));
        assertFalse(validating.insertar("A".repeat(101), "mago", 100, 1));
        assertFalse(validating.insertar(" ", "mago", 100, 1));
        assertFalse(validating.insertar("Aria", "invalid", 100, 1));
        assertFalse(validating.insertar("Aria", "mago", 0, 1));
        assertFalse(validating.insertar("Aria", "mago", 100, 0));
        assertNull(validating.buscarPorId(0));
        assertEquals(HeroeDAO.ERROR_BD, validating.actualizarNivel(1, 0));
        assertEquals(HeroeDAO.ERROR_BD, validating.eliminar(-1));
        assertEquals(0, connections.get());
    }
}
