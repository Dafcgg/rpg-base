package com.rpg.dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Loads local MySQL settings without printing credentials or JDBC URLs. */
public final class ConexionDB {
    private static final Path ARCHIVO_CONFIG = Path.of("config.txt");
    private ConexionDB() { }

    public static Connection obtenerConexion() { return conectar(false); }
    public static Connection obtenerConexionGrupal() { return conectar(true); }

    private static Connection conectar(boolean grupal) {
        Map<String, String> config = leerConfiguracion();
        String preferida = config.get(grupal ? "url_grupal" : "url");
        String alternativa = config.get(grupal ? "url" : "url_grupal");
        String url = preferida == null || preferida.isBlank() ? alternativa : preferida;
        String usuario = config.get("usuario");
        String clave = config.get("clave");
        if (url == null || url.isBlank() || usuario == null || usuario.isBlank() || clave == null) {
            throw new PersistenciaException(PersistenciaException.Motivo.CONFIGURACION,
                    "Configuracion incompleta: define URL, usuario y clave en config.txt.", null);
        }
        if (!url.startsWith("jdbc:mysql://")) {
            throw new PersistenciaException(PersistenciaException.Motivo.CONFIGURACION,
                    "La URL debe usar jdbc:mysql://.", null);
        }
        Properties propiedades = new Properties();
        propiedades.setProperty("user", usuario);
        propiedades.setProperty("password", clave);
        // FIX: Network failures must not block the console indefinitely.
        propiedades.setProperty("connectTimeout", "5000");
        propiedades.setProperty("socketTimeout", "10000");
        try {
            return DriverManager.getConnection(url, propiedades);
        } catch (SQLException e) {
            // FIX: null would be mistaken for an empty database or a missing hero.
            throw new PersistenciaException(PersistenciaException.Motivo.CONEXION,
                    "No se pudo conectar a MySQL.", e);
        }
    }

    private static Map<String, String> leerConfiguracion() {
        Map<String, String> config = new HashMap<>();
        try (BufferedReader lector = Files.newBufferedReader(ARCHIVO_CONFIG, StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank() || linea.stripLeading().startsWith("#")) {
                    continue;
                }
                int separador = linea.indexOf('=');
                if (separador < 1 || linea.substring(0, separador).isBlank()) {
                    throw new PersistenciaException(PersistenciaException.Motivo.CONFIGURACION,
                            "Formato invalido en config.txt.", null);
                }
                String clave = linea.substring(0, separador).trim();
                String valor = linea.substring(separador + 1);
                // FIX: Leading/trailing spaces can be part of the actual password.
                valor = "clave".equals(clave) ? valor : valor.trim();
                if (config.putIfAbsent(clave, valor) != null) {
                    throw new PersistenciaException(PersistenciaException.Motivo.CONFIGURACION,
                            "Clave de configuracion repetida en config.txt.", null);
                }
            }
        } catch (IOException e) {
            throw new PersistenciaException(PersistenciaException.Motivo.CONFIGURACION,
                    "No se pudo leer config.txt. Copia config.example.txt y completa los valores.", e);
        }
        return config;
    }
}