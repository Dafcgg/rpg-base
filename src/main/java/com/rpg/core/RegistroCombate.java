package com.rpg.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Imprime cada evento del combate y lo guarda para incluirlo en el guardado de la partida
 * y en el historial persistente en disco.
 *
 * <h2>Investigacion Teorica: FileWriter vs Files.writeString</h2>
 * <ul>
 *   <li><b>FileWriter + BufferedWriter:</b>
 *     Trabaja con un bufer intermedio en memoria (tipicamente 8 KB). Cada llamada a {@code write()}
 *     o {@code newLine()} escribe en dicho bufer y solo descarga fisicamente al disco (flush) cuando
 *     el bufer se llena o se invoca {@code flush()}/{@code close()}.
 *     Es optimo para flujos continuos, logs secuenciales en tiempo real o archivos que crecen
 *     progresivamente con multiples operaciones de escritura pequenas, minimizando llamadas de sistema (syscalls).
 *     Con {@code new FileWriter(file, true)} permite facilmente el modo <i>append</i> (adicion al final).
 *   </li>
 *   <li><b>Files.writeString (Java NIO.2):</b>
 *     Es un metodo de conveniencia de un solo paso que toma un {@link CharSequence} completo, abre el
 *     archivo, escribe los bytes codificados y lo cierra. Es comodo para escribir de una vez un texto
 *     pequeno o mediano que ya esta completo en memoria (configuraciones, un JSON, un volcado).
 *     <b>No garantiza atomicidad a nivel del sistema operativo</b>: si el proceso o la maquina fallan a mitad
 *     de la escritura el archivo puede quedar truncado o incompleto, y otro proceso podria leerlo a medias.
 *     Para obtener una actualizacion "todo o nada" hay que escribir en un archivo temporal y luego usar
 *     {@code Files.move(..., StandardCopyOption.ATOMIC_MOVE)}.
 *     Para registros de eventos frecuentes, invocarlo en cada linea abriria y cerraria el archivo cada vez
 *     (peor rendimiento de E/S): ahi conviene {@code BufferedWriter} en modo append.
 *   </li>
 * </ul>
 */
public class RegistroCombate {

    private static final String ARCHIVO_HISTORIAL_DEFECTO = "historial_combates.txt";
    /** Cerrojo del archivo de historial: escritura y lectura no se solapan entre hilos. */
    private static final Object LOCK_ARCHIVO = new Object();

    /**
     * Consumer funcional para la salida de eventos en consola empleando referencia a metodo.
     */
    private static final Consumer<String> LOG_CONSOLA = System.out::println;

    private final StringBuilder texto = new StringBuilder();
    private static final int MAX_CARACTERES = 1_048_576;
    private Consumer<String> consumidorLog = LOG_CONSOLA;
    private final String rutaHistorial;

    public RegistroCombate() {
        this(ARCHIVO_HISTORIAL_DEFECTO);
    }

    public RegistroCombate(String rutaHistorial) {
        this.rutaHistorial = Objects.requireNonNull(rutaHistorial, "rutaHistorial");
    }

    public synchronized void setConsumidorLog(Consumer<String> consumidorLog) {
        this.consumidorLog = Objects.requireNonNull(consumidorLog, "consumidorLog");
    }

    public void agregar(String linea) {
        Objects.requireNonNull(linea, "linea");
        Consumer<String> salida;
        synchronized (this) {
            // FIX: una sesion larga no retiene un registro ilimitado en memoria.
            String evento = linea.length() > MAX_CARACTERES
                    ? linea.substring(linea.length() - MAX_CARACTERES) : linea;
            texto.append(evento).append(System.lineSeparator());
            if (texto.length() > MAX_CARACTERES) {
                texto.delete(0, texto.length() - MAX_CARACTERES);
            }
            salida = consumidorLog;
        }
        // FIX: no ejecutar callbacks externos mientras se retiene el monitor del registro.
        salida.accept(linea);
    }

    public void agregar(String formato, Object... argumentos) {
        agregar(String.format(formato, argumentos));
    }

    public synchronized void limpiar() {
        texto.setLength(0);
    }

    public synchronized String getTexto() {
        return texto.toString();
    }

    /**
     * Vuelca el contenido acumulado de la sesion de combate actual al archivo por defecto
     * en modo append usando {@link BufferedWriter} y {@link FileWriter}.
     */
    public void volcarAArchivo() {
        volcarAArchivo(rutaHistorial);
    }

    /**
     * Vuelca el contenido de {@code texto} a un archivo de texto especificado en modo append.
     * Utiliza {@code try-with-resources} para garantizar la descarga y cierre del flujo.
     *
     * @param rutaArchivo Ruta del archivo destino.
     */
    public void volcarAArchivo(String rutaArchivo) {
        String contenido = getTexto(); // copia sincronizada del buffer
        if (contenido.isEmpty()) {
            return;
        }
        String separador = System.lineSeparator();
        if (contenido.endsWith(separador)) {
            contenido = contenido.substring(0, contenido.length() - separador.length());
        }
        File archivo = new File(rutaArchivo);
        synchronized (LOCK_ARCHIVO) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(archivo, StandardCharsets.UTF_8, true))) {
                bw.write(contenido);
                bw.newLine();
                System.out.println("[RegistroCombate] Historial volcado exitosamente a: " + archivo.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[RegistroCombate] Error de E/S al volcar historial: " + e.getMessage());
            }
        }
    }

    /**
     * Lee e imprime el historial de combates desde el archivo por defecto usando {@link BufferedReader}.
     * Captura especificamente {@link FileNotFoundException} si aun no existe historial.
     */
    public static void leerHistorial() {
        leerHistorial(ARCHIVO_HISTORIAL_DEFECTO);
    }

    /**
     * Lee e imprime el historial desde la ruta indicada.
     * Utiliza {@code try-with-resources} y manejo exhaustivo de excepciones.
     *
     * @param rutaArchivo Ruta del archivo a leer.
     */
    public static void leerHistorial(String rutaArchivo) {
        File archivo = new File(rutaArchivo);
        System.out.println("\n=== HISTORIAL DE COMBATES (" + archivo.getName() + ") ===");
        synchronized (LOCK_ARCHIVO) {
            try (BufferedReader br = new BufferedReader(new FileReader(archivo, StandardCharsets.UTF_8))) {
                String linea;
                boolean vacio = true;
                while ((linea = br.readLine()) != null) {
                    System.out.println(linea);
                    vacio = false;
                }
                if (vacio) {
                    System.out.println("(El archivo de historial se encuentra vacio).");
                }
            } catch (FileNotFoundException e) {
                System.out.println("[RegistroCombate] No se encontro el archivo de historial: " + archivo.getName()
                        + ". Aun no se han disputado o registrado combates.");
            } catch (IOException e) {
                System.err.println("[RegistroCombate] Error al leer el historial de combates: " + e.getMessage());
            }
        }
    }
}
