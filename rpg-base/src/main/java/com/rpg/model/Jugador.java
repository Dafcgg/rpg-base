package com.rpg.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * POJO que representa un jugador autenticado.
 * <p>
 * La clave nunca se almacena en texto plano: el metodo estatico
 * {@link #hashSHA256(String)} aplica el algoritmo SHA-256 antes de
 * enviar el valor a la base de datos o de comparar credenciales.
 * </p>
 */
public class Jugador {

    private int    id;
    private String usuario;
    private String claveHash; // siempre SHA-256 hex
    private String rol;       // "admin" o "jugador"

    // ---------------------------------------------------------------- constructores

    public Jugador() {}

    public Jugador(String usuario, String claveHash, String rol) {
        this.usuario   = usuario;
        this.claveHash = claveHash;
        this.rol       = rol;
    }

    // ---------------------------------------------------------------- utilidad SHA-256

    /**
     * Convierte una contrasena en texto plano a su representacion hexadecimal SHA-256.
     *
     * @param textoPlano contrasena original introducida por el usuario
     * @return cadena de 64 caracteres hexadecimales
     * @throws RuntimeException si la JVM no soporta SHA-256 (no ocurre en Java 21)
     */
    public static String hashSHA256(String textoPlano) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(textoPlano.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible en esta JVM", e);
        }
    }

    // ---------------------------------------------------------------- getters / setters

    public int getId()                   { return id; }
    public void setId(int id)            { this.id = id; }

    public String getUsuario()           { return usuario; }
    public void setUsuario(String u)     { this.usuario = u; }

    public String getClaveHash()         { return claveHash; }
    public void setClaveHash(String c)   { this.claveHash = c; }

    public String getRol()               { return rol; }
    public void setRol(String rol)       { this.rol = rol; }

    /** @return true si el jugador tiene privilegios de administrador */
    public boolean esAdmin() {
        return "admin".equalsIgnoreCase(this.rol);
    }

    @Override
    public String toString() {
        return "Jugador{id=" + id + ", usuario='" + usuario + "', rol='" + rol + "'}";
    }
}
