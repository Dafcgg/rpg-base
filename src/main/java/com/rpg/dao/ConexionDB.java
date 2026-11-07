package com.rpg.dao;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Loads MySQL settings from config.txt and opens database connections. */
public final class ConexionDB {

    private static final String ARCHIVO_CONFIG = "config.txt";

    private ConexionDB() {
    }

    public static Connection obtenerConexion() {
        return conectar(false);
    }

    public static Connection obtenerConexionGrupal() {
        return conectar(true);
    }

    private static Connection conectar(boolean grupal) {
        Map<String, String> config = leerConfiguracion();
        if (config == null) {
            return null;
        }

        String url = grupal
                ? (config.get("url_grupal") != null ? config.get("url_grupal") : config.get("url"))
                : (config.get("url") != null ? config.get("url") : config.get("url_grupal"));
        if (url == null || url.isBlank()) {
            url = grupal ? config.get("url") : config.get("url_grupal");
        }
        String usuario = config.get("usuario");
        String clave = config.get("clave");
        if (url == null || url.isBlank() || usuario == null || clave == null) {
            System.err.println("[ConexionDB] Configuracion incompleta. Define una URL, usuario y clave.");
            return null;
        }

        try {
            return DriverManager.getConnection(url, usuario, clave);
        } catch (SQLException e) {
            System.err.printf("[ConexionDB] No se pudo conectar. SQLState=%s, codigo=%d.%n",
                    e.getSQLState(), e.getErrorCode());
            return null;
        }
    }

    private static Map<String, String> leerConfiguracion() {
        File archivo = new File(ARCHIVO_CONFIG);
        if (!archivo.exists()) {
            System.err.println("[ConexionDB] No existe config.txt. Copia config.example.txt y completa los valores.");
            return null;
        }

        Map<String, String> config = new HashMap<>();
        try (BufferedReader lector = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                String limpia = linea.trim();
                if (limpia.isEmpty() || limpia.startsWith("#")) {
                    continue;
                }
                String[] partes = limpia.split("=", 2);
                if (partes.length != 2 || partes[0].trim().isEmpty()) {
                    System.err.println("[ConexionDB] Formato invalido en config.txt.");
                    return null;
                }
                config.put(partes[0].trim(), partes[1].trim());
            }
        } catch (FileNotFoundException e) {
            System.err.println("[ConexionDB] No se pudo abrir config.txt.");
            return null;
        } catch (IOException e) {
            System.err.println("[ConexionDB] Error al leer config.txt.");
            return null;
        }
        return config;
    }
}
