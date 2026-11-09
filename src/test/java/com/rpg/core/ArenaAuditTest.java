package com.rpg.core;

import com.rpg.entities.Guerrero;
import com.rpg.entities.Personaje;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class ArenaAuditTest {
    private final RegistroCombate registro = new RegistroCombate();

    private Arena arena(Random azar) {
        registro.setConsumidorLog(linea -> { });
        return new Arena(registro, azar);
    }

    @Test
    void maximumLevelAndCriticalDamageSaturateInsteadOfWrapping() {
        Guerrero atacante = new Guerrero("Maximo");
        atacante.setNivel(Integer.MAX_VALUE);
        Guerrero defensor = new Guerrero("Objetivo");
        defensor.setVidaMaxima(Integer.MAX_VALUE);
        defensor.restaurarVida();
        Arena arena = arena(new Random() {
            @Override public int nextInt(int bound) { return 0; }
        });
        assertEquals(Integer.MAX_VALUE, Arena.CALCULADOR_DANIO_BASE.apply(atacante, defensor));
        int antes = defensor.getVida();
        assertDoesNotThrow(() -> arena.atacar(atacante, defensor));
        assertTrue(defensor.getVida() < antes);
        assertTrue(defensor.getVida() >= 0);
    }

    @Test
    void magicalDamageAtMaximumLevelStillDamagesTheTarget() {
        Guerrero atacante = new Guerrero("Maximo");
        atacante.setNivel(Integer.MAX_VALUE);
        Guerrero defensor = new Guerrero("Objetivo");
        Arena.ATAQUE_MAGICO.ejecutar(atacante, defensor);
        assertEquals(0, defensor.getVida());
    }

    @Test
    void deadActorsCannotDealPhysicalOrSpecialDamage() {
        Guerrero atacante = new Guerrero("Caido");
        Guerrero defensor = new Guerrero("Objetivo");
        atacante.setVida(0);
        Arena arena = arena(new Random(7));
        int antes = defensor.getVida();
        arena.atacar(atacante, defensor);
        assertFalse(arena.intentarEspecial(atacante, defensor));
        Arena.ATAQUE_FISICO.ejecutar(atacante, defensor);
        assertEquals(antes, defensor.getVida());
    }

    @Test
    void interruptedDuelHasNoInventedWinnerAndPreservesInterruption() {
        Arena arena = arena(new Random(8));
        Thread.currentThread().interrupt();
        try {
            Personaje ganador = arena.duelo(new Guerrero("Uno"), new Guerrero("Dos"));
            assertNull(ganador);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void selfAttackAndInvalidHealthDenominatorFailExplicitly() {
        Guerrero heroe = new Guerrero("Solo");
        assertThrows(IllegalArgumentException.class, () -> arena(new Random()).duelo(heroe, heroe));
        assertThrows(IllegalArgumentException.class, () -> FormatoCombate.barraVida(1, 0));
        assertEquals("[##########]", FormatoCombate.barraVida(Integer.MAX_VALUE, 100));
        assertEquals("[----------]", FormatoCombate.barraVida(-1, 100));
    }
}
