package com.rpg.persistencia;

import com.rpg.entities.PerfilJugador;
import com.rpg.entities.Personaje;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;

/** Persistencia local con reemplazo atomico y deserializacion limitada al dominio. */
public class GestorPartidas {

    private static final String ARCHIVO_PERFIL = "perfil_jugador.dat";
    private static final String ARCHIVO_AUTO = "perfil_auto.dat";
    private static final String ARCHIVO_PERSONAJE = "personaje.dat";
    private static final long MAX_BYTES = 1_048_576;
    private static final Set<String> CLASES_PERMITIDAS = Set.of(
            "com.rpg.entities.Personaje", "com.rpg.entities.Guerrero", "com.rpg.entities.Mago",
            "com.rpg.entities.Enemigo", "com.rpg.entities.PerfilJugador", "com.rpg.entities.Arma",
            "com.rpg.entities.Gremio", "com.rpg.entities.EstadoEfecto", "com.rpg.items.Inventario",
            "com.rpg.items.Pocion", "com.rpg.entities.enums.TipoPersonaje",
            "com.rpg.entities.enums.EstadoPersonaje", "com.rpg.entities.enums.TipoEfecto",
            "com.rpg.entities.enums.RaridadItem", "java.lang.String", "java.lang.Object",
            "java.lang.Enum", "java.util.ArrayList", "java.util.HashSet", "java.util.TreeSet",
            "java.util.HashMap", "java.util.LinkedHashMap", "java.util.Map$Entry");

    // FIX: todas las instancias coordinan reemplazos y borrados del mismo archivo.
    private static final Object LOCK_ARCHIVOS = new Object();
    private final Path directorio;
    private volatile PerfilJugador perfilActual;

    public GestorPartidas() {
        this(Path.of("partidas"));
    }

    /** Permite aislar partidas de jugadores y pruebas sin modificar user.dir. */
    public GestorPartidas(Path directorio) {
        this.directorio = Objects.requireNonNull(directorio, "directorio").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directorio);
        } catch (IOException e) {
            // FIX: informar el fallo; guardar() devolvera false sin fingir persistencia.
            System.err.println("[GestorPartidas] No se pudo crear el directorio: " + e.getMessage());
        }
    }

    public void setPerfilActual(PerfilJugador perfil) {
        synchronized (LOCK_ARCHIVOS) {
            this.perfilActual = perfil;
        }
    }

    /** @return false ante fallo; nunca informa exito sin confirmar el reemplazo. */
    public boolean guardarPerfil(PerfilJugador perfil) {
        return guardar(ARCHIVO_PERFIL, perfil);
    }

    public boolean guardarPerfilAutomatico() {
        synchronized (LOCK_ARCHIVOS) {
            // FIX: cambiar de perfil no permite un guardado tardio del perfil anterior.
            PerfilJugador perfil = perfilActual;
            return perfil != null && guardar(ARCHIVO_AUTO, perfil);
        }
    }

    public PerfilJugador cargarPerfil() {
        return cargar(ARCHIVO_PERFIL, PerfilJugador.class);
    }

    public boolean guardarPersonaje(Personaje personaje) {
        return guardar(ARCHIVO_PERSONAJE, personaje);
    }

    public Personaje cargarPersonaje() {
        return cargar(ARCHIVO_PERSONAJE, Personaje.class);
    }

    private boolean guardar(String nombreArchivo, Object objeto) {
        if (objeto == null) {
            System.err.println("[GestorPartidas] No se puede guardar un objeto nulo.");
            return false;
        }
        synchronized (LOCK_ARCHIVOS) {
            Path temporal = null;
            try {
                Files.createDirectories(directorio);
                temporal = Files.createTempFile(directorio, nombreArchivo + ".", ".tmp");
                // FIX: escribir/cerrar/forzar el temporal antes de tocar una partida valida.
                try (FileOutputStream archivo = new FileOutputStream(temporal.toFile());
                     ObjectOutputStream salida = new ObjectOutputStream(new SalidaLimitada(archivo))) {
                    synchronized (objeto) {
                        salida.writeObject(objeto);
                    }
                    salida.flush();
                    archivo.getFD().sync();
                }
                // FIX: solo confirmar partidas que el propio lector pueda reconstruir.
                Class<?> tipoEsperado = objeto instanceof PerfilJugador ? PerfilJugador.class : Personaje.class;
                leerValidado(temporal, tipoEsperado);
                // Sin fallback no atomico: si el sistema no lo soporta se conserva el anterior.
                Files.move(temporal, directorio.resolve(nombreArchivo),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException | ClassNotFoundException | RuntimeException e) {
                System.err.println("[GestorPartidas] No se pudo guardar la partida: " + e.getMessage());
                return false;
            } finally {
                if (temporal != null) {
                    try {
                        Files.deleteIfExists(temporal);
                    } catch (IOException e) {
                        System.err.println("[GestorPartidas] No se pudo limpiar el temporal: " + e.getMessage());
                    }
                }
            }
        }
    }

    private <T> T cargar(String nombreArchivo, Class<T> tipo) {
        synchronized (LOCK_ARCHIVOS) {
            Path ruta = directorio.resolve(nombreArchivo);
            if (!Files.exists(ruta)) {
                System.out.println("[GestorPartidas] No hay partida guardada.");
                return null;
            }
            try {
                // FIX: limite previo incluye Strings, a los que ObjectInputFilter no filtra.
                if (Files.size(ruta) > MAX_BYTES) {
                    throw new InvalidObjectException("La partida supera el limite de 1 MiB");
                }
                return leerValidado(ruta, tipo);
            } catch (IOException | ClassNotFoundException | RuntimeException e) {
                System.err.println("[GestorPartidas] No se pudo cargar la partida: " + e.getMessage());
                return null;
            }
        }
    }

    private static <T> T leerValidado(Path ruta, Class<T> tipo) throws IOException, ClassNotFoundException {
        // FIX: el limite del flujo cierra la carrera entre comprobar el tamano y leer Strings.
        // Declarar tambien el archivo evita fugas si el constructor lee una cabecera corrupta.
        try (FileInputStream archivo = new FileInputStream(ruta.toFile());
             ObjectInputStream entrada = new EntradaObjetosLimitada(archivo)) {
            entrada.setObjectInputFilter(GestorPartidas::filtrar);
            Object objeto = entrada.readObject();
            if (!tipo.isInstance(objeto)) {
                throw new InvalidObjectException("Tipo de partida inesperado");
            }
            return tipo.cast(objeto);
        }
    }

    private static final class EntradaObjetosLimitada extends ObjectInputStream {
        private long objetos;

        private EntradaObjetosLimitada(InputStream origen) throws IOException {
            super(new EntradaLimitada(origen));
            enableResolveObject(true);
        }

        @Override protected Object resolveObject(Object objeto) throws IOException {
            // FIX: Strings no pasan por ObjectInputFilter; contar tambien esos objetos.
            if (++objetos > 10_000) throw new InvalidObjectException("Demasiados objetos en la partida");
            return objeto;
        }
    }

    private static final class EntradaLimitada extends FilterInputStream {
        private long restantes = MAX_BYTES;

        private EntradaLimitada(InputStream origen) {
            super(origen);
        }

        private void comprobarLimite() throws IOException {
            if (restantes == 0) throw new IOException("La partida supera el limite de 1 MiB");
        }

        @Override public int read() throws IOException {
            comprobarLimite();
            int valor = in.read();
            if (valor != -1) restantes--;
            return valor;
        }

        @Override public int read(byte[] bytes, int desde, int largo) throws IOException {
            Objects.checkFromIndexSize(desde, largo, bytes.length);
            if (largo == 0) return 0;
            comprobarLimite();
            int leidos = in.read(bytes, desde, (int) Math.min(largo, restantes));
            if (leidos > 0) restantes -= leidos;
            return leidos;
        }

        @Override public long skip(long cantidad) throws IOException {
            if (cantidad <= 0) return 0;
            comprobarLimite();
            long saltados = in.skip(Math.min(cantidad, restantes));
            restantes -= saltados;
            return saltados;
        }
    }

    private static final class SalidaLimitada extends FilterOutputStream {
        private long restantes = MAX_BYTES;

        private SalidaLimitada(OutputStream destino) {
            super(destino);
        }

        private void reservar(int cantidad) throws IOException {
            if (cantidad > restantes) throw new IOException("La partida supera el limite de 1 MiB");
            restantes -= cantidad;
        }

        @Override public void write(int valor) throws IOException {
            reservar(1);
            out.write(valor);
        }

        @Override public void write(byte[] bytes, int desde, int largo) throws IOException {
            Objects.checkFromIndexSize(desde, largo, bytes.length);
            reservar(largo);
            out.write(bytes, desde, largo);
        }
    }

    private static ObjectInputFilter.Status filtrar(ObjectInputFilter.FilterInfo info) {
        // FIX: lista cerrada, profundidad, referencias, bytes y arrays acotados contra DoS.
        if (info.depth() > 32 || info.references() > 10_000 || info.streamBytes() > MAX_BYTES
                || info.arrayLength() > 10_000) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> tipo = info.serialClass();
        if (tipo == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        while (tipo.isArray()) {
            tipo = tipo.getComponentType();
        }
        return tipo.isPrimitive() || CLASES_PERMITIDAS.contains(tipo.getName())
                ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
    }

    public void mostrarMetadatos() {
        synchronized (LOCK_ARCHIVOS) {
            File archivo = directorio.resolve(ARCHIVO_PERFIL).toFile();
            System.out.println("\n=== METADATOS DE PARTIDA ===");
            System.out.println("  Nombre   : " + archivo.getName());
            System.out.println("  Ruta abs : " + archivo.getAbsolutePath());
            System.out.println("  Existe   : " + archivo.exists());
            System.out.printf("  Tamano   : %d bytes%n", archivo.length());
        }
    }

    /** API clasica conservada para la demostracion del taller. */
    public boolean eliminarAutoguardado() {
        synchronized (LOCK_ARCHIVOS) {
            return directorio.resolve(ARCHIVO_AUTO).toFile().delete();
        }
    }

    public boolean eliminarPartida() {
        synchronized (LOCK_ARCHIVOS) {
            try {
                boolean eliminado = Files.deleteIfExists(directorio.resolve(ARCHIVO_PERFIL));
                Files.deleteIfExists(directorio.resolve(ARCHIVO_AUTO));
                return eliminado;
            } catch (IOException e) {
                System.err.println("[GestorPartidas] No se pudo eliminar la partida: " + e.getMessage());
                return false;
            }
        }
    }

    public boolean existePartida() {
        synchronized (LOCK_ARCHIVOS) {
            return Files.isRegularFile(directorio.resolve(ARCHIVO_PERFIL));
        }
    }
}
