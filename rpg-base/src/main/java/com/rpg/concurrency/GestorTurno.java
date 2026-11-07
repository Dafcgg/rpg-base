package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

/**
 * Administra el hilo de cuenta regresiva para el turno del jugador.
 *
 * <h2>Uso típico</h2>
 * <pre>{@code
 * GestorTurno gestor = new GestorTurno(15);
 * gestor.iniciarTemporizador(() -> accionPorDefecto = Accion.ATACAR);
 * // ... leer la entrada del jugador ...
 * gestor.cancelar(); // si el jugador respondió a tiempo
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * El campo {@code hiloActivo} se declara {@code volatile} para que la escritura
 * desde {@link #iniciarTemporizador} sea visible inmediatamente en {@link #cancelar},
 * que puede llamarse desde un hilo diferente.
 */
public class GestorTurno {

    private final int segundosPorTurno;

    /** Referencia volátil al hilo en curso; null si no hay temporizador activo. */
    private volatile Thread hiloActivo;

    /**
     * @param segundosPorTurno duración máxima de cada turno en segundos
     */
    public GestorTurno(int segundosPorTurno) {
        this.segundosPorTurno = segundosPorTurno;
    }

    /**
     * Crea y arranca un nuevo hilo de cuenta regresiva.
     * Si ya había uno activo, lo cancela antes de iniciar el nuevo.
     *
     * @param callback receptor de {@code onTiempoAgotado()} si el turno expira
     */
    public void iniciarTemporizador(TurnoCallback callback) {
        cancelar(); // asegura que no haya temporizador anterior pendiente
        TemporizadorTurnoTask tarea = new TemporizadorTurnoTask(segundosPorTurno, callback);
        Thread hilo = new Thread(tarea, "temporizador-turno");
        hilo.setDaemon(true); // no impide que la JVM cierre si el juego termina
        hiloActivo = hilo;

        System.out.printf("[Turno] Tienes %d segundos para elegir una accion.%n", segundosPorTurno);
        System.out.printf("[Estado hilo] NEW -> %s%n", hilo.getState());
        hilo.start();
        System.out.printf("[Estado hilo] RUNNABLE -> %s%n", hilo.getState());
    }

    /**
     * Interrumpe el temporizador activo (si existe).
     * Después de esta llamada el hilo terminará limpiamente.
     */
    public void cancelar() {
        Thread hilo = hiloActivo;
        if (hilo != null && hilo.isAlive()) {
            hilo.interrupt();
            hiloActivo = null;
        }
    }

    /**
     * Espera a que el hilo del temporizador termine completamente.
     * Útil en tests para evitar salidas entrelazadas.
     */
    public void esperarFin() {
        Thread hilo = hiloActivo;
        if (hilo != null) {
            try {
                hilo.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
