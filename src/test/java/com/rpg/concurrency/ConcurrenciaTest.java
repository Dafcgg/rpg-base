package com.rpg.concurrency;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Guerrero;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.persistencia.GestorPartidas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrenciaTest {
    @TempDir Path directorio;

    @Test
    void validaIntervalosYDependenciasAntesDeCrearHilos() {
        GestorPartidas gestor = new GestorPartidas(directorio);
        assertThrows(IllegalArgumentException.class, () -> new AutoGuardadoDaemon(gestor, 0));
        assertThrows(IllegalArgumentException.class, () -> new AutoGuardadoDaemon(gestor, -1));
        assertThrows(NullPointerException.class, () -> new AutoGuardadoDaemon(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new GestorTurno(0));
        assertThrows(NullPointerException.class, () -> new TemporizadorTurnoTask(1, null));
        assertThrows(NullPointerException.class, () -> new EfectoContinuoTask(null,
                new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 1, 1)));
    }

    @Test
    void cancelarImpideUnCallbackQueYaPasoLaUltimaComprobacionDeInterrupt() throws Exception {
        GestorTurno gestor = new GestorTurno(1);
        AtomicInteger callbacks = new AtomicInteger();
        CountDownLatch aPuntoDeNotificar = new CountDownLatch(1);
        CountDownLatch permitirNotificar = new CountDownLatch(1);
        PrintStream original = System.out;
        PrintStream salida = new PrintStream(OutputStream.nullOutputStream()) {
            @Override public void println(String texto) {
                if (texto != null && texto.contains("Tiempo agotado!")) {
                    aPuntoDeNotificar.countDown();
                    boolean interrumpido = false;
                    while (true) {
                        try {
                            permitirNotificar.await();
                            break;
                        } catch (InterruptedException e) {
                            interrumpido = true;
                        }
                    }
                    if (interrumpido) Thread.currentThread().interrupt();
                }
            }
        };
        try {
            System.setOut(salida);
            gestor.iniciarTemporizador(callbacks::incrementAndGet);
            assertTrue(aPuntoDeNotificar.await(3, TimeUnit.SECONDS));
            gestor.cancelar();
            permitirNotificar.countDown();
            assertTimeoutPreemptively(Duration.ofSeconds(2), gestor::esperarFin);
            assertEquals(0, callbacks.get());
        } finally {
            permitirNotificar.countDown();
            gestor.cancelar();
            gestor.esperarFin();
            System.setOut(original);
            salida.close();
        }
    }

    @Test
    void efectoCanceladoMientrasEsperaElMonitorNoDaUnGolpeTardio() throws Exception {
        Guerrero heroe = new Guerrero("Prueba");
        Thread hilo;
        synchronized (heroe) {
            heroe.aplicarEfecto(new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 100, 50));
            hilo = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t.getName().equals("efecto-Veneno-Prueba") && t.isAlive())
                    .findFirst().orElseThrow();
            assertTrue(heroe.quitarEfecto("Veneno"));
            heroe.restaurarVida();
        }
        hilo.join(2_000);
        assertFalse(hilo.isAlive());
        assertEquals(heroe.getVidaMaxima(), heroe.getVida());
        assertTrue(heroe.getEfectos().isEmpty());
    }

    @Test
    void daemonEsDaemonDesdeSuCreacionYConservaInterrupcionAlCerrar() throws Exception {
        AutoGuardadoDaemon daemon = new AutoGuardadoDaemon(new GestorPartidas(directorio), 60_000);
        assertTrue(daemon.isDaemon());
        daemon.start();
        daemon.interrupt();
        daemon.join(2_000);
        assertFalse(daemon.isAlive());
        assertTrue(daemon.isInterrupted());
    }

    @Test
    void tareaSinRegistroNoPuedeModificarOtroPersonaje() {
        Guerrero heroe = new Guerrero("Prueba");
        new EfectoContinuoTask(heroe,
                new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 100, 1)).run();
        assertEquals(heroe.getVidaMaxima(), heroe.getVida());
    }
}
