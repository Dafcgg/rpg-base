package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

import java.util.Objects;

/**
 * Administra el hilo de cuenta regresiva para el turno del jugador.
 *
 * <h2>Uso tipico</h2>
 * <pre>{@code
 * GestorTurno gestor = new GestorTurno(15);
 * gestor.iniciarTemporizador(() -> accionPorDefecto = Accion.ATACAR);
 * // ... leer la entrada del jugador ...
 * gestor.cancelar(); // si el jugador respondio a tiempo
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * El mismo monitor protege inicio, cancelacion y generacion del callback, de modo
 * que un temporizador sustituido no pueda notificar el vencimiento de otro turno.
 */
public class GestorTurno {

    private final int segundosPorTurno;

    /** Referencias protegidas por el monitor de este gestor. */
    private Thread hiloActivo;
    private Thread ultimoHilo;
    private Object generacionActual;

    /**
     * @param segundosPorTurno duracion maxima de cada turno en segundos
     */
    public GestorTurno(int segundosPorTurno) {
        if (segundosPorTurno <= 0) {
            throw new IllegalArgumentException("El tiempo del turno debe ser positivo");
        }
        this.segundosPorTurno = segundosPorTurno;
    }

    /**
     * Crea y arranca un nuevo hilo de cuenta regresiva.
     * Si ya habia uno activo, lo cancela antes de iniciar el nuevo.
     *
     * @param callback receptor de {@code onTiempoAgotado()} si el turno expira
     */
    public synchronized void iniciarTemporizador(TurnoCallback callback) {
        Objects.requireNonNull(callback, "callback");
        cancelar(); // asegura que no haya temporizador anterior pendiente
        Object generacion = new Object();
        generacionActual = generacion;
        TemporizadorTurnoTask tarea = new TemporizadorTurnoTask(segundosPorTurno, () -> {
            // FIX: cancelar/iniciar y el callback son atomicos respecto de la misma generacion.
            // Un temporizador viejo nunca expira la lectura de un turno nuevo.
            synchronized (this) {
                if (generacionActual == generacion && !Thread.currentThread().isInterrupted()) {
                    callback.onTiempoAgotado();
                }
            }
        });
        Thread hilo = new Thread(() -> {
            try {
                tarea.run();
            } finally {
                synchronized (this) {
                    if (generacionActual == generacion) {
                        hiloActivo = null;
                        generacionActual = null;
                    }
                }
            }
        }, "temporizador-turno");
        hilo.setDaemon(true); // no impide que la JVM cierre si el juego termina
        hiloActivo = hilo;
        ultimoHilo = hilo;

        System.out.printf("[Turno] Tienes %d segundos para elegir una accion.%n", segundosPorTurno);
        System.out.printf("[Estado hilo] NEW -> %s%n", hilo.getState());
        hilo.start();
        System.out.printf("[Estado hilo] RUNNABLE -> %s%n", hilo.getState());
    }

    /**
     * Interrumpe el temporizador activo (si existe).
     * Despues de esta llamada el hilo terminara limpiamente.
     */
    public synchronized void cancelar() {
        generacionActual = null;
        Thread hilo = hiloActivo;
        hiloActivo = null;
        if (hilo != null) {
            hilo.interrupt();
        }
    }

    /**
     * Espera a que el hilo del temporizador termine completamente.
     * Util en tests para evitar salidas entrelazadas.
     */
    public void esperarFin() {
        Thread hilo;
        synchronized (this) {
            // FIX: cancelar no pierde la referencia al hilo que aun debe terminar.
            hilo = ultimoHilo;
        }
        if (hilo != null && hilo != Thread.currentThread()) {
            try {
                hilo.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
