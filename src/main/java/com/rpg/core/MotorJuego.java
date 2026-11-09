package com.rpg.core;

import com.rpg.concurrency.AutoGuardadoDaemon;
import com.rpg.concurrency.GestorTurno;
import com.rpg.entities.Enemigo;
import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.PerfilJugador;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.exceptions.CapacidadExcedidaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;
import com.rpg.persistencia.GestorPartidas;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Coordina el flujo del juego: crear heroe, combatir, torneo, bestiario,
 * temporizador de turnos concurrente y persistencia en disco (manual y autoguardado en segundo plano).
 */
public class MotorJuego implements AutoCloseable {

    private static final String NOMBRE_GREMIO = "Guardianes del Alba";
    private static final int CURACION_POCION_VIDA = 30;
    private static final int SEGUNDOS_TURNO = 15;
    private static final long INTERVALO_AUTOGUARDADO_MS = 30_000L;

    private final Consola consola;
    private final RegistroCombate registro;
    private final Arena arena;
    private final Bestiario bestiario = new Bestiario();
    private final Supplier<Enemigo> proveedorEnemigoAleatorio;
    private PerfilJugador perfil = new PerfilJugador();
    private final Gremio gremio = new Gremio(NOMBRE_GREMIO);
    private final TorneoManager torneo;

    // Modulos de Concurrencia y Persistencia
    private final GestorTurno gestorTurno;
    private final GestorPartidas gestorPartidas;
    private final AutoGuardadoDaemon daemonAutoGuardado;

    private String ultimoGuardado = "";

    public MotorJuego(Consola consola) {
        this(consola, SEGUNDOS_TURNO, new GestorPartidas());
    }

    MotorJuego(Consola consola, int segundosTurno, GestorPartidas gestorPartidas) {
        this(consola, segundosTurno, gestorPartidas, new RegistroCombate());
    }

    MotorJuego(Consola consola, int segundosTurno, GestorPartidas gestorPartidas, RegistroCombate registro) {
        this.consola = Objects.requireNonNull(consola, "consola");
        this.registro = Objects.requireNonNull(registro, "registro");
        this.arena = new Arena(registro, new Random());
        this.torneo = new TorneoManager(arena, registro);
        this.gestorTurno = new GestorTurno(segundosTurno);
        this.gestorPartidas = Objects.requireNonNull(gestorPartidas, "gestorPartidas");
        this.proveedorEnemigoAleatorio = bestiario.proveedorEnemigoAleatorio(new Random());
        this.gestorPartidas.setPerfilActual(this.perfil);

        // Inicializacion del AutoGuardadoDaemon como hilo daemon de fondo
        this.daemonAutoGuardado = new AutoGuardadoDaemon(this.gestorPartidas, INTERVALO_AUTOGUARDADO_MS);
        this.daemonAutoGuardado.setDaemon(true); // OBLIGATORIO: invocado antes de start()
        this.daemonAutoGuardado.start();
    }

    // ---- opciones del menu principal

    public void combateRapido() {
        ejecutarCombate(crearJugador());
    }

    public void combateRapidoCon(Personaje jugador) {
        if (jugador == null) {
            throw new IllegalArgumentException("El personaje no puede ser null.");
        }
        if (!jugador.estaVivo()) {
            throw new IllegalArgumentException("Un personaje derrotado no puede iniciar un combate.");
        }
        entregarKitInicial(jugador);
        jugador.unirseAGremio(gremio);
        System.out.printf("%s se une al gremio %s (%d miembros).%n",
                jugador.getNombre(), gremio.getNombre(), gremio.getMiembros().size());
        ejecutarCombate(jugador);
    }

    private void ejecutarCombate(Personaje jugador) {
        registro.limpiar();
        Enemigo enemigo = null;
        boolean huyo;
        try {
            enemigo = elegirEnemigo();
            registro.agregar("%n=== %s vs %s ===", jugador.getNombre(), enemigo.getNombre());
            huyo = combatir(jugador, enemigo);
        } finally {
            // FIX: EOF, excepciones y huida tambien deben cancelar relojes y efectos.
            gestorTurno.cancelar();
            jugador.detenerEfectos();
            if (enemigo != null) {
                enemigo.detenerEfectos();
            }
            // FIX: el gremio representa combatientes activos; no retener cada heroe de la sesion.
            jugador.abandonarGremio();
        }

        cerrarCombate(jugador, enemigo, huyo);
        guardarProgreso();
        // Volcar registro de combate al historial de texto
        registro.volcarAArchivo();
    }

    public void jugarTorneo() {
        registro.limpiar();
        Personaje campeon = torneo.jugarTorneo();
        if (campeon == null) {
            registro.agregar("%nTORNEO CANCELADO: no hay campeon.");
            registro.volcarAArchivo();
            return;
        }
        registro.agregar("%nCAMPEON DEL TORNEO: %s", campeon.getNombre());
        perfil.desbloquearLogro("Campeon del Torneo");
        guardarProgreso();
        registro.volcarAArchivo();
    }

    public void mostrarBestiario() {
        System.out.println("\n=== BESTIARIO ===");
        System.out.print(bestiario.describir());
        System.out.printf("Victorias: %d | Derrotas: %d | Logros: %s%n",
                perfil.getVictorias(), perfil.getDerrotas(), perfil.getLogros());
    }

    // ---- operaciones de persistencia expuestas

    /**
     * Guarda la partida actual tanto en memoria como en archivo binario serializado.
     */
    public void guardarPartidaEnDisco() {
        // FIX: guardarProgreso ya escribe; no duplicar la E/S ni mensajes de exito.
        guardarProgreso();
    }

    /**
     * Carga el progreso del jugador desde el archivo binario persistente.
     */
    public void cargarPartidaDesdeDisco() {
        PerfilJugador cargado = gestorPartidas.cargarPerfil();
        if (cargado != null) {
            this.perfil = cargado;
            this.gestorPartidas.setPerfilActual(this.perfil);
            System.out.println("Progreso restablecido correctamente en el motor de juego.");
        }
    }

    /**
     * Muestra el historial acumulado en el archivo de logs de combate.
     */
    public void verHistorialCombates() {
        RegistroCombate.leerHistorial();
    }

    /**
     * Muestra los metadatos del archivo de guardado principal.
     */
    public void verMetadatosGuardado() {
        gestorPartidas.mostrarMetadatos();
    }

    /**
     * Elimina el archivo de partida guardada en disco.
     */
    public boolean borrarPartidaGuardada() {
        return gestorPartidas.eliminarPartida();
    }

    // ---- creacion de personajes

    private Personaje crearJugador() {
        int clase = consola.leerEntero("Clase (1. Guerrero, 2. Mago): ", 1, 2);
        String nombre = consola.leerNombre("Nombre del heroe: ");
        Personaje jugador = switch (clase) {
            case 1 -> new Guerrero(nombre);
            default -> new Mago(nombre);
        };
        entregarKitInicial(jugador);
        jugador.unirseAGremio(gremio);
        System.out.printf("%s se une al gremio %s (%d miembros).%n",
                jugador.getNombre(), gremio.getNombre(), gremio.getMiembros().size());
        return jugador;
    }

    private void entregarKitInicial(Personaje jugador) {
        List<Pocion> kit = List.of(
                new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN),
                new Pocion("Elixir de Regeneracion", 0, RaridadItem.RARO,
                        new EstadoEfecto("Regeneracion", TipoEfecto.CURACION_CONTINUA, 6, 3), false),
                new Pocion("Antidoto", 0, RaridadItem.COMUN, null, true));
        try {
            for (Pocion pocion : kit) {
                jugador.getInventario().agregar(pocion);
            }
        } catch (CapacidadExcedidaException e) {
            System.out.println("No se pudo entregar todo el kit: " + e.getMessage());
        }
    }

    private Enemigo elegirEnemigo() {
        List<String> nombres = bestiario.nombres();
        System.out.println("Rivales disponibles:");
        for (int i = 0; i < nombres.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, nombres.get(i));
        }
        while (true) {
            String texto = consola.leerLinea("Rival (numero o nombre): ");
            Enemigo enemigo = resolverRival(texto, nombres);
            if (enemigo != null) {
                return enemigo;
            }
            System.out.println("No existe ese rival.");
        }
    }

    private Enemigo resolverRival(String texto, List<String> nombres) {
        try {
            int numero = Integer.parseInt(texto);
            boolean valido = numero >= 1 && numero <= nombres.size();
            return valido ? bestiario.crearEnemigo(nombres.get(numero - 1)) : null;
        } catch (NumberFormatException e) {
            return bestiario.crearEnemigo(texto);
        }
    }

    // ---- bucle de combate

    private boolean combatir(Personaje jugador, Enemigo enemigo) {
        int ronda = 1;
        while (jugador.estaVivo() && enemigo.estaVivo()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new NoSuchElementException("Combate cancelado por interrupcion");
            }
            registro.agregar("%n-- Ronda %d --", ronda);
            if (arena.iniciarTurno(jugador) && turnoJugador(jugador, enemigo)) {
                return true;
            }
            arena.turnoAutomatico(enemigo, jugador);
            ronda++;
        }
        return false;
    }

    private boolean turnoJugador(Personaje jugador, Enemigo enemigo) {
        // FIX: un solo plazo cubre estado, reintentos y seleccion de pociones.
        consola.prepararEsperaConLimite();
        gestorTurno.iniciarTemporizador(consola::expirarEspera);
        try {
            boolean turnoGastado;
            do {
                registro.agregar(FormatoCombate.resumen(jugador));
                registro.agregar(FormatoCombate.resumen(enemigo));
                Accion accion = pedirAccion();
                if (!jugador.estaVivo() || !enemigo.estaVivo()) {
                    return false;
                }
                if (accion == null) {
                    registro.agregar("%s tardo demasiado tiempo en reaccionar y pierde el turno.", jugador.getNombre());
                    return false;
                }
                if (accion == Accion.HUIR) {
                    return true;
                }
                turnoGastado = switch (accion) {
                    case ATACAR -> {
                        arena.atacar(jugador, enemigo);
                        yield true;
                    }
                    case ESPECIAL -> arena.intentarEspecial(jugador, enemigo);
                    case POCION -> usarPocion(jugador);
                    default -> {
                        mostrarEstado(jugador);
                        yield false;
                    }
                };
            } while (!turnoGastado && jugador.estaVivo() && enemigo.estaVivo());
            return false;
        } finally {
            gestorTurno.cancelar();
            consola.finalizarEsperaConLimite();
        }
    }

    /**
     * Solicita una accion bajo el plazo compartido que administra turnoJugador.
     * Ver estado o entrar al inventario no reinicia ese plazo.
     *
     * @return La {@link Accion} seleccionada, o {@code null} si el tiempo se agoto.
     */
    private Accion pedirAccion() {
        System.out.println("1. Atacar  2. Habilidad especial  3. Usar pocion  4. Ver estado  0. Huir");
        OptionalInt opcion = consola.leerEnteroConLimite("Accion: ", 0, 4);

        if (opcion.isEmpty()) {
            return null; // tiempo agotado: el turno se pierde
        }

        return switch (opcion.getAsInt()) {
            case 1 -> Accion.ATACAR;
            case 2 -> Accion.ESPECIAL;
            case 3 -> Accion.POCION;
            case 4 -> Accion.ESTADO;
            default -> Accion.HUIR;
        };
    }

    private boolean usarPocion(Personaje jugador) {
        Inventario<Pocion> inventario = jugador.getInventario();
        if (inventario.estaVacio()) {
            System.out.println("No tienes pociones.");
            return false;
        }
        int numero = 1;
        for (Pocion pocion : inventario) {
            System.out.printf("  %d. %s%n", numero++, pocion.getNombre());
        }
        OptionalInt seleccion = consola.leerEnteroConLimite("Pocion (0 = cancelar): ", 0, inventario.tamanio());
        if (seleccion.isEmpty() || !jugador.estaVivo()) {
            return false;
        }
        int eleccion = seleccion.getAsInt();
        if (eleccion == 0) {
            return false;
        }
        registro.agregar(jugador.usarPocion(inventario.quitar(eleccion - 1)));
        return true;
    }

    private void mostrarEstado(Personaje jugador) {
        System.out.printf("Nivel: %d | Habilidades: %s | Arma: %s | Defensa: %d | Pociones: %d%n",
                jugador.getNivel(), jugador.getHabilidades(),
                jugador.getArma() != null ? jugador.getArma().getNombre() : "ninguna",
                jugador.getDefensa(), jugador.getInventario().tamanio());
        for (EstadoEfecto efecto : jugador.getEfectos()) {
            System.out.printf("Efecto activo: %s (%d turnos)%n", efecto.getNombre(), efecto.getTurnosRestantes());
        }
    }

    // ---- resultado del combate

    /**
     * Construye un resumen dinamico de la batalla utilizando Stream.builder().
     */
    public String generarReporteBatallaBuilder(Personaje jugador, Enemigo enemigo, boolean huyo) {
        Stream.Builder<String> reporteBuilder = Stream.builder();
        reporteBuilder.add("========================================");
        reporteBuilder.add(String.format("REPORTE DE BATALLA: %s vs %s", jugador.getNombre(), enemigo.getNombre()));
        reporteBuilder.add("========================================");
        reporteBuilder.add(String.format("Heroe: %s | Nivel: %d | HP: %d/%d",
                jugador.getNombre(), jugador.getNivel(), jugador.getVida(), jugador.getVidaMaxima()));
        reporteBuilder.add(String.format("Enemigo: %s | HP: %d/%d",
                enemigo.getNombre(), enemigo.getVida(), enemigo.getVidaMaxima()));
        reporteBuilder.add(String.format("Resultado final: %s",
                huyo ? "HUIDA" : (jugador.estaVivo() ? "VICTORIA" : "DERROTA")));
        reporteBuilder.add("========================================");

        return reporteBuilder.build()
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private void cerrarCombate(Personaje jugador, Enemigo enemigo, boolean huyo) {
        String reporteFinal = generarReporteBatallaBuilder(jugador, enemigo, huyo);
        registro.agregar("%n%s", reporteFinal);

        if (huyo) {
            registro.agregar("%s huye del combate.", jugador.getNombre());
        } else if (jugador.estaVivo()) {
            registro.agregar("%nVictoria de %s!", jugador.getNombre());
            perfil.registrarVictoria();
            bestiario.registrarEnemigoDerrotado(enemigo.getNombre());
            desbloquearLogros(jugador, enemigo);
            repartirBotin(jugador);
        } else {
            registro.agregar("%n%s ha caido ante %s.", jugador.getNombre(), enemigo.getNombre());
            perfil.registrarDerrota();
        }
    }

    private void desbloquearLogros(Personaje jugador, Enemigo enemigo) {
        boolean intacto = jugador.getVida() == jugador.getVidaMaxima();
        List<String> candidatos = List.of("Primera Victoria", "Cazador de " + enemigo.getNombre(),
                intacto ? "Intocable" : "");
        for (String logro : candidatos) {
            if (!logro.isEmpty() && perfil.desbloquearLogro(logro)) {
                registro.agregar("Logro desbloqueado: %s", logro);
            }
        }
    }

    private void repartirBotin(Personaje jugador) {
        try {
            jugador.getInventario().agregar(new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN));
            registro.agregar("Botin: Pocion de Vida.");
        } catch (CapacidadExcedidaException e) {
            registro.agregar("Botin perdido: %s", e.getMessage());
        }
    }

    // ---- guardado

    /**
     * Guarda el estado en memoria y delega la sincronizacion de persistencia
     * con {@link GestorPartidas}.
     */
    public void guardarProgreso() {
        StringBuilder contenido = new StringBuilder();
        contenido.append("=== PARTIDA RPG ===").append(System.lineSeparator());
        contenido.append("Victorias: ").append(perfil.getVictorias()).append(System.lineSeparator());
        contenido.append("Derrotas: ").append(perfil.getDerrotas()).append(System.lineSeparator());
        contenido.append("Logros: ").append(String.join(", ", perfil.getLogros())).append(System.lineSeparator());
        contenido.append("Gremio ").append(gremio.getNombre()).append(": ")
                .append(gremio.getMiembros().size()).append(" miembros").append(System.lineSeparator());
        contenido.append("Enemigos derrotados: ").append(bestiario.resumenDerrotados()).append(System.lineSeparator());
        contenido.append(System.lineSeparator()).append("--- Ultimo combate ---").append(System.lineSeparator());
        contenido.append(registro.getTexto());
        ultimoGuardado = contenido.toString();
        System.out.println("[Progreso actualizado en memoria]");

        // Persistir tambien a disco de forma sincronizada
        gestorPartidas.guardarPerfil(perfil);
    }

    public String getUltimoGuardado() {
        return ultimoGuardado;
    }

    public Bestiario getBestiario() {
        return bestiario;
    }

    public Supplier<Enemigo> getProveedorEnemigoAleatorio() {
        return proveedorEnemigoAleatorio;
    }

    public Arena getArena() {
        return arena;
    }

    public RegistroCombate getRegistro() {
        return registro;
    }

    public PerfilJugador getPerfil() {
        return perfil;
    }

    @Override
    public void close() {
        // FIX: un daemon deja de ser necesario al salir de la vista, no solo al morir la JVM.
        gestorTurno.cancelar();
        daemonAutoGuardado.interrupt();
        try {
            daemonAutoGuardado.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
