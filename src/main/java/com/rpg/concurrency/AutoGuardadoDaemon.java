package com.rpg.concurrency;

import com.rpg.persistencia.GestorPartidas;

/**
 * Hilo daemon que guarda el estado de la partida en disco periódicamente,
 * liberando al jugador de tener que hacerlo manualmente.
 *
 * <h2>Hilos Daemon — Teoría</h2>
 * <p>En la JVM existen dos categorías de hilos:</p>
 * <ul>
 *   <li><strong>Hilos de usuario (non-daemon):</strong> La JVM espera a que todos
 *       terminen antes de apagar la máquina virtual. Son los hilos "importantes".</li>
 *   <li><strong>Hilos daemon:</strong> La JVM los mata automáticamente cuando ya no
 *       quedan hilos de usuario vivos. No impiden el cierre de la JVM.</li>
 * </ul>
 *
 * <h2>¿Por qué setDaemon(true) ANTES de start()?</h2>
 * <p>Una vez que se llama a {@link Thread#start()}, el hilo pasa al estado
 * {@code RUNNABLE} y la JVM lo registra en su árbol de hilos. Cambiar la naturaleza
 * daemon de un hilo en ejecución es una operación inválida porque alteraría el
 * recuento de hilos de usuario que la JVM usa para decidir cuándo apagarse.
 * Por eso {@link Thread#setDaemon(boolean)} lanza {@link IllegalThreadStateException}
 * si se llama después de {@code start()}.</p>
 *
 * <pre>{@code
 * // CORRECTO
 * AutoGuardadoDaemon daemon = new AutoGuardadoDaemon(gestor, 30_000);
 * daemon.setDaemon(true);  // <-- antes de start()
 * daemon.start();
 *
 * // INCORRECTO (lanza IllegalThreadStateException)
 * daemon.start();
 * daemon.setDaemon(true);  // ERROR
 * }</pre>
 *
 * <h2>Sincronización con GestorPartidas</h2>
 * <p>El guardado se delega a {@link GestorPartidas#guardarPerfilAutomatico}, que
 * sincroniza internamente sobre su lock privado. Así, si el usuario también guarda
 * manualmente desde el menú, no habrá corrupción del archivo.</p>
 */
public class AutoGuardadoDaemon extends Thread {

    private final GestorPartidas gestorPartidas;
    private final long intervaloMs;

    /**
     * Construye el daemon. <strong>No llama a {@code start()}</strong>; el caller
     * es responsable de llamar a {@code setDaemon(true)} y luego a {@code start()}.
     *
     * @param gestorPartidas gestor de persistencia (thread-safe)
     * @param intervaloMs    milisegundos entre guardados automáticos
     */
    public AutoGuardadoDaemon(GestorPartidas gestorPartidas, long intervaloMs) {
        super("auto-guardado-daemon");
        this.gestorPartidas = gestorPartidas;
        this.intervaloMs = intervaloMs;
    }

    @Override
    public void run() {
        System.out.printf("[AutoGuardado] Daemon iniciado (intervalo: %d ms)%n", intervaloMs);
        while (true) {
            try {
                Thread.sleep(intervaloMs);
                gestorPartidas.guardarPerfilAutomatico();
                System.out.println("[AutoGuardado] Partida guardada automaticamente en disco.");
            } catch (InterruptedException e) {
                // Restaurar bandera y salir limpiamente
                Thread.currentThread().interrupt();
                System.out.println("[AutoGuardado] Daemon detenido.");
                break;
            }
        }
    }
}
