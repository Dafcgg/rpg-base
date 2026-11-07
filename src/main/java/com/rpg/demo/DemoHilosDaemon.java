package com.rpg.demo;

/**
 * Evidencia de la investigacion obligatoria sobre hilos daemon.
 *
 * <h2>Hilo de usuario vs hilo daemon</h2>
 * <ul>
 *   <li><b>Hilo de usuario (non-daemon):</b> la JVM NO termina mientras quede alguno vivo.</li>
 *   <li><b>Hilo daemon:</b> es de servicio (autoguardado, limpieza); la JVM termina cuando ya no
 *       quedan hilos de usuario y lo mata sin esperar a que acabe.</li>
 * </ul>
 *
 * <h2>Demostraciones</h2>
 * <ol>
 *   <li>{@link #setDaemonTrasStartLanzaExcepcion()}: llamar a {@code setDaemon(true)} despues de
 *       {@code start()} lanza {@link IllegalThreadStateException}.</li>
 *   <li>Argumento {@code salida-jvm}: {@code main} arranca un daemon que dormiria 60 s y termina;
 *       la JVM cierra de inmediato aunque el daemon siga vivo.</li>
 * </ol>
 */
public final class DemoHilosDaemon {

    private DemoHilosDaemon() {
    }

    /** @return true si setDaemon(true) despues de start() lanzo IllegalThreadStateException */
    public static boolean setDaemonTrasStartLanzaExcepcion() {
        Thread hilo = new Thread(() -> dormir(300), "demo-daemon-tardio");
        hilo.start(); // el hilo ya esta en ejecucion
        try {
            hilo.setDaemon(true); // INCORRECTO: demasiado tarde
            return false;
        } catch (IllegalThreadStateException e) {
            System.out.println("[DemoDaemon] IllegalThreadStateException al llamar setDaemon(true) despues de start().");
            return true;
        } finally {
            hilo.interrupt();
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && "salida-jvm".equals(args[0])) {
            Thread daemon = new Thread(() -> dormir(60_000), "demo-daemon-largo");
            daemon.setDaemon(true); // correcto: ANTES de start()
            daemon.start();
            System.out.println("[DemoDaemon] main termina con el daemon vivo: la JVM debe cerrar ya.");
            return;
        }
        setDaemonTrasStartLanzaExcepcion();
    }
}
