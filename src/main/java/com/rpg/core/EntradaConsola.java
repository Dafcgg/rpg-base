package com.rpg.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entrada de teclado que se puede abandonar por tiempo (lectura con limite real).
 *
 * <h2>Problema que resuelve</h2>
 * <p>{@code Scanner.nextLine()} bloquea el hilo hasta que el usuario pulsa Enter y no se puede
 * interrumpir. Si el temporizador de turno expira mientras el hilo principal espera, el juego quedaba
 * detenido hasta que el usuario escribiera algo.</p>
 *
 * <h2>Como funciona</h2>
 * <ol>
 *   <li>Un hilo daemon ({@code entrada-consola}) lee el flujo original y publica <b>una linea completa</b>
 *       por elemento en una {@link BlockingQueue}.</li>
 *   <li>Este objeto es un {@link InputStream} que sirve esas lineas al {@code Scanner}: las lecturas
 *       normales (menus del juego) simplemente esperan la siguiente linea.</li>
 *   <li>Para la lectura con limite, el temporizador de turno llama a {@link #expirar()}, que publica un
 *       elemento especial en la misma cola. {@link #esperarLinea()} lo recibe y devuelve {@code false}
 *       sin tocar el {@code Scanner}: el hilo principal sigue de inmediato, sin exigir un Enter tardio.</li>
 * </ol>
 *
 * <p>Como cada {@code read()} entrega como maximo una linea y {@code available()} es siempre 0, el
 * buffer interno del {@code Scanner} queda vacio entre lecturas, de modo que "hay una linea en la cola"
 * equivale a "{@code nextLine()} no va a bloquear".</p>
 *
 * <p>Una linea escrita tarde (despues de expirar el turno) no se pierde: queda en la cola y la consume la
 * siguiente lectura.</p>
 */
public final class EntradaConsola extends InputStream {

    /** Marca de fin de flujo (Ctrl+D o tuberia cerrada). Se compara por identidad. */
    private static final byte[] FIN = new byte[0];

    /** Marca de "se agoto el tiempo". Se compara por identidad. */
    private static final byte[] TIEMPO = new byte[0];

    // FIX: entrada hostil o pegado masivo no pueden hacer crecer la memoria sin limite.
    private static final int MAX_BYTES_LINEA = 4096;
    private final BlockingQueue<byte[]> cola = new ArrayBlockingQueue<>(128);
    private final AtomicBoolean cerrada = new AtomicBoolean();
    private final AtomicBoolean expirada = new AtomicBoolean();
    private final InputStream origen;
    private final Thread bomba;

    /** Linea en curso de entrega al Scanner (protegida por el monitor de este objeto). */
    private byte[] actual;
    private int posicion;

    public EntradaConsola(InputStream origen) {
        this.origen = Objects.requireNonNull(origen, "origen");
        bomba = new Thread(this::bombear, "entrada-consola");
        bomba.setDaemon(true); // no impide que la JVM termine
        bomba.start();
    }

    /** Lee el flujo original y publica una linea completa por elemento de la cola. */
    private void bombear() {
        ByteArrayOutputStream linea = new ByteArrayOutputStream();
        try {
            int b;
            while (!cerrada.get() && (b = origen.read()) != -1) {
                if (linea.size() >= MAX_BYTES_LINEA) {
                    throw new IOException("Linea de consola demasiado larga (maximo 4096 bytes)");
                }
                linea.write(b);
                if (b == '\n') {
                    cola.put(linea.toByteArray());
                    linea.reset();
                }
            }
            if (linea.size() > 0 && !cerrada.get()) {
                cola.put(linea.toByteArray());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (!cerrada.get()) {
                System.err.println("[EntradaConsola] Error de lectura: " + e.getMessage());
            }
        } finally {
            if (!cerrada.get()) {
                try {
                    cola.put(FIN);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Descarta una marca de tiempo agotado que haya quedado de un turno anterior. */
    public void armar() {
        cola.removeIf(item -> item == TIEMPO);
        expirada.set(false);
    }

    /** Fin de la espera: elimina marcas de tiempo que hayan llegado justo despues de responder. */
    public void desarmar() {
        cola.removeIf(item -> item == TIEMPO);
        expirada.set(false);
    }

    /** Despierta a quien espera en {@link #esperarLinea()}. Seguro para llamarse desde otro hilo. */
    public void expirar() {
        expirada.set(true);
        cola.offer(TIEMPO);
    }

    /**
     * Espera hasta que haya una linea completa (o fin de flujo) o hasta que se llame a {@link #expirar()}.
     *
     * @return {@code true} si {@code Scanner.nextLine()} ya no bloqueara; {@code false} si el tiempo expiro
     * @throws InterruptedException si el hilo que espera es interrumpido
     */
    public synchronized boolean esperarLinea() throws InterruptedException {
        // FIX: el timeout sigue vigente incluso si la cola estaba llena al publicarlo.
        if (expirada.get()) {
            return false;
        }
        if (cerrada.get()) {
            actual = FIN;
        }
        if (actual != null) {
            return true;
        }
        byte[] item = cola.take();
        if (item == TIEMPO || expirada.get()) {
            if (item != TIEMPO) {
                actual = item;
                posicion = 0;
            }
            return false;
        }
        actual = item;
        posicion = 0;
        return true;
    }

    @Override
    public synchronized int read(byte[] destino, int desde, int largo) throws IOException {
        Objects.checkFromIndexSize(desde, largo, destino.length);
        if (largo == 0) {
            return 0;
        }
        if (cerrada.get()) {
            return -1;
        }
        try {
            while (actual == null) {
                byte[] item = cola.take();
                if (item != TIEMPO) { // una marca de tiempo no es entrada del usuario
                    actual = item;
                    posicion = 0;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Lectura de consola interrumpida");
        }
        if (actual == FIN) {
            return -1; // fin de flujo permanente
        }
        int n = Math.min(largo, actual.length - posicion);
        System.arraycopy(actual, posicion, destino, desde, n);
        posicion += n;
        if (posicion >= actual.length) {
            actual = null;
        }
        return n;
    }

    @Override
    public int read() throws IOException {
        byte[] uno = new byte[1];
        int n = read(uno, 0, 1);
        return n == -1 ? -1 : (uno[0] & 0xFF);
    }

    @Override
    public void close() throws IOException {
        // FIX: despertar consumidores y productores bloqueados al cerrar el Scanner.
        if (cerrada.compareAndSet(false, true)) {
            bomba.interrupt();
            cola.clear();
            cola.offer(FIN);
            // System.in pertenece al proceso; cerrarlo desde otro hilo puede bloquear.
            if (origen != System.in) {
                origen.close();
            }
        }
    }
}
