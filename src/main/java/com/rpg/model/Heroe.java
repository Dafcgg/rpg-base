package com.rpg.model;

import java.util.Locale;
import java.util.Objects;

/** Persistent hero record. */
public final class Heroe {

    private int id;
    private String nombre;
    private String tipo;
    private int vidaMaxima;
    private int nivel;

    public Heroe() {
        this(0, "Nuevo", "guerrero", 1, 1);
    }

    public Heroe(String nombre, String tipo, int vidaMaxima, int nivel) {
        this(0, nombre, tipo, vidaMaxima, nivel);
    }

    public Heroe(int id, String nombre, String tipo, int vidaMaxima, int nivel) {
        setId(id);
        setNombre(nombre);
        setTipo(tipo);
        setVidaMaxima(vidaMaxima);
        setNivel(nivel);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("El id no puede ser negativo.");
        }
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 100) {
            throw new IllegalArgumentException("El nombre no puede superar 100 caracteres.");
        }
        if (!limpio.matches("[A-Za-z0-9 ]+")) {
            throw new IllegalArgumentException("El nombre solo puede usar letras ASCII, numeros y espacios.");
        }
        this.nombre = limpio;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo debe ser guerrero o mago.");
        }
        String normalizado = tipo.trim().toLowerCase(Locale.ROOT);
        if (!normalizado.equals("guerrero") && !normalizado.equals("mago")) {
            throw new IllegalArgumentException("El tipo debe ser guerrero o mago.");
        }
        this.tipo = normalizado;
    }

    public int getVidaMaxima() {
        return vidaMaxima;
    }

    public void setVidaMaxima(int vidaMaxima) {
        if (vidaMaxima <= 0) {
            throw new IllegalArgumentException("La vida maxima debe ser mayor que cero.");
        }
        this.vidaMaxima = vidaMaxima;
    }

    public int getNivel() {
        return nivel;
    }

    public void setNivel(int nivel) {
        if (nivel < 1) {
            throw new IllegalArgumentException("El nivel debe ser al menos 1.");
        }
        this.nivel = nivel;
    }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (!(objeto instanceof Heroe otro)) {
            return false;
        }
        return id == otro.id
                && vidaMaxima == otro.vidaMaxima
                && nivel == otro.nivel
                && Objects.equals(nombre, otro.nombre)
                && Objects.equals(tipo, otro.tipo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nombre, tipo, vidaMaxima, nivel);
    }

    @Override
    public String toString() {
        return "Heroe{id=" + id + ", nombre='" + nombre + "', tipo='" + tipo
                + "', vidaMaxima=" + vidaMaxima + ", nivel=" + nivel + "}";
    }
}
