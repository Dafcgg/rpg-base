package com.rpg.core;

import com.rpg.entities.Escuadron;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;

import java.util.List;

/**
 * Torneo de 4 gladiadores: dos semifinales simultaneas en hilos paralelos y una gran final.
 *
 * <h2>Ciclo de vida de los Hilos en Java</h2>
 * Los estados de un {@link Thread} en la JVM son observados mediante {@link Thread#getState()}:
 * <ul>
 *   <li><b>NEW:</b> El hilo ha sido instanciado pero no se ha invocado {@code start()}.</li>
 *   <li><b>RUNNABLE:</b> El hilo se esta ejecutando en la JVM o esta listo en la cola del SO.</li>
 *   <li><b>TIMED_WAITING:</b> El hilo esta esperando de forma pasiva durante un tiempo acotado
 *       (por ejemplo, dentro de {@code Thread.sleep()}).</li>
 *   <li><b>TERMINATED:</b> La ejecucion del metodo {@code run()} ha concluido o se interrumpio de forma terminal.</li>
 * </ul>
 */
public class TorneoManager {

    private final Arena arena;
    private final RegistroCombate registro;

    public TorneoManager(Arena arena, RegistroCombate registro) {
        this.arena = arena;
        this.registro = registro;
    }

    /**
     * Ejecuta el torneo completo paralelizando las semifinales y sincronizando la final con join().
     *
     * @return El campeon del torneo, o {@code null} si el torneo se cancela (hilo principal
     *         interrumpido o semifinal sin resultado): nunca se inventa un ganador.
     */
    public Personaje jugarTorneo() {
        List<Personaje> gladiadores = crearGladiadores().getMiembros();

        final Personaje[] finalistas = new Personaje[2];

        // Semifinal 1: Gladiador 0 vs Gladiador 1
        Runnable tareaSemi1 = () -> {
            try {
                // Breve espera para ilustrar TIMED_WAITING y permitir intercalado concurrente
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            finalistas[0] = simularSemifinal(gladiadores.get(0), gladiadores.get(1));
        };

        // Semifinal 2: Gladiador 2 vs Gladiador 3
        Runnable tareaSemi2 = () -> {
            try {
                // Breve espera para ilustrar TIMED_WAITING y permitir intercalado concurrente
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            finalistas[1] = simularSemifinal(gladiadores.get(2), gladiadores.get(3));
        };

        Thread hiloSemi1 = new Thread(tareaSemi1, "Semifinal-1");
        Thread hiloSemi2 = new Thread(tareaSemi2, "Semifinal-2");

        // Fase NEW
        System.out.printf("[Estado Hilo] %s: %s%n", hiloSemi1.getName(), hiloSemi1.getState());
        System.out.printf("[Estado Hilo] %s: %s%n", hiloSemi2.getName(), hiloSemi2.getState());

        // Iniciar hilos
        hiloSemi1.start();
        hiloSemi2.start();

        // Fase RUNNABLE
        System.out.printf("[Estado Hilo] %s: %s%n", hiloSemi1.getName(), hiloSemi1.getState());
        System.out.printf("[Estado Hilo] %s: %s%n", hiloSemi2.getName(), hiloSemi2.getState());

        // Monitoreo y sincronizacion en UN solo try: cualquier interrupcion del hilo principal
        // (durante la pausa o durante cualquiera de los dos join) cancela el torneo completo.
        boolean cancelado = false;
        try {
            // Breve pausa para capturar el estado TIMED_WAITING de los hilos de semifinal
            Thread.sleep(100);
            System.out.printf("[Estado Hilo] %s: %s%n", hiloSemi1.getName(), hiloSemi1.getState());
            System.out.printf("[Estado Hilo] %s: %s%n", hiloSemi2.getName(), hiloSemi2.getState());

            hiloSemi1.join();
            hiloSemi2.join();
        } catch (InterruptedException e) {
            cancelado = true;
        }

        if (cancelado) {
            // No se lee finalistas[]: puede haber solo uno (o ninguno) y no se inventa un ganador.
            return cancelarTorneo(hiloSemi1, hiloSemi2);
        }

        // Fase TERMINATED
        System.out.printf("[Estado Hilo] %s: %s%n", hiloSemi1.getName(), hiloSemi1.getState());
        System.out.printf("[Estado Hilo] %s: %s%n", hiloSemi2.getName(), hiloSemi2.getState());

        // Ambos join() retornaron: la lectura de finalistas[] es segura (happens-before de join)
        if (finalistas[0] == null || finalistas[1] == null) {
            System.out.println("[TorneoManager] Torneo CANCELADO: una semifinal no culmino. No hay campeon.");
            return null;
        }

        System.out.println("\n>>> Ambas semifinales culminaron. Procediendo a la Gran Final...\n");
        return simularFinal(finalistas[0], finalistas[1]);
    }

    /**
     * Cancelacion determinista: pide a ambas semifinales que paren, espera a que terminen (sin dejar
     * hilos sueltos escribiendo en el registro), restaura la bandera de interrupcion y avisa al usuario.
     *
     * @return siempre {@code null}: no hay campeon
     */
    private static Personaje cancelarTorneo(Thread... semifinales) {
        for (Thread hilo : semifinales) {
            hilo.interrupt();
        }
        boolean interrumpidoDeNuevo = false;
        for (Thread hilo : semifinales) {
            while (hilo.isAlive()) {
                try {
                    hilo.join();
                } catch (InterruptedException e) {
                    interrumpidoDeNuevo = true; // se sigue esperando: no se abandonan hilos vivos
                }
            }
        }
        Thread.currentThread().interrupt(); // el llamador puede ver que fue interrumpido
        System.out.println("[TorneoManager] Torneo CANCELADO: el hilo principal fue interrumpido. No hay campeon.");
        if (interrumpidoDeNuevo) {
            System.out.println("[TorneoManager] Hubo mas de una interrupcion durante la cancelacion.");
        }
        return null;
    }

    /**
     * No es {@code synchronized}: las dos semifinales deben correr en paralelo.
     * El unico recurso compartido es {@link RegistroCombate}, que ya sincroniza sus metodos.
     */
    public Personaje simularSemifinal(Personaje a, Personaje b) {
        registro.agregar("=== SEMIFINAL (%s): %s vs %s ===",
                Thread.currentThread().getName(), a.getNombre(), b.getNombre());
        Personaje ganador = arena.duelo(a, b);
        // FIX: un duelo cancelado no tiene ganador que se pueda dereferenciar.
        if (ganador != null) {
            registro.agregar("Clasifica a la final: %s", ganador.getNombre());
        }
        return ganador;
    }

    private Personaje simularFinal(Personaje a, Personaje b) {
        a.restaurarVida();
        b.restaurarVida();
        registro.agregar("=== FINAL: %s vs %s ===", a.getNombre(), b.getNombre());
        return arena.duelo(a, b);
    }

    private Escuadron crearGladiadores() {
        Escuadron escuadron = new Escuadron("Gladiadores");
        escuadron.agregar(new Guerrero("Maximo"));
        escuadron.agregar(new Mago("Circe"));
        escuadron.agregar(new Guerrero("Aquiles"));
        escuadron.agregar(new Mago("Merlina"));
        return escuadron;
    }
}
