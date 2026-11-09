package com.rpg.entities;

import com.rpg.entities.enums.EstadoPersonaje;
import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.exceptions.CapacidadExcedidaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class DomainAuditTest {

    @Test
    void maximumHealingCannotOverflowOrKillThePatient() {
        Guerrero heroe = new Guerrero("Paciente");
        heroe.recibirDanio(30);
        assertEquals(30, heroe.curar(Integer.MAX_VALUE));
        assertEquals(heroe.getVidaMaxima(), heroe.getVida());
        assertTrue(heroe.estaVivo());
    }

    @Test
    void extremeHealthDamageAndDefenseKeepValidBounds() {
        Guerrero heroe = new Guerrero("Tanque", Integer.MAX_VALUE);
        heroe.setVidaMaxima(Integer.MAX_VALUE);
        heroe.setVida(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, heroe.getDefensa());
        heroe.defender(Integer.MAX_VALUE);
        assertTrue(heroe.getVida() > 0);
        heroe.recibirDanio(Integer.MAX_VALUE);
        assertEquals(0, heroe.getVida());
        assertEquals(EstadoPersonaje.MUERTO, heroe.getEstado());
    }

    @Test
    void highLevelsWeaponsAndSpecialsCannotWrapDamageNegative() {
        Guerrero heroe = new Guerrero("Gigante");
        heroe.setNivel(Integer.MAX_VALUE);
        heroe.equiparArma(new Arma("Espada", Integer.MAX_VALUE, RaridadItem.LEGENDARIO));
        assertEquals(Integer.MAX_VALUE, heroe.calcularDanio());
        Enemigo objetivo = new Enemigo("Objetivo", Integer.MAX_VALUE, 0, 0);
        heroe.realizarAtaqueEspecial(objetivo);
        assertEquals(0, objetivo.getVida());

        Mago mago = new Mago("Mago");
        mago.setNivel(Integer.MAX_VALUE);
        objetivo.restaurarVida();
        assertDoesNotThrow(() -> mago.realizarAtaqueEspecial(objetivo));
        assertEquals(0, objetivo.getVida());
        assertTrue(objetivo.getEfectos().isEmpty());
    }

    @Test
    void zeroAttackDoesNothingAndNegativeAttackIsRejected() {
        Guerrero heroe = new Guerrero("Guarda");
        int vida = heroe.getVida();
        heroe.defender(0);
        assertEquals(vida, heroe.getVida());
        assertThrows(IllegalArgumentException.class, () -> heroe.defender(-1));
        assertEquals(vida, heroe.getVida());
    }

    @Test
    void settingPositiveHealthRestoresAConsistentLivingState() {
        Guerrero heroe = new Guerrero("Renacido");
        heroe.setVida(0);
        heroe.setVida(25);
        assertTrue(heroe.estaVivo());
        assertEquals(EstadoPersonaje.VIVO, heroe.getEstado());
    }

    @Test
    void invalidClassResourcesAndItemsFailBeforeCombat() {
        assertThrows(IllegalArgumentException.class, () -> new Guerrero("Escudo", -100));
        assertThrows(IllegalArgumentException.class, () -> new Mago("Mana", -1));
        assertThrows(IllegalArgumentException.class, () -> new Mago("Mana", Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new EstadoEfecto("", TipoEfecto.DANIO_CONTINUO, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new EstadoEfecto("Veneno", null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Pocion("Mala", -1, RaridadItem.COMUN));
        assertThrows(IllegalArgumentException.class, () -> new Arma("Mala", 1, null));
        assertThrows(NullPointerException.class, () -> new Inventario<>(1).agregar(null));
    }

    @Test
    void invalidTargetsDoNotConsumeResourcesAndDeadCharactersCannotCast() {
        Mago mago = new Mago("Mago");
        assertThrows(NullPointerException.class, () -> mago.realizarAtaqueEspecial(null));
        assertEquals(60, mago.getMana());
        mago.setVida(0);
        Guerrero objetivo = new Guerrero("Objetivo");
        assertDoesNotThrow(() -> mago.realizarAtaqueEspecial(objetivo));
        assertEquals(objetivo.getVidaMaxima(), objetivo.getVida());
        assertEquals(60, mago.getMana());
    }

    @Test
    void restoringMageForNextDuelRestoresManaAndCancelsEffects() throws Exception {
        Mago mago = new Mago("Mago", 0);
        mago.aplicarEfecto(new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 1, 50));
        mago.restaurarVida();
        assertEquals(60, mago.getMana());
        assertEquals(mago.getVidaMaxima(), mago.getVida());
        assertTrue(mago.getEfectos().isEmpty());
    }

    @Test
    void effectCancellationPreventsLateTicksAndExposedEffectsAreCopies() throws Exception {
        Guerrero heroe = new Guerrero("Objetivo");
        heroe.aplicarEfecto(new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 1, 50));
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                while (heroe.getVida() == heroe.getVidaMaxima()) Thread.sleep(5);
            });
            EstadoEfecto copia = heroe.getEfectos().iterator().next();
            assertEquals(49, copia.getTurnosRestantes());
            copia.finalizar();
            assertFalse(heroe.getEfectos().iterator().next().haTerminado());
            heroe.quitarEfecto("Veneno");
            int vidaTrasCancelar = heroe.getVida();
            Thread.sleep(1_100);
            assertEquals(vidaTrasCancelar, heroe.getVida());
            assertTrue(heroe.getEfectos().isEmpty());
        } finally {
            heroe.detenerEfectos();
        }
    }

    @Test
    void savingEffectProgressCannotRepeatTicksAlreadyApplied() throws Exception {
        Guerrero heroe = new Guerrero("Guardado");
        heroe.aplicarEfecto(new EstadoEfecto("Veneno", TipoEfecto.DANIO_CONTINUO, 1, 3));
        byte[] guardado;
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                while (heroe.getVida() == heroe.getVidaMaxima()) Thread.sleep(5);
            });
            guardado = serialize(heroe);
        } finally {
            heroe.detenerEfectos();
        }
        Guerrero cargado = (Guerrero) deserialize(guardado);
        for (int turno = 0; turno < 10; turno++) cargado.procesarEfectos();
        assertEquals(cargado.getVidaMaxima() - 3, cargado.getVida());
        assertTrue(cargado.getEfectos().isEmpty());
    }

    @Test
    void inventoryIteratorIsASnapshotAndConcurrentAddsRespectCapacity() throws Exception {
        Inventario<String> inventario = new Inventario<>(1);
        inventario.agregar("primero");
        Iterator<String> iterador = inventario.iterator();
        inventario.quitar(0);
        assertEquals("primero", iterador.next());
        assertThrows(UnsupportedOperationException.class, iterador::remove);
        try (var ejecutor = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> tareas = new ArrayList<>();
            for (int i = 0; i < 64; i++) tareas.add(() -> {
                try {
                    inventario.agregar("item");
                    return true;
                } catch (CapacidadExcedidaException esperado) {
                    return false;
                }
            });
            long exitos = 0;
            for (var resultado : ejecutor.invokeAll(tareas)) if (resultado.get()) exitos++;
            assertEquals(1, exitos);
            assertEquals(1, inventario.tamanio());
        }
    }

    @Test
    void guildTransferAndSnapshotsKeepBothSidesConsistent() throws Exception {
        Guerrero heroe = new Guerrero("Miembro");
        Gremio primero = new Gremio("Primero");
        Gremio segundo = new Gremio("Segundo");
        primero.agregarMiembro(heroe);
        List<Personaje> instantanea = primero.getMiembros();
        segundo.agregarMiembro(heroe);
        assertSame(segundo, heroe.getGremio());
        assertTrue(primero.getMiembros().isEmpty());
        assertEquals(List.of(heroe), instantanea);
        assertEquals(List.of(heroe), segundo.getMiembros());
        Guerrero cargado = (Guerrero) deserialize(serialize(heroe));
        assertSame(cargado, cargado.getGremio().getMiembros().getFirst());
    }

    @Test
    void deserializationRejectsForgedNegativeHealthAndProfileCounters() throws Exception {
        Guerrero heroe = new Guerrero("Corrupto");
        setField(Personaje.class, heroe, "vida", -1);
        byte[] corrupto = serialize(heroe);
        assertThrows(InvalidObjectException.class, () -> deserialize(corrupto));

        PerfilJugador perfil = new PerfilJugador();
        setField(PerfilJugador.class, perfil, "victorias", -1);
        byte[] perfilCorrupto = serialize(perfil);
        assertThrows(InvalidObjectException.class, () -> deserialize(perfilCorrupto));
    }

    @Test
    void counterOverflowCannotProduceNegativeStatistics() throws Exception {
        PerfilJugador perfil = new PerfilJugador();
        setField(PerfilJugador.class, perfil, "victorias", Integer.MAX_VALUE);
        setField(PerfilJugador.class, perfil, "derrotas", Integer.MAX_VALUE);
        perfil.registrarVictoria();
        perfil.registrarDerrota();
        assertEquals(Integer.MAX_VALUE, perfil.getVictorias());
        assertEquals(Integer.MAX_VALUE, perfil.getDerrotas());
    }

    private static void setField(Class<?> clase, Object objeto, String nombre, Object valor) throws Exception {
        Field campo = clase.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(objeto, valor);
    }

    private static byte[] serialize(Object objeto) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(objeto);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return entrada.readObject();
        }
    }
}
