package com.rpg.model;

/**
 * POJO que representa una fila de la tabla {@code heroes} (rpg_grupal_db).
 * Atributos privados y setters validados.
 */
public class Heroe {

    public static final String TIPO_GUERRERO = "guerrero";
    public static final String TIPO_MAGO = "mago";

    private int id;
    private String nombre;
    private String tipo;
    private int vidaMaxima;
    private int nivel;

    public Heroe() {
        this.nivel = 1;
    }

    public Heroe(String nombre, String tipo, int vidaMaxima, int nivel) {
        this.nombre = validarNombre(nombre);
        this.tipo = validarTipo(tipo);
        this.vidaMaxima = validarVida(vidaMaxima);
        this.nivel = validarNivel(nivel);
    }

    public Heroe(int id, String nombre, String tipo, int vidaMaxima, int nivel) {
        this(nombre, tipo, vidaMaxima, nivel);
        this.id = id;
    }

    public static boolean esTipoValido(String tipo) {
        return tipo != null
                && (TIPO_GUERRERO.equalsIgnoreCase(tipo.trim()) || TIPO_MAGO.equalsIgnoreCase(tipo.trim()));
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = validarNombre(nombre);
    }

    private static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacio");
        }
        return nombre.trim();
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = validarTipo(tipo);
    }

    private static String validarTipo(String tipo) {
        if (!esTipoValido(tipo)) {
            throw new IllegalArgumentException("El tipo debe ser guerrero o mago");
        }
        return tipo.trim().toLowerCase();
    }

    public int getVidaMaxima() {
        return vidaMaxima;
    }

    public void setVidaMaxima(int vidaMaxima) {
        this.vidaMaxima = validarVida(vidaMaxima);
    }

    private static int validarVida(int vidaMaxima) {
        if (vidaMaxima <= 0) {
            throw new IllegalArgumentException("La vida maxima debe ser mayor que 0");
        }
        return vidaMaxima;
    }

    public int getNivel() {
        return nivel;
    }

    public void setNivel(int nivel) {
        this.nivel = validarNivel(nivel);
    }

    private static int validarNivel(int nivel) {
        if (nivel < 1) {
            throw new IllegalArgumentException("El nivel debe ser mayor o igual a 1");
        }
        return nivel;
    }

    @Override
    public String toString() {
        return String.format("Heroe{id=%d, nombre='%s', tipo=%s, vidaMaxima=%d, nivel=%d}",
                id, nombre, tipo, vidaMaxima, nivel);
    }
}
