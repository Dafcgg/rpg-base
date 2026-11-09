package com.rpg.core;

import com.rpg.entities.Guerrero;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.entities.EstadoEfecto;
import com.rpg.persistencia.GestorPartidas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class MotorJuegoAuditTest {
    @TempDir Path partidas;

    @Test
    void dyingWhileChoosingAnActionCountsAsDefeatInsteadOfEscape() {
        Guerrero jugador = new Guerrero("Caido");
        EntradaConsola entrada = new EntradaConsola(new ByteArrayInputStream(new byte[0]));
        try (Scanner scanner = new Scanner(entrada)) {
            Consola consola = new Consola(scanner, entrada) {
                @Override public String leerLinea(String mensaje) { return "1"; }
                @Override public OptionalInt leerEnteroConLimite(String mensaje, int minimo, int maximo) {
                    jugador.recibirDanio(Integer.MAX_VALUE);
                    return OptionalInt.of(0);
                }
            };
            try (MotorJuego motor = new MotorJuego(consola, 1, new GestorPartidas(partidas),
                    new RegistroCombate(partidas.resolve("historial.txt").toString()))) {
                motor.getRegistro().setConsumidorLog(linea -> { });
                motor.combateRapidoCon(jugador);
                assertEquals(1, motor.getPerfil().getDerrotas());
                assertNull(jugador.getGremio());
            }
        }
    }

    @Test
    void inputEndingDuringAnActionCancelsEffectsAndReleasesGuildMembership() {
        Guerrero jugador = new Guerrero("Abandono");
        EntradaConsola entrada = new EntradaConsola(new ByteArrayInputStream(new byte[0]));
        try (Scanner scanner = new Scanner(entrada)) {
            Consola consola = new Consola(scanner, entrada) {
                @Override public String leerLinea(String mensaje) { return "1"; }
                @Override public OptionalInt leerEnteroConLimite(String mensaje, int minimo, int maximo) {
                    jugador.aplicarEfecto(new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 1, 10));
                    throw new java.util.NoSuchElementException("EOF simulado");
                }
            };
            try (MotorJuego motor = new MotorJuego(consola, 1, new GestorPartidas(partidas),
                    new RegistroCombate(partidas.resolve("historial.txt").toString()))) {
                motor.getRegistro().setConsumidorLog(linea -> { });
                assertThrows(java.util.NoSuchElementException.class, () -> motor.combateRapidoCon(jugador));
                assertTrue(jugador.getEfectos().isEmpty());
                assertNull(jugador.getGremio());
            }
        }
    }

    @Test
    void longCombatLogHasBoundedRetention() {
        RegistroCombate registro = new RegistroCombate();
        registro.setConsumidorLog(linea -> { });
        for (int i = 0; i < 2000; i++) registro.agregar("Evento" + i + "x".repeat(1000));
        registro.agregar("Ultimo evento");
        assertTrue(registro.getTexto().length() <= 1_048_576);
        assertTrue(registro.getTexto().endsWith("Ultimo evento" + System.lineSeparator()));
    }

    @Test
    void potionSubmenuSharesTheDeadlineAndCannotPauseCombatForever() throws Exception {
        try (PipedOutputStream teclado = new PipedOutputStream();
             EntradaConsola entrada = new EntradaConsola(new PipedInputStream(teclado));
             Scanner scanner = new Scanner(entrada);
             MotorJuego motor = new MotorJuego(new Consola(scanner, entrada), 1, new GestorPartidas(partidas),
                     new RegistroCombate(partidas.resolve("historial.txt").toString()));
             var executor = Executors.newSingleThreadExecutor()) {
            motor.getRegistro().setConsumidorLog(linea -> { });
            teclado.write("1\n3\n".getBytes());
            teclado.flush();
            var combate = executor.submit(() -> motor.combateRapidoCon(new Guerrero("Pociones")));
            try {
                long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (!motor.getRegistro().getTexto().contains("pierde el turno") && System.nanoTime() < limite) {
                    Thread.sleep(10);
                }
                assertTrue(motor.getRegistro().getTexto().contains("pierde el turno"));
                teclado.write("0\n".getBytes());
                teclado.flush();
                combate.get(1, TimeUnit.SECONDS);
            } finally {
                combate.cancel(true);
            }
        }
    }
}
