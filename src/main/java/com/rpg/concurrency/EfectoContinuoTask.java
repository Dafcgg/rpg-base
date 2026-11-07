package com.rpg.concurrency;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Personaje;

/**
 * Tarea {@link Runnable} que aplica el tick de un {@link EstadoEfecto} de forma
 * continua y periodica sobre un {@link Personaje}, reemplazando el procesamiento
 * por turnos de {@code Personaje.procesarEfectos()}.
 *
 * <h2>Patron de interrupcion segura</h2>
 * <ul>
 *   <li>El bucle principal comprueba {@code Thread.currentThread().isInterrupted()}
 *       en cada iteracion.</li>
 *   <li>Cuando {@link Thread#sleep(long)} lanza {@link InterruptedException}:
 *     <ol>
 *       <li>Se elimina el efecto del personaje para dejar el estado consistente.</li>
 *       <li>Se restaura la bandera de interrupcion con
 *           {@code Thread.currentThread().interrupt()}.</li>
 *       <li>Se sale del bucle con {@code break}.</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * Los metodos criticos de {@code Personaje} ({@code recibirDanio}, {@code curar},
 * {@code quitarEfecto}) estan sincronizados, por lo que esta tarea puede correr
 * concurrentemente con otras tareas de efecto o con el hilo principal.
 */
public class EfectoContinuoTask implements Runnable {

    /** Intervalo entre ticks en milisegundos (1 segundo por tick de efecto). */
    private static final long INTERVALO_MS = 1_000L;

    private final Personaje objetivo;
    private final EstadoEfecto efecto;

    /**
     * @param objetivo personaje que recibira el efecto periodico
     * @param efecto   efecto a aplicar (se usa una copia interna para evitar mutacion externa)
     */
    public EfectoContinuoTask(Personaje objetivo, EstadoEfecto efecto) {
        this.objetivo = objetivo;
        this.efecto = efecto.copia();
    }

    @Override
    public void run() {
        final Thread hiloActual = Thread.currentThread();
        final String nombreEfecto = efecto.getNombre();
        String motivo = "cancelado";
        System.out.printf("[EfectoContinuo] Iniciando '%s' sobre %s (%d ticks)%n",
                nombreEfecto, objetivo.getNombre(), efecto.getTurnosRestantes());

        try {
            while (!hiloActual.isInterrupted()) {
                if (efecto.haTerminado() || !objetivo.estaVivo()) {
                    motivo = "termino";
                    break;
                }

                aplicarTick();
                efecto.consumirTurno();

                if (efecto.haTerminado()) {
                    motivo = "termino";
                    break;
                }

                try {
                    Thread.sleep(INTERVALO_MS);
                } catch (InterruptedException e) {
                    // Interrupcion externa (cancelacion del efecto): restaurar la bandera y salir.
                    // La limpieza comun se hace en el finally.
                    hiloActual.interrupt();
                    break;
                }
            }
        } finally {
            // Limpieza centralizada: se ejecuta al terminar los ticks, al morir el objetivo,
            // al ser interrumpido o ante cualquier excepcion inesperada.
            efecto.finalizar();
            // Solo elimina la entrada de Personaje si sigue registrada a NOMBRE de ESTE hilo:
            // un efecto homonimo reaplicado (con otro hilo) no se toca.
            objetivo.efectoTerminado(nombreEfecto);
            System.out.printf("[EfectoContinuo] '%s' %s sobre %s%n",
                    nombreEfecto, motivo, objetivo.getNombre());
        }
    }

    /** Aplica un tick de dano o curacion segun el tipo de efecto. */
    private void aplicarTick() {
        int valor = efecto.getValorPorTick();
        if (efecto.esDanio()) {
            objetivo.recibirDanio(valor);
            System.out.printf("[EfectoContinuo] %s sufre %d de danio por '%s' (vida: %d)%n",
                    objetivo.getNombre(), valor, efecto.getNombre(), objetivo.getVida());
        } else {
            int curado = objetivo.curar(valor);
            System.out.printf("[EfectoContinuo] %s recupera %d de vida por '%s' (vida: %d)%n",
                    objetivo.getNombre(), curado, efecto.getNombre(), objetivo.getVida());
        }
    }
}
