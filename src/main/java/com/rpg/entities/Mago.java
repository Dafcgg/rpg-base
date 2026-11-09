package com.rpg.entities;

import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.entities.enums.TipoPersonaje;
import com.rpg.exceptions.SinManaException;

import java.io.Serial;
import java.io.Serializable;

public final class Mago extends Personaje implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int VIDA = 80;
    private static final int ATAQUE = 14;
    private static final int DEFENSA = 3;
    private static final int MANA_MAXIMO = 60;
    private static final int COSTO_MANA_ESPECIAL = 20;
    private static final int REGENERACION_MANA = 5;
    private static final double MULTIPLICADOR_ESPECIAL = 1.5;
    private static final String NOMBRE_ESPECIAL = "Bola de Fuego";
    private static final EstadoEfecto QUEMADURA = new EstadoEfecto("Quemadura", TipoEfecto.DANIO_CONTINUO, 4, 3);

    private int mana;

    public Mago(String nombre) {
        this(nombre, MANA_MAXIMO);
    }

    public Mago(String nombre, int mana) {
        super(nombre, TipoPersonaje.MAGO, VIDA, ATAQUE, DEFENSA);
        if (mana < 0 || mana > MANA_MAXIMO) throw new IllegalArgumentException("Mana fuera de rango"); // FIX
        this.mana = mana;
        aprenderHabilidad(NOMBRE_ESPECIAL);
        equiparArma(new Arma("Baston de Roble", 4, RaridadItem.RARO));
    }

    @Override
    public synchronized void alIniciarTurno() {
        mana = Math.min(MANA_MAXIMO, mana + REGENERACION_MANA);
    }

    @Override
    public synchronized void restaurarVida() {
        super.restaurarVida();
        mana = MANA_MAXIMO; // FIX: un nuevo duelo restaura tambien el recurso de clase.
    }

    /** Bola de Fuego: ignora la defensa del objetivo y lo deja ardiendo (DoT). */
    @Override
    public String realizarAtaqueEspecial(Personaje objetivo) throws SinManaException {
        java.util.Objects.requireNonNull(objetivo, "objetivo");
        if (objetivo == this) throw new IllegalArgumentException("Objetivo invalido");
        if (!objetivo.estaVivo()) return objetivo.getNombre() + " ya esta derrotado"; // FIX: tolerar muerte concurrente.
        synchronized (this) {
            if (!estaVivo()) return getNombre() + " esta derrotado y no puede atacar";
            if (mana < COSTO_MANA_ESPECIAL) {
                throw new SinManaException(COSTO_MANA_ESPECIAL, mana);
            }
            mana -= COSTO_MANA_ESPECIAL; // FIX: reservar mana atomicamente, sin bloquear al objetivo.
        }
        int danio = acotarEstadistica(calcularDanio() * MULTIPLICADOR_ESPECIAL); // FIX: cast sin overflow.
        int vidaAntes = objetivo.getVida();
        objetivo.recibirDanio(danio);
        boolean sobrevive = objetivo.estaVivo();
        if (sobrevive) objetivo.aplicarEfecto(QUEMADURA);
        return String.format("%s lanza %s (-%d de mana) y causa %d de dano a %s%s",
                getNombre(), NOMBRE_ESPECIAL, COSTO_MANA_ESPECIAL, vidaAntes - objetivo.getVida(),
                objetivo.getNombre(), sobrevive ? ", que queda en llamas" : ""); // FIX: registrar dano real.
    }

    public synchronized int getMana() {
        return mana;
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (mana < 0 || mana > MANA_MAXIMO || getTipo() != TipoPersonaje.MAGO) {
            throw new java.io.InvalidObjectException("Mago invalido"); // FIX
        }
    }

    @Serial
    private synchronized void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        out.defaultWriteObject();
    }
}
