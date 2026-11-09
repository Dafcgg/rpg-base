package com.rpg.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(5)
class ConsolaTest {

    @Test
    void timeoutQueuedBeforeInputWaitDoesNotBlock() {
        BlockingInputStream source = new BlockingInputStream();
        EntradaConsola entrada = new EntradaConsola(source);
        try (Scanner scanner = new Scanner(entrada)) {
            Consola consola = new Consola(scanner, entrada);
            consola.prepararEsperaConLimite();
            consola.expirarEspera();

            assertTrue(consola.leerEnteroConLimite("Accion: ", 0, 4).isEmpty());
        } finally {
            source.finish();
        }
    }

    @Test
    void invalidNumbersAreRetriedAndFinalLineWithoutNewlineIsRead() {
        EntradaConsola entrada = new EntradaConsola(new ByteArrayInputStream("abc\n999999999999\n-1\n2".getBytes()));
        try (Scanner scanner = new Scanner(entrada)) {
            assertEquals(2, new Consola(scanner, entrada).leerEntero("Numero: ", 0, 4));
        }
    }

    @Test
    void oversizedLineCannotBeAcceptedAsAValidPrefix() {
        EntradaConsola entrada = new EntradaConsola(new ByteArrayInputStream("1".repeat(100_000).getBytes()));
        try (Scanner scanner = new Scanner(entrada)) {
            assertThrows(java.util.NoSuchElementException.class, scanner::nextLine);
        }
    }

    @Test
    void closingInputWakesBlockedReaders() throws Exception {
        BlockingInputStream source = new BlockingInputStream();
        EntradaConsola entrada = new EntradaConsola(source);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var lectura = executor.submit(() -> entrada.read());
            entrada.close();
            assertEquals(-1, lectura.get(1, TimeUnit.SECONDS));
        } finally {
            source.finish();
        }
    }

    @Test
    void invalidRangeIsRejectedInsteadOfLoopingForever() {
        EntradaConsola entrada = new EntradaConsola(new ByteArrayInputStream(new byte[0]));
        try (Scanner scanner = new Scanner(entrada)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Consola(scanner, entrada).leerEntero("Numero: ", 5, 1));
        }
    }

    @Test
    void sharedTurnDeadlineRemainsExpiredAcrossMenus() {
        EntradaConsola entrada = new EntradaConsola(new ByteArrayInputStream("3\n1\n".getBytes()));
        try (Scanner scanner = new Scanner(entrada)) {
            Consola consola = new Consola(scanner, entrada);
            consola.prepararEsperaConLimite();
            assertEquals(3, consola.leerEnteroConLimite("Accion: ", 0, 4).orElseThrow());
            consola.expirarEspera();
            assertTrue(consola.leerEnteroConLimite("Pocion: ", 0, 3).isEmpty());
            consola.finalizarEsperaConLimite();
            assertEquals(1, consola.leerEntero("Menu: ", 0, 4));
        }
    }

    private static final class BlockingInputStream extends InputStream {

        private final BlockingQueue<Integer> bytes = new LinkedBlockingQueue<>();

        @Override
        public int read() throws IOException {
            try {
                return bytes.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Test input interrupted");
            }
        }

        private void finish() {
            bytes.offer(-1);
        }
    }
}
