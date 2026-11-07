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

/**
 * Fabrica de conexiones JDBC para la base de datos MySQL del modulo Arena Segura.
 *
 * <h2>Que es JDBC</h2>
 * <p>
 * JDBC (Java Database Connectivity) es la API estandar de Java para interactuar con
 * bases de datos relacionales. Proporciona un conjunto de interfaces ({@code Connection},
 * {@code PreparedStatement}, {@code ResultSet}, etc.) que los proveedores de BD implementan
 * mediante un <em>driver</em> (en nuestro caso {@code mysql-connector-j}).
 * </p>
 *
 * <h2>Rol de DriverManager</h2>
 * <p>
 * {@link java.sql.DriverManager} es el punto de entrada central de JDBC. Cuando se llama a
 * {@code DriverManager.getConnection(url, user, pass)}, el gestor recorre la lista de drivers
 * registrados y delega la conexion al primero que reconozca el protocolo de la URL.
 * El driver de MySQL se registra automaticamente al ser cargado por el ClassLoader (mecanismo
 * SPI desde Java 6+), por lo que NO es necesario llamar a {@code Class.forName(...)}.
 * </p>
 *
 * <h2>Desglose de la URL JDBC de MySQL</h2>
 * <pre>
 *   jdbc:mysql://localhost:3306/rpg_arena_db
 *   │    │       │         │   │
 *   │    │       │         │   └─ nombre de la base de datos (schema)
 *   │    │       │         └───── puerto TCP (MySQL por defecto: 3306)
 *   │    │       └─────────────── host (nombre DNS o IP del servidor)
 *   │    └─────────────────────── subprotocolo: identifica el tipo de BD
 *   └──────────────────────────── protocolo JDBC
 * </pre>
 *
 * <h2>Error cuando falta el driver en pom.xml</h2>
 * <p>
 * Si la dependencia {@code com.mysql:mysql-connector-j} no esta declarada en {@code pom.xml},
 * la JVM lanzara al llamar a {@code DriverManager.getConnection}:
 * <pre>
 *   java.sql.SQLException: No suitable driver found for jdbc:mysql://localhost:3306/rpg_arena_db
 * </pre>
 * Esto ocurre porque ningun driver del classpath reconoce el protocolo {@code jdbc:mysql://}.
 * La solucion es agregar la dependencia y recompilar el proyecto.
 * </p>
 *
 * <h2>Lectura de config.txt</h2>
 * <p>
 * Las credenciales se leen desde {@code config.txt} en el directorio de trabajo actual.
 * Formato esperado (una clave por linea, separada por {@code =}):
 * <pre>
 *   url=jdbc:mysql://localhost:3306/rpg_arena_db
 *   usuario=root
 *   clave=mi_clave
 * </pre>
 * Si el archivo no existe o faltan claves, se muestra un mensaje descriptivo y
 * {@link #obtenerConexion()} retorna {@code null} sin lanzar excepciones no controladas.
 * </p>
 */
public class ConexionDB {

    /** Nombre del archivo de configuracion en el directorio de trabajo. */
    private static final String ARCHIVO_CONFIG = "config.txt";

    // Claves esperadas en config.txt
    private static final String CLAVE_URL      = "url";
    private static final String CLAVE_USUARIO  = "usuario";
    private static final String CLAVE_PASSWORD = "clave";

    /**
     * Crea y retorna una nueva conexion JDBC leyendo las credenciales de {@code config.txt}.
     *
     * <p>El recurso {@code Connection} retornado es responsabilidad del llamador:
     * debe cerrarse dentro de un bloque {@code try-with-resources} para garantizar
     * la liberacion de la conexion al pool de MySQL.</p>
     *
     * @return {@code Connection} abierta, o {@code null} si no fue posible conectar
     */
    /** Clave opcional para la base del Registro de Heroes (rpg_grupal_db). */
    private static final String CLAVE_URL_GRUPAL = "url_grupal";

    /** Conexion a la base de la Arena Segura (clave {@code url} de config.txt). */
    public static Connection obtenerConexion() {
        return conectar(CLAVE_URL);
    }

    /** Conexion a rpg_grupal_db (clave {@code url_grupal} de config.txt). */
    public static Connection obtenerConexionGrupal() {
        return conectar(CLAVE_URL_GRUPAL);
    }

    private static Connection conectar(String claveUrl) {
        File archivo = new File(ARCHIVO_CONFIG);
        if (!archivo.exists()) {
            System.out.println("[ConexionDB] ERROR: No se encontro el archivo '" + ARCHIVO_CONFIG + "'.");
            System.out.println("[ConexionDB] Copia 'config.example.txt' como 'config.txt' y rellena tus credenciales.");
            return null;
        }

        Map<String, String> props = leerConfig(archivo);
        if (props == null) {
            return null; // el error ya fue informado: no se intenta conectar
        }

        String url      = props.get(claveUrl);
        String usuario  = props.get(CLAVE_USUARIO);
        String password = props.get(CLAVE_PASSWORD);

        if (url == null || usuario == null || password == null) {
            System.out.println("[ConexionDB] ERROR: Faltan claves en '" + ARCHIVO_CONFIG
                    + "'. Se necesitan: " + claveUrl + ", usuario, clave.");
            return null;
        }

        try {
            Connection conn = DriverManager.getConnection(url, usuario, password);
            System.out.println("[ConexionDB] Conexion establecida con: " + url);
            return conn;
        } catch (SQLException e) {
            System.out.println("[ConexionDB] ERROR al conectar con la BD: " + e.getMessage());
            System.out.println("[ConexionDB] Verifica que MySQL este activo y que las credenciales sean correctas.");
            return null;
        }
    }

    /** @return el mapa clave/valor, o null si el archivo no se pudo leer */
    private static Map<String, String> leerConfig(File archivo) {
        Map<String, String> props = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                // Ignorar comentarios y lineas vacias
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }
                String[] partes = linea.split("=", 2);
                if (partes.length == 2) {
                    props.put(partes[0].trim(), partes[1].trim());
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("[ConexionDB] ERROR: '" + ARCHIVO_CONFIG
                    + "' no existe o no se puede abrir (" + e.getMessage() + ").");
            return null;
        } catch (IOException e) {
            System.out.println("[ConexionDB] ERROR de lectura mientras se leia '" + ARCHIVO_CONFIG
                    + "': " + e.getMessage());
            return null;
        }
        return props;
    }

    // Constructor privado: clase de utilidad, no se instancia
    private ConexionDB() {}
}
