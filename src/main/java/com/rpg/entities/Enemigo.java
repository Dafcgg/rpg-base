package com.rpg.entities;

import com.rpg.entities.enums.TipoPersonaje;

import java.io.Serial;
import java.io.Serializable;

/** Monstruo del bestiario. Su ataque especial pega fuerte y puede envenenar, quemar o aturdir. */
public final class Enemigo extends Personaje implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final double MULTIPLICADOR_ESPECIAL = 1.5;

    private final int ataque;
    private final EstadoEfecto efectoEspecial;
    private final boolean aturde;

    public Enemigo(String nombre, int vida, int ataque, int defensa) {
        this(nombre, vida, ataque, defensa, null, false);
    }

    public Enemigo(String nombre, int vida, int ataque, int defensa, EstadoEfecto efectoEspecial, boolean aturde) {
        super(nombre, TipoPersonaje.MONSTRUO, vida, ataque, defensa);
        this.ataque = ataque;
        this.efectoEspecial = efectoEspecial == null ? null : efectoEspecial.copia(); // FIX: proteger plantilla.
        this.aturde = aturde;
    }

    /** Nueva instancia con la vida llena: el bestiario guarda plantillas y se combate con copias. */
    public synchronized Enemigo copia() {
        Enemigo copia = new Enemigo(getNombre(), getVidaMaxima(), ataque, getDefensa(), efectoEspecial, aturde);
        copia.setNivel(getNivel()); // FIX: no perder el nivel de la plantilla al copiar.
        copia.equiparArma(getArma());
        return copia;
    }

    @Override
    public String realizarAtaqueEspecial(Personaje objetivo) {
        java.util.Objects.requireNonNull(objetivo, "objetivo");
        if (!estaVivo()) return getNombre() + " esta derrotado y no puede atacar"; // FIX: tolerar muerte concurrente.
        if (objetivo == this) throw new IllegalArgumentException("Objetivo invalido");
        if (!objetivo.estaVivo()) return objetivo.getNombre() + " ya esta derrotado";
        int vidaAntes = objetivo.getVida();
        objetivo.defender(acotarEstadistica(calcularDanio() * MULTIPLICADOR_ESPECIAL)); // FIX
        StringBuilder mensaje = new StringBuilder(String.format("%s ataca con furia a %s y causa %d de dano",
                getNombre(), objetivo.getNombre(), vidaAntes - objetivo.getVida()));
        if (efectoEspecial != null && objetivo.estaVivo()) {
            objetivo.aplicarEfecto(efectoEspecial);
            mensaje.append(", aplicando ").append(efectoEspecial.getNombre());
        }
        if (aturde && objetivo.estaVivo()) {
            objetivo.aturdir();
            mensaje.append(" y deja aturdido a ").append(objetivo.getNombre());
        }
        return mensaje.toString();
    }

    public int getAtaque() {
        return ataque;
    }

    public String getHabilidad() {
        if (efectoEspecial != null) {
            return efectoEspecial.getNombre();
        }
        return aturde ? "Aturdir" : "Ninguna";
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (ataque < 0 || getTipo() != TipoPersonaje.MONSTRUO) {
            throw new java.io.InvalidObjectException("Enemigo invalido"); // FIX
        }
    }
}
