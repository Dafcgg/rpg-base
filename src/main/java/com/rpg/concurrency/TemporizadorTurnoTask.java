package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

/**
 * Tarea {@link Runnable} que implementa la cuenta regresiva de turno del jugador.
 *
 * <h2>Ciclo de vida</h2>
 * <ol>
 *   <li>El hilo arranca y empieza la cuenta regresiva desde {@code segundos} hasta 0.</li>
 *   <li>En cada segundo imprime el tiempo restante.</li>
 *   <li>Si el hilo es interrumpido (el jugador respondio a tiempo), sale limpiamente
 *       restaurando la bandera de interrupcion.</li>
 *   <li>Si llega a 0, invoca {@code callback.onTiempoAgotado()} para que el motor
 *       registre la perdida de turno.</li>
 * </ol>
 *
 * <h2>Interrupcion segura</h2>
 * Cuando {@link Thread#sleep(long)} lanza {@link InterruptedException}, se restaura
 * la bandera con {@code Thread.currentThread().interrupt()} y se sale del bucle con
 * {@code break}, permitiendo que el hilo termine ordenadamente sin consumir la senal.
 */
public class TemporizadorTurnoTask implements Runnable {

    private final int segundos;
    private final TurnoCallback callback;

    /**
     * @param segundos tiempo maximo de turno (debe ser positivo)
     * @param callback receptor de la notificacion cuando el tiempo expira
     */
    public TemporizadorTurnoTask(int segundos, TurnoCallback callback) {
        if (segundos <= 0) {
            throw new IllegalArgumentException("El tiempo del turno debe ser positivo");
        }
        this.segundos = segundos;
        this.callback = callback;
    }

    @Override
    public void run() {
        for (int restante = segundos; restante > 0 && !Thread.currentThread().isInterrupted(); restante--) {
            System.out.printf("\r[Turno] Tiempo restante: %2d s   ", restante);
            System.out.flush();
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                // El jugador respondio antes de que el tiempo expirara.
                // Restauramos la bandera y salimos limpiamente.
                Thread.currentThread().interrupt();
                System.out.println(); // salto de linea para limpiar el contador
                return;
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            System.out.println();
            return; // cancelado antes de expirar: no se notifica el callback
        }
        // Tiempo agotado: notificamos al motor de juego desde este hilo.
        System.out.println("\n[Turno] Tiempo agotado! El turno se pierde.");
        callback.onTiempoAgotado();
    }
}
