package com.rpg.core;

import com.rpg.entities.Personaje;
import com.rpg.exceptions.SinManaException;
import com.rpg.exceptions.VidaInsuficienteException;

import java.util.Objects;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Reglas de un combate: ataques, habilidades, efectos al inicio del turno y duelos automaticos. */
public class Arena {

    private static final int PROBABILIDAD_CRITICO = 15;
    private static final double FACTOR_CRITICO = 1.5;
    private static final int VARIACION_DANIO = 3;
    private static final int PROBABILIDAD_ESPECIAL_IA = 35;
    private static final int MAXIMO_RONDAS = 40;

    private final RegistroCombate registro;
    private final Random azar;

    /**
     * BiConsumer funcional para aplicar danio a un personaje.
     */
    public static final BiConsumer<Personaje, Integer> APLICAR_DANIO = (objetivo, cantidad) -> {
        if (objetivo != null && cantidad != null && cantidad > 0) {
            objetivo.recibirDanio(cantidad);
        }
    };

    /**
     * BiConsumer funcional para aplicar curacion a un personaje.
     */
    public static final BiConsumer<Personaje, Integer> APLICAR_CURACION = (objetivo, cantidad) -> {
        if (objetivo != null && cantidad != null && cantidad > 0) {
            objetivo.curar(cantidad);
        }
    };

    /**
     * BiFunction funcional para calcular dinamicamente el danio base entre un atacante
     * y un defensor segun su nivel y tipo.
     */
    public static final BiFunction<Personaje, Personaje, Integer> CALCULADOR_DANIO_BASE = (atacante, defensor) -> {
        if (atacante == null) {
            return 0;
        }
        // FIX: nivel, bonificaciones y criticos no deben desbordar un int.
        long danio = atacante.calcularDanio();
        // Bonificacion segun ventaja de nivel
        if (defensor != null) {
            long diferenciaNivel = (long) atacante.getNivel() - defensor.getNivel();
            if (diferenciaNivel > 0) {
                danio += diferenciaNivel * 2;
            }
            // Modificador segun combinacion de tipos (Guerrero/Mago/Monstruo)
            if (atacante.getTipo() != defensor.getTipo()) {
                danio += 2;
            }
        }
        return saturarDanio(danio);
    };

    /**
     * Expresion lambda propia (AccionCombate): Ataque fisico directo y contundente.
     */
    public static final AccionCombate ATAQUE_FISICO = (atacante, defensor) -> {
        validarCombatientes(atacante, defensor);
        if (!atacante.estaVivo() || !defensor.estaVivo()) {
            return "Ataque cancelado: un combatiente esta derrotado";
        }
        int danioBase = CALCULADOR_DANIO_BASE.apply(atacante, defensor);
        int vidaAntes = defensor.getVida();
        defensor.defender(danioBase);
        int causado = vidaAntes - defensor.getVida();
        return String.format("[Ataque Fisico] %s golpea con fuerza a %s y causa %d de dano",
                atacante.getNombre(), defensor.getNombre(), causado);
    };

    /**
     * Expresion lambda propia (AccionCombate): Ataque magico que penetra armadura y afecta mana.
     */
    public static final AccionCombate ATAQUE_MAGICO = (atacante, defensor) -> {
        validarCombatientes(atacante, defensor);
        if (!atacante.estaVivo() || !defensor.estaVivo()) {
            return "Ataque cancelado: un combatiente esta derrotado";
        }
        int danioBase = saturarDanio(Math.round(CALCULADOR_DANIO_BASE.apply(atacante, defensor) * 1.25));
        int vidaAntes = defensor.getVida();
        // Danio magico directo a los puntos de vida
        APLICAR_DANIO.accept(defensor, Math.max(1, danioBase));
        int causado = vidaAntes - defensor.getVida();
        return String.format("[Ataque Magico] %s lanza un proyectil arcano sobre %s y causa %d de dano puro",
                atacante.getNombre(), defensor.getNombre(), causado);
    };

    public Arena(RegistroCombate registro, Random azar) {
        this.registro = Objects.requireNonNull(registro, "registro");
        this.azar = Objects.requireNonNull(azar, "azar");
    }

    /** Procesa efectos y aturdimiento. @return true si el personaje puede actuar este turno */
    public boolean iniciarTurno(Personaje personaje) {
        Objects.requireNonNull(personaje, "personaje");
        if (!personaje.estaVivo() || Thread.currentThread().isInterrupted()) {
            return false;
        }
        personaje.alIniciarTurno();
        for (String mensaje : personaje.procesarEfectos()) {
            registro.agregar(mensaje);
        }
        if (!personaje.estaVivo()) {
            registro.agregar("%s cae derrotado por sus efectos", personaje.getNombre());
            return false;
        }
        if (personaje.consumirAturdimiento()) {
            registro.agregar("%s esta aturdido y pierde el turno", personaje.getNombre());
            return false;
        }
        return true;
    }

    public void atacar(Personaje atacante, Personaje defensor) {
        validarCombatientes(atacante, defensor);
        if (!atacante.estaVivo() || !defensor.estaVivo() || Thread.currentThread().isInterrupted()) {
            return;
        }
        boolean critico = azar.nextInt(100) < PROBABILIDAD_CRITICO;
        double factor = critico ? FACTOR_CRITICO : 1.0;
        int danioBase = CALCULADOR_DANIO_BASE.apply(atacante, defensor);
        int danioBruto = saturarDanio(Math.round(danioBase * factor) + azar.nextInt(VARIACION_DANIO + 1));
        int vidaAntes = defensor.getVida();
        defensor.defender(danioBruto);
        registro.agregar("%s ataca a %s%s y causa %d de dano", atacante.getNombre(), defensor.getNombre(),
                critico ? " (CRITICO)" : "", vidaAntes - defensor.getVida());
        registro.agregar("   %s", FormatoCombate.resumen(defensor));
    }

    /**
     * Ejecuta una accion funcional de combate implementada mediante la interfaz propia AccionCombate.
     *
     * @param accion Expresion lambda con la logica del movimiento ofensivo/magico
     * @param atacante Personaje que ataca
     * @param defensor Personaje defensor
     */
    public void ejecutarAccionCombate(AccionCombate accion, Personaje atacante, Personaje defensor) {
        Objects.requireNonNull(accion, "accion");
        String mensaje = accion.ejecutar(atacante, defensor);
        registro.agregar(mensaje);
        registro.agregar("   %s", FormatoCombate.resumen(defensor));
    }

    /**
     * Aplica modificacion de vida de forma funcional usando BiConsumer.
     */
    public void aplicarEfectoVida(BiConsumer<Personaje, Integer> operador, Personaje objetivo, int cantidad) {
        Objects.requireNonNull(operador, "operador");
        operador.accept(objetivo, cantidad);
    }

    /** @return true si el especial se ejecuto; false si fallo por falta de mana o de vida */
    public boolean intentarEspecial(Personaje atacante, Personaje defensor) {
        validarCombatientes(atacante, defensor);
        if (!atacante.estaVivo() || !defensor.estaVivo() || Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            registro.agregar(atacante.realizarAtaqueEspecial(defensor));
            registro.agregar("   %s", FormatoCombate.resumen(defensor));
            return true;
        } catch (SinManaException | VidaInsuficienteException e) {
            registro.agregar("%s no puede usar su habilidad: %s", atacante.getNombre(), e.getMessage());
            return false;
        }
    }

    /** Turno completo controlado por el programa (enemigos y gladiadores). */
    public void turnoAutomatico(Personaje atacante, Personaje defensor) {
        if (!atacante.estaVivo() || !defensor.estaVivo() || !iniciarTurno(atacante)) {
            return;
        }
        boolean quiereEspecial = azar.nextInt(100) < PROBABILIDAD_ESPECIAL_IA;
        if (quiereEspecial && intentarEspecial(atacante, defensor)) {
            return;
        }
        atacar(atacante, defensor);
    }

    /** Pelea hasta que uno cae (o se acaban las rondas). @return el ganador */
    public Personaje duelo(Personaje a, Personaje b) {
        validarCombatientes(a, b);
        try {
            for (int ronda = 1; ronda <= MAXIMO_RONDAS && a.estaVivo() && b.estaVivo(); ronda++) {
                // FIX: las semifinales obedecen la cancelacion tambien durante el combate.
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }
                registro.agregar("-- Ronda %d --", ronda);
                turnoAutomatico(a, b);
                turnoAutomatico(b, a);
            }
        } finally {
            // FIX: ningun efecto continua atacando despues de decidir un ganador.
            a.detenerEfectos();
            b.detenerEfectos();
        }
        if (Thread.currentThread().isInterrupted() || (!a.estaVivo() && !b.estaVivo())) {
            return null;
        }
        return a.getVida() >= b.getVida() ? a : b;
    }

    private static int saturarDanio(long danio) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, danio));
    }

    private static void validarCombatientes(Personaje atacante, Personaje defensor) {
        Objects.requireNonNull(atacante, "atacante");
        Objects.requireNonNull(defensor, "defensor");
        if (atacante == defensor) {
            throw new IllegalArgumentException("Un combatiente no puede atacarse a si mismo");
        }
    }
}
