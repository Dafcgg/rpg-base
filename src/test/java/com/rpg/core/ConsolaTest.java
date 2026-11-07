package com.rpg.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
