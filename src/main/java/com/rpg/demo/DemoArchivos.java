package com.rpg.demo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

/**
 * Demostraciones del taller de archivos que no forman parte del juego:
 * ruta relativa vs absoluta, {@code File.createNewFile()} con {@code IOException} y
 * {@code NotSerializableException} (provocada y resuelta).
 *
 * <h2>Ruta relativa vs absoluta</h2>
 * <p>Una ruta <b>relativa</b> ({@code partidas/demo.tmp}) se interpreta a partir del directorio de trabajo
 * del proceso ({@code user.dir}); su significado cambia segun desde donde se ejecute el programa.
 * Una ruta <b>absoluta</b> ({@code /home/ana/rpg-base/partidas/demo.tmp} o {@code C:\\rpg\\partidas\\demo.tmp})
 * identifica el archivo sin depender del directorio de trabajo. {@code File.getPath()} devuelve la ruta tal
 * como se construyo y {@code File.getAbsolutePath()} la resuelve contra {@code user.dir}.</p>
 *
 * <h2>serialVersionUID</h2>
 * <p>Es el identificador de version de una clase serializable. Al serializar se escribe junto con los datos;
 * al deserializar, Java compara el valor guardado con el de la clase actual y, si no coinciden, lanza
 * {@link java.io.InvalidClassException}. Declararlo a mano permite cambios compatibles (agregar un campo)
 * sin invalidar archivos viejos y obliga a cambiarlo conscientemente cuando el cambio es incompatible.
 * Si no se declara, Java lo calcula a partir de la estructura de la clase y cualquier cambio menor lo altera.</p>
 */
public final class DemoArchivos {

    private DemoArchivos() {
    }

    // ------------------------------------------------------------------ rutas

    /** @return {@code true} si la ruta relativa no es absoluta y su version absoluta si lo es */
    public static boolean mostrarRutas() {
        File relativa = new File("partidas", "demo_rutas.tmp");
        File absoluta = relativa.getAbsoluteFile();
        System.out.println("[DemoArchivos] Relativa : " + relativa.getPath() + " (isAbsolute=" + relativa.isAbsolute() + ")");
        System.out.println("[DemoArchivos] Absoluta : " + absoluta.getPath() + " (isAbsolute=" + absoluta.isAbsolute() + ")");
        return !relativa.isAbsolute() && absoluta.isAbsolute();
    }

    // ----------------------------------------------------------- createNewFile

    /**
     * Crea un archivo con {@code createNewFile()}: {@code true} la primera vez, {@code false} si ya existe,
     * y {@link IOException} si no se puede crear (por ejemplo, la carpeta no existe).
     *
     * @return {@code true} si se observaron los tres comportamientos
     */
    public static boolean demostrarCreateNewFile() {
        File carpeta = new File("partidas");
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            System.out.println("[DemoArchivos] No se pudo crear la carpeta 'partidas'.");
            return false;
        }
        File archivo = new File(carpeta, "demo_createNewFile.tmp");
        boolean primera = false;
        boolean segunda = true;
        try {
            primera = archivo.createNewFile();
            System.out.println("[DemoArchivos] createNewFile (1a vez): " + primera);
            segunda = archivo.createNewFile();
            System.out.println("[DemoArchivos] createNewFile (2a vez, ya existe): " + segunda);
        } catch (IOException e) {
            System.out.println("[DemoArchivos] No se pudo crear el archivo: " + e.getMessage());
        } finally {
            if (archivo.exists() && !archivo.delete()) {
                System.out.println("[DemoArchivos] No se pudo borrar " + archivo.getName());
            }
        }

        // Caso de error: la carpeta padre no existe -> IOException
        File imposible = new File("carpeta_que_no_existe_xyz", "datos.tmp");
        boolean lanzoIOException = false;
        try {
            imposible.createNewFile();
        } catch (IOException e) {
            lanzoIOException = true;
            System.out.println("[DemoArchivos] IOException esperada: " + e.getMessage());
        }
        return primera && !segunda && lanzoIOException;
    }

    // ------------------------------------------------------ NotSerializableException

    /** Clase NO serializable: sera el objeto anidado (composicion) que provoca el error. */
    static final class Mochila {
        private final int capacidad;

        Mochila(int capacidad) {
            this.capacidad = capacidad;
        }

        int getCapacidad() {
            return capacidad;
        }
    }

    /**
     * Version defectuosa: es {@link Serializable} pero compone una {@link Mochila} que no lo es.
     * El aviso {@code [serial]} del compilador se silencia a proposito: el defecto es el objeto de la demo.
     */
    @SuppressWarnings("serial")
    static final class HeroeDefectuoso implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String nombre;
        private final Mochila mochila; // NO serializable -> NotSerializableException

        HeroeDefectuoso(String nombre, Mochila mochila) {
            this.nombre = nombre;
            this.mochila = mochila;
        }
    }

    /** Solucion A: el objeto anidado tambien implementa {@link Serializable}. */
    static final class MochilaSerializable implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final int capacidad;

        MochilaSerializable(int capacidad) {
            this.capacidad = capacidad;
        }

        int getCapacidad() {
            return capacidad;
        }
    }

    static final class HeroeCorregido implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String nombre;
        private final MochilaSerializable mochila;

        HeroeCorregido(String nombre, MochilaSerializable mochila) {
            this.nombre = nombre;
            this.mochila = mochila;
        }
    }

    /** Solucion B: el campo se marca {@code transient} y simplemente no se guarda. */
    static final class HeroeConTransient implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String nombre;
        private final transient Mochila mochila; // no se serializa: vuelve null al leer

        HeroeConTransient(String nombre, Mochila mochila) {
            this.nombre = nombre;
            this.mochila = mochila;
        }
    }

    /**
     * Serializa en memoria (sin tocar el disco) para provocar y resolver la excepcion.
     *
     * @return {@code true} si la version defectuosa falla y las dos soluciones funcionan
     */
    public static boolean demostrarNotSerializableException() {
        boolean fallo = false;
        try {
            serializar(new HeroeDefectuoso("Aquiles", new Mochila(10)));
        } catch (NotSerializableException e) {
            fallo = true;
            System.out.println("[DemoArchivos] NotSerializableException provocada por: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("[DemoArchivos] Error de E/S inesperado: " + e.getMessage());
        }

        boolean solucionA = false;
        try {
            solucionA = serializar(new HeroeCorregido("Aquiles", new MochilaSerializable(10))) > 0;
            System.out.println("[DemoArchivos] Solucion A (el anidado implementa Serializable): OK");
        } catch (IOException e) {
            System.out.println("[DemoArchivos] Solucion A fallo: " + e.getMessage());
        }

        boolean solucionB = false;
        try {
            solucionB = serializar(new HeroeConTransient("Aquiles", new Mochila(10))) > 0;
            System.out.println("[DemoArchivos] Solucion B (campo transient): OK");
        } catch (IOException e) {
            System.out.println("[DemoArchivos] Solucion B fallo: " + e.getMessage());
        }
        return fallo && solucionA && solucionB;
    }

    private static int serializar(Object objeto) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(objeto);
        }
        return bytes.size();
    }

    public static void main(String[] args) {
        mostrarRutas();
        demostrarCreateNewFile();
        demostrarNotSerializableException();
    }
}
