package com.rpg.concurrency;

import com.rpg.persistencia.GestorPartidas;

import java.util.Objects;

/**
 * Hilo daemon que guarda el estado de la partida en disco periodicamente,
 * liberando al jugador de tener que hacerlo manualmente.
 *
 * <h2>Hilos Daemon - Teoria</h2>
 * <p>En la JVM existen dos categorias de hilos:</p>
 * <ul>
 *   <li><strong>Hilos de usuario (non-daemon):</strong> La JVM espera a que todos
 *       terminen antes de apagar la maquina virtual. Son los hilos "importantes".</li>
 *   <li><strong>Hilos daemon:</strong> La JVM los mata automaticamente cuando ya no
 *       quedan hilos de usuario vivos. No impiden el cierre de la JVM.</li>
 * </ul>
 *
 * <h2>Por que setDaemon(true) ANTES de start()?</h2>
 * <p>Una vez que se llama a {@link Thread#start()}, el hilo pasa al estado
 * {@code RUNNABLE} y la JVM lo registra en su arbol de hilos. Cambiar la naturaleza
 * daemon de un hilo en ejecucion es una operacion invalida porque alteraria el
 * recuento de hilos de usuario que la JVM usa para decidir cuando apagarse.
 * Por eso {@link Thread#setDaemon(boolean)} lanza {@link IllegalThreadStateException}
 * si se llama despues de {@code start()}.</p>
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
 * <h2>Sincronizacion con GestorPartidas</h2>
 * <p>El guardado se delega a {@link GestorPartidas#guardarPerfilAutomatico}, que
 * sincroniza internamente sobre su lock privado. Asi, si el usuario tambien guarda
 * manualmente desde el menu, no habra corrupcion del archivo.</p>
 */
public final class AutoGuardadoDaemon extends Thread {

    private final GestorPartidas gestorPartidas;
    private final long intervaloMs;

    /**
     * Construye y configura el daemon. <strong>No llama a {@code start()}</strong>;
     * el caller decide cuando iniciarlo.
     *
     * @param gestorPartidas gestor de persistencia (thread-safe)
     * @param intervaloMs    milisegundos entre guardados automaticos
     */
    public AutoGuardadoDaemon(GestorPartidas gestorPartidas, long intervaloMs) {
        super("auto-guardado-daemon");
        // FIX: cero produce un bucle de E/S y un intervalo negativo mata el hilo.
        if (intervaloMs <= 0) {
            throw new IllegalArgumentException("El intervalo de guardado debe ser positivo");
        }
        this.gestorPartidas = Objects.requireNonNull(gestorPartidas, "gestorPartidas");
        this.intervaloMs = intervaloMs;
        setDaemon(true);
    }

    @Override
    public void run() {
        System.out.printf("[AutoGuardado] Daemon iniciado (intervalo: %d ms)%n", intervaloMs);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(intervaloMs);
                // FIX: el resultado de E/S decide el mensaje; un fallo no es un guardado.
                if (!Thread.currentThread().isInterrupted() && gestorPartidas.guardarPerfilAutomatico()) {
                    System.out.println("[AutoGuardado] Partida guardada automaticamente en disco.");
                }
            } catch (InterruptedException e) {
                // Restaurar bandera y salir limpiamente
                Thread.currentThread().interrupt();
                System.out.println("[AutoGuardado] Daemon detenido.");
                break;
            }
        }
    }
}
