package com.rpg.concurrency;

import com.rpg.core.TurnoCallback;

/**
 * Tarea {@link Runnable} que implementa la cuenta regresiva de turno del jugador.
 *
 * <h2>Ciclo de vida</h2>
 * <ol>
 *   <li>El hilo arranca y empieza la cuenta regresiva desde {@code segundos} hasta 0.</li>
 *   <li>En cada segundo imprime el tiempo restante.</li>
 *   <li>Si el hilo es interrumpido (el jugador respondió a tiempo), sale limpiamente
 *       restaurando la bandera de interrupción.</li>
 *   <li>Si llega a 0, invoca {@code callback.onTiempoAgotado()} para que el motor
 *       registre la pérdida de turno.</li>
 * </ol>
 *
 * <h2>Interrupción segura</h2>
 * Cuando {@link Thread#sleep(long)} lanza {@link InterruptedException}, se restaura
 * la bandera con {@code Thread.currentThread().interrupt()} y se sale del bucle con
 * {@code break}, permitiendo que el hilo termine ordenadamente sin consumir la señal.
 */
public class TemporizadorTurnoTask implements Runnable {

    private final int segundos;
    private final TurnoCallback callback;

    /**
     * @param segundos tiempo máximo de turno (debe ser positivo)
     * @param callback receptor de la notificación cuando el tiempo expira
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
        for (int restante = segundos; restante > 0; restante--) {
            System.out.printf("\r[Turno] Tiempo restante: %2d s   ", restante);
            System.out.flush();
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                // El jugador respondió antes de que el tiempo expirara.
                // Restauramos la bandera y salimos limpiamente.
                Thread.currentThread().interrupt();
                System.out.println(); // salto de linea para limpiar el contador
                return;
            }
        }
        // Tiempo agotado: notificamos al motor de juego desde este hilo.
        System.out.println("\n[Turno] Tiempo agotado! El turno se pierde.");
        callback.onTiempoAgotado();
    }
}
