package com.rpg.core;

/**
 * Interfaz de callback para notificar al motor de juego cuando el temporizador
 * de turno se agota sin que el jugador haya actuado.
 *
 * <p>El implementador ({@link MotorJuego}) debe registrar una acción por defecto
 * (por ejemplo {@link Accion#ATACAR}) y liberar el bloqueo de lectura.</p>
 */
public interface TurnoCallback {

    /**
     * Invocado por {@link com.rpg.concurrency.TemporizadorTurnoTask} cuando la cuenta
     * regresiva llega a cero. La implementación debe ser <em>thread-safe</em> porque
     * se ejecuta desde un hilo distinto al hilo principal del juego.
     */
    void onTiempoAgotado();
}
