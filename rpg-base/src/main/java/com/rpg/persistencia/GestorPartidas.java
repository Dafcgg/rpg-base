package com.rpg.persistencia;

import com.rpg.entities.Personaje;
import com.rpg.entities.PerfilJugador;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Servicio de persistencia binaria para partidas del juego RPG.
 *
 * <h2>Estructura de archivos</h2>
 * <pre>
 * partidas/
 *   perfil_jugador.dat    ← guardado principal del perfil
 *   perfil_auto.dat       ← guardado automático del daemon
 * </pre>
 *
 * <h2>Sincronización</h2>
 * <p>Todas las operaciones de E/S se realizan dentro de bloques
 * {@code synchronized(lock)} para evitar colisiones entre el guardado manual
 * del usuario y el {@link com.rpg.concurrency.AutoGuardadoDaemon}.</p>
 *
 * <h2>FileWriter vs Files.writeString — Comparativa</h2>
 * <ul>
 *   <li><strong>{@code FileWriter} + {@code BufferedWriter}:</strong> Escribe con
 *       búfer; ideal para escrituras incrementales (append de logs). El búfer
 *       acumula bytes hasta que se llama a {@code flush()} o {@code close()}.
 *       Mejor rendimiento en escrituras frecuentes y pequeñas.</li>
 *   <li><strong>{@code Files.writeString} (NIO):</strong> Escribe todo el contenido
 *       de golpe (atómico a nivel de API, aunque el SO no garantiza atomicidad en
 *       disco). Conveniente para escribir un String completo de una sola vez.
 *       Internamente abre, escribe y cierra el canal en una sola llamada.</li>
 *   <li><strong>Regla práctica:</strong> usa {@code BufferedWriter} para streams
 *       con muchas escrituras pequeñas (historial de combate línea a línea);
 *       usa {@code Files.writeString} cuando quieras escribir texto completo
 *       de forma concisa y la frecuencia de escritura es baja.</li>
 * </ul>
 *
 * <h2>Seguridad en Serialización</h2>
 * <p>{@code ObjectInputStream.readObject()} puede ejecutar bloques de código
 * arbitrario a través del mecanismo de {@code readObject()} personalizado en
 * clases maliciosas (gadget chains). Riesgos:</p>
 * <ul>
 *   <li>Ejecución remota de código (RCE) si se deserializa de fuentes no confiables.</li>
 *   <li>Denegación de servicio (DoS) por objetos que consumen memoria excesiva.</li>
 * </ul>
 * <p><strong>Alternativas más seguras:</strong></p>
 * <ul>
 *   <li>Formatos planos (CSV, properties) para datos simples.</li>
 *   <li>JSON (Jackson, Gson) con clases POJO explícitas — sin código ejecutable.</li>
 *   <li>Usar {@code ObjectInputFilter} (Java 9+) para permitir solo clases esperadas.</li>
 * </ul>
 */
public class GestorPartidas {

    private static final String DIRECTORIO = "partidas";
    private static final String ARCHIVO_PERFIL = "perfil_jugador.dat";
    private static final String ARCHIVO_AUTO = "perfil_auto.dat";
    private static final String ARCHIVO_PERSONAJE = "personaje.dat";

    /**
     * Filtro de deserializacion: solo se aceptan clases del proyecto y de java.base;
     * cualquier otra clase (por ejemplo una cadena de gadgets) se rechaza.
     */
    private static final ObjectInputFilter FILTRO_DESERIALIZACION =
            ObjectInputFilter.Config.createFilter("com.rpg.**;java.base/*;!*");

    /** Lock de sincronización compartido entre guardado manual y daemon. */
    private final Object lock = new Object();

    /** Referencia volátil al perfil activo para el daemon de autoguardado. */
    private volatile PerfilJugador perfilActual;

    public GestorPartidas() {
        // Crear el directorio de partidas si no existe
        File directorio = new File(DIRECTORIO);
        if (!directorio.exists()) {
            boolean creado = directorio.mkdirs();
            if (creado) {
                System.out.println("[GestorPartidas] Directorio 'partidas/' creado.");
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Guardado y carga del perfil del jugador
    // ─────────────────────────────────────────────────────────

    /**
     * Registra el perfil activo para que el daemon de autoguardado pueda
     * persistirlo sin necesidad de parámetros adicionales.
     *
     * @param perfil perfil a mantener en memoria
     */
    public void setPerfilActual(PerfilJugador perfil) {
        this.perfilActual = perfil;
    }

    /**
     * Guarda el {@link PerfilJugador} en disco como objeto serializado.
     * Usa {@code try-with-resources} para garantizar el cierre de flujos.
     *
     * @param perfil objeto a persistir
     */
    public void guardarPerfil(PerfilJugador perfil) {
        synchronized (lock) {
            File archivo = new File(DIRECTORIO, ARCHIVO_PERFIL);
            File dir = archivo.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(archivo))) {
                synchronized (perfil) { // el daemon y el juego no deben mutar el perfil mientras se serializa
                    oos.writeObject(perfil);
                }
                System.out.printf("[GestorPartidas] Perfil guardado en: %s%n",
                        archivo.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[GestorPartidas] Error al guardar perfil: " + e.getMessage());
            }
        }
    }

    /**
     * Invocado por {@link com.rpg.concurrency.AutoGuardadoDaemon} para guardar
     * el perfil activo de forma automatica.
     */
    public void guardarPerfilAutomatico() {
        PerfilJugador perfil = perfilActual;
        if (perfil == null) {
            return;
        }
        synchronized (lock) {
            File archivo = new File(DIRECTORIO, ARCHIVO_AUTO);
            File dir = archivo.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(archivo))) {
                synchronized (perfil) {
                    oos.writeObject(perfil);
                }
            } catch (IOException e) {
                System.err.println("[AutoGuardado] Error: " + e.getMessage());
            }
        }
    }

    /**
     * Carga el {@link PerfilJugador} desde el archivo de partida principal.
     *
     * @return el perfil deserializado, o {@code null} si el archivo no existe o hay error
     */
    public PerfilJugador cargarPerfil() {
        synchronized (lock) {
            File archivo = new File(DIRECTORIO, ARCHIVO_PERFIL);
            if (!archivo.exists()) {
                System.out.println("[GestorPartidas] No se encontro ninguna partida guardada.");
                return null;
            }
            try (ObjectInputStream ois = new ObjectInputStream(
                    new FileInputStream(archivo))) {
                ois.setObjectInputFilter(FILTRO_DESERIALIZACION);
                Object obj = ois.readObject();
                if (obj instanceof PerfilJugador perfil) {
                    System.out.printf("[GestorPartidas] Perfil cargado: victorias=%d, derrotas=%d%n",
                            perfil.getVictorias(), perfil.getDerrotas());
                    return perfil;
                } else {
                    System.err.println("[GestorPartidas] Tipo de objeto inesperado en el archivo.");
                    return null;
                }
            } catch (FileNotFoundException e) {
                System.out.println("[GestorPartidas] Archivo de partida no encontrado.");
                return null;
            } catch (ClassNotFoundException e) {
                System.err.println("[GestorPartidas] Clase no encontrada al deserializar: " + e.getMessage());
                return null;
            } catch (IOException e) {
                System.err.println("[GestorPartidas] Error al cargar partida: " + e.getMessage());
                return null;
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Metadatos y gestión de archivos
    // ─────────────────────────────────────────────────────────

    /**
     * Muestra metadatos del archivo de partida guardada.
     */
    public void mostrarMetadatos() {
        synchronized (lock) {
            File archivo = new File(DIRECTORIO, ARCHIVO_PERFIL);
            System.out.println("\n=== METADATOS DE PARTIDA ===");
            if (!archivo.exists()) {
                System.out.println("  No hay partida guardada.");
                return;
            }
            System.out.println("  Nombre   : " + archivo.getName());
            System.out.println("  Ruta rel : " + archivo.getPath());
            System.out.println("  Ruta abs : " + archivo.getAbsolutePath());
            System.out.printf("  Tamano   : %d bytes%n", archivo.length());
            System.out.println("  Existe   : " + archivo.exists());
            System.out.println("  Legible  : " + archivo.canRead());
        }
    }

    /**
     * Elimina el autoguardado con {@link File#delete()}.
     *
     * <h3>File.delete() vs Files.deleteIfExists(Path)</h3>
     * <ul>
     *   <li>{@code File.delete()} devuelve solo un {@code boolean}: {@code false} significa
     *       "no existia" O "fallo" y no se puede distinguir la causa.</li>
     *   <li>{@code Files.deleteIfExists(Path)} devuelve {@code false} solo si no existia y
     *       lanza {@code IOException} con la causa real (permisos, directorio no vacio...).</li>
     * </ul>
     * Por eso la partida principal se borra con NIO y el autoguardado con la API clasica,
     * para demostrar ambas.
     *
     * @return {@code true} si el archivo existia y se elimino
     */
    public boolean eliminarAutoguardado() {
        synchronized (lock) {
            File archivo = new File(DIRECTORIO, ARCHIVO_AUTO);
            boolean eliminado = archivo.delete();
            System.out.println(eliminado
                    ? "[GestorPartidas] Autoguardado eliminado con File.delete()."
                    : "[GestorPartidas] File.delete() devolvio false (no existia o no se pudo borrar).");
            return eliminado;
        }
    }

    /**
     * Elimina la partida guardada principal (con {@code Files.deleteIfExists}) y el autoguardado
     * (con {@code File.delete()}).
     *
     * @return {@code true} si se eliminó con éxito la partida principal
     */
    public boolean eliminarPartida() {
        synchronized (lock) {
            Path ruta = Paths.get(DIRECTORIO, ARCHIVO_PERFIL);
            try {
                boolean eliminado = Files.deleteIfExists(ruta);
                if (eliminado) {
                    System.out.println("[GestorPartidas] Partida eliminada.");
                } else {
                    System.out.println("[GestorPartidas] No habia partida que eliminar.");
                }
                eliminarAutoguardado();
                return eliminado;
            } catch (IOException e) {
                System.err.println("[GestorPartidas] Error al eliminar partida: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * Verifica si existe una partida guardada en disco.
     *
     * @return {@code true} si el archivo de partida principal existe
     */
    public boolean existePartida() {
        synchronized (lock) {
            return new File(DIRECTORIO, ARCHIVO_PERFIL).exists();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Serializacion de un Personaje completo (agregacion/composicion)
    // ─────────────────────────────────────────────────────────

    /**
     * Serializa un {@link Personaje} con todo su grafo: arma (agregacion), gremio (asociacion
     * bidireccional), inventario con pociones (composicion) y efectos. Los hilos de efectos son
     * {@code transient} y no se guardan.
     */
    public boolean guardarPersonaje(Personaje personaje) {
        synchronized (lock) {
            File archivo = new File(DIRECTORIO, ARCHIVO_PERSONAJE);
            File dir = archivo.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(archivo))) {
                synchronized (personaje) {
                    oos.writeObject(personaje);
                }
                return true;
            } catch (IOException e) {
                System.err.println("[GestorPartidas] Error al guardar personaje: " + e.getMessage());
                return false;
            }
        }
    }

    /** @return el personaje deserializado (con filtro de clases) o null si no existe o falla */
    public Personaje cargarPersonaje() {
        synchronized (lock) {
            File archivo = new File(DIRECTORIO, ARCHIVO_PERSONAJE);
            if (!archivo.exists()) {
                System.out.println("[GestorPartidas] No hay personaje guardado.");
                return null;
            }
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(archivo))) {
                ois.setObjectInputFilter(FILTRO_DESERIALIZACION);
                Object obj = ois.readObject();
                return obj instanceof Personaje p ? p : null;
            } catch (ClassNotFoundException | IOException e) {
                System.err.println("[GestorPartidas] Error al cargar personaje: " + e.getMessage());
                return null;
            }
        }
    }
}
