package com.rpg.model;

/**
 * POJO que representa un monstruo del bestiario almacenado en la tabla {@code monstruos}.
 */
public class Monstruo {

    private int    id;
    private String nombre;
    private int    vida;
    private int    ataque;

    // ---------------------------------------------------------------- constructores

    public Monstruo() {}

    public Monstruo(String nombre, int vida, int ataque) {
        this.nombre = nombre;
        this.vida   = vida;
        this.ataque = ataque;
    }

    public Monstruo(int id, String nombre, int vida, int ataque) {
        this.id     = id;
        this.nombre = nombre;
        this.vida   = vida;
        this.ataque = ataque;
    }

    // ---------------------------------------------------------------- getters / setters

    public int getId()                  { return id; }
    public void setId(int id)           { this.id = id; }

    public String getNombre()           { return nombre; }
    public void setNombre(String n)     { this.nombre = n; }

    public int getVida()                { return vida; }
    public void setVida(int vida)       { this.vida = vida; }

    public int getAtaque()              { return ataque; }
    public void setAtaque(int ataque)   { this.ataque = ataque; }

    @Override
    public String toString() {
        return String.format("Monstruo{id=%d, nombre='%s', vida=%d, ataque=%d}",
                id, nombre, vida, ataque);
    }
}
