package com.rpg.persistencia;

import com.rpg.entities.Arma;
import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.PerfilJugador;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.items.Pocion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GestorPartidasTest {
    @TempDir Path directorio;

    @Test
    void falloDeSerializacionConservaLaPartidaAnteriorYNoDejaTemporales() throws Exception {
        GestorPartidas gestor = new GestorPartidas(directorio);
        PerfilJugador perfil = new PerfilJugador();
        perfil.registrarVictoria();
        assertTrue(gestor.guardarPerfil(perfil));
        byte[] anterior = Files.readAllBytes(directorio.resolve("perfil_jugador.dat"));

        assertFalse(gestor.guardarPerfil(new PerfilNoSerializable()));
        assertFalse(gestor.guardarPerfil(null));
        assertArrayEquals(anterior, Files.readAllBytes(directorio.resolve("perfil_jugador.dat")));
        assertEquals(1, gestor.cargarPerfil().getVictorias());
        try (var archivos = Files.list(directorio)) {
            assertEquals(1, archivos.count());
        }
    }

    @Test
    void conservaElGrafoConCiclosYLosCamposTransientSeReconstruyen() throws Exception {
        GestorPartidas gestor = new GestorPartidas(directorio);
        Guerrero heroe = new Guerrero("Conan");
        heroe.unirseAGremio(new Gremio("Auditores"));
        heroe.equiparArma(new Arma("Espada", 12, RaridadItem.COMUN));
        heroe.getInventario().agregar(new Pocion("Vida", 30, RaridadItem.COMUN));
        heroe.aprenderHabilidad("Bloquear");
        // Mantener el monitor impide que el tick cambie el grafo durante el guardado.
        synchronized (heroe) {
            heroe.aplicarEfecto(new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 1, 3));
            assertTrue(gestor.guardarPersonaje(heroe));
            heroe.detenerEfectos();
        }
        Personaje leido = gestor.cargarPersonaje();
        assertNotNull(leido);
        assertSame(leido, leido.getGremio().getMiembros().getFirst());
        assertEquals("Espada", leido.getArma().getNombre());
        assertEquals(1, leido.getInventario().tamanio());
        assertTrue(leido.getHabilidades().contains("Bloquear"));
        assertEquals(3, leido.getEfectos().iterator().next().getTurnosRestantes());
        assertFalse(leido.procesarEfectos().isEmpty());
        leido.recibirDanioConLock(2);
        assertEquals(leido.getVidaMaxima() - 3, leido.getVida());
    }

    @Test
    void unaClaseDelPaqueteNoAutorizadaNoEjecutaReadObject() throws Exception {
        GestorPartidas gestor = new GestorPartidas(directorio);
        ClaseNoAutorizada.ejecutada.set(false);
        escribirObjeto("perfil_jugador.dat", new ClaseNoAutorizada());
        assertNull(gestor.cargarPerfil());
        assertFalse(ClaseNoAutorizada.ejecutada.get());
    }

    @Test
    void rechazaArrayGiganteAntesDeReservarMemoria() throws Exception {
        GestorPartidas gestor = new GestorPartidas(directorio);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(new Object[0]);
        }
        byte[] forjado = bytes.toByteArray();
        ByteBuffer.wrap(forjado, forjado.length - Integer.BYTES, Integer.BYTES).putInt(Integer.MAX_VALUE);
        Files.write(directorio.resolve("perfil_jugador.dat"), forjado);
        assertNull(gestor.cargarPerfil());
    }

    @Test
    void rechazaArchivosCorruptosGrandesYObjetosConInvariantesInvalidas() throws Exception {
        GestorPartidas gestor = new GestorPartidas(directorio);
        Path archivo = directorio.resolve("perfil_jugador.dat");
        Files.write(archivo, new byte[] {1, 2, 3});
        assertNull(gestor.cargarPerfil());
        // En Windows el borrado falla si quedo abierto el FileInputStream del constructor.
        assertTrue(Files.deleteIfExists(archivo));
        Files.write(archivo, new byte[1_048_577]);
        assertNull(gestor.cargarPerfil());
        PerfilJugador manipulado = new PerfilJugador();
        Field victorias = PerfilJugador.class.getDeclaredField("victorias");
        victorias.setAccessible(true);
        victorias.setInt(manipulado, -1);
        escribirObjeto("perfil_jugador.dat", manipulado);
        assertNull(gestor.cargarPerfil());
    }

    @Test
    void directorioInaccesibleDevuelveFalloSinCrearGuardado() throws Exception {
        Path archivo = directorio.resolve("ocupado");
        Files.writeString(archivo, "no es un directorio");
        GestorPartidas gestor = new GestorPartidas(archivo);
        assertFalse(gestor.guardarPerfil(new PerfilJugador()));
        gestor.setPerfilActual(new PerfilJugador());
        assertFalse(gestor.guardarPerfilAutomatico());
        assertEquals("no es un directorio", Files.readString(archivo));
    }

    @Test
    void noConfirmaUnGuardadoQueSuPropioFiltroRechazaria() throws Exception {
        GestorPartidas gestor = new GestorPartidas(directorio);
        PerfilJugador anterior = new PerfilJugador();
        anterior.registrarVictoria();
        assertTrue(gestor.guardarPerfil(anterior));
        byte[] bytesAnteriores = Files.readAllBytes(directorio.resolve("perfil_jugador.dat"));

        assertFalse(gestor.guardarPerfil(new SubclaseSerializable()));
        PerfilJugador demasiadasReferencias = new PerfilJugador();
        for (int i = 0; i < 10_001; i++) demasiadasReferencias.desbloquearLogro("Logro " + i);
        assertFalse(gestor.guardarPerfil(demasiadasReferencias));
        assertArrayEquals(bytesAnteriores, Files.readAllBytes(directorio.resolve("perfil_jugador.dat")));
        assertEquals(1, gestor.cargarPerfil().getVictorias());
    }

    @Test
    void limitaLaSalidaDuranteLaEscrituraYConservaElAnterior() throws Exception {
        GestorPartidas gestor = new GestorPartidas(directorio);
        assertTrue(gestor.guardarPerfil(new PerfilJugador()));
        byte[] anterior = Files.readAllBytes(directorio.resolve("perfil_jugador.dat"));
        PerfilJugador enorme = new PerfilJugador();
        enorme.desbloquearLogro("X".repeat(1_048_576));
        assertFalse(gestor.guardarPerfil(enorme));
        assertArrayEquals(anterior, Files.readAllBytes(directorio.resolve("perfil_jugador.dat")));
        try (var archivos = Files.list(directorio)) {
            assertEquals(1, archivos.count());
        }
    }

    @Test
    void limiteDelFlujoNoDependeDeLaComprobacionPreviaDelTamano() throws Exception {
        // Simula que el archivo crecio tras Files.size(), sin introducir una carrera aleatoria.
        Class<?> tipo = Class.forName("com.rpg.persistencia.GestorPartidas$EntradaLimitada");
        var constructor = tipo.getDeclaredConstructor(InputStream.class);
        constructor.setAccessible(true);
        try (InputStream entrada = (InputStream) constructor.newInstance(
                new ByteArrayInputStream(new byte[1_048_577]))) {
            assertEquals(1_048_576, entrada.skip(2_000_000));
            assertThrows(IOException.class, entrada::read);
        }
    }

    private void escribirObjeto(String archivo, Object objeto) throws IOException {
        try (ObjectOutputStream salida = new ObjectOutputStream(Files.newOutputStream(directorio.resolve(archivo)))) {
            salida.writeObject(objeto);
        }
    }

    static class PerfilNoSerializable extends PerfilJugador {
        @Serial private static final long serialVersionUID = 1L;
        private final Object noSerializable = new Object();
    }

    static class SubclaseSerializable extends PerfilJugador {
        @Serial private static final long serialVersionUID = 1L;
    }

    static class ClaseNoAutorizada implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        static final AtomicBoolean ejecutada = new AtomicBoolean();
        @Serial private void readObject(ObjectInputStream entrada) throws IOException, ClassNotFoundException {
            entrada.defaultReadObject();
            ejecutada.set(true);
        }
    }
}
