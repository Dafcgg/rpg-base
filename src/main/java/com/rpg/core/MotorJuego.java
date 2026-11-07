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
import java.util.OptionalInt;
import java.util.Random;

/**
 * Coordina el flujo del juego: crear héroe, combatir, torneo, bestiario,
 * temporizador de turnos concurrente y persistencia en disco (manual y autoguardado en segundo plano).
 */
public class MotorJuego {

    private static final String NOMBRE_GREMIO = "Guardianes del Alba";
    private static final int CURACION_POCION_VIDA = 30;
    private static final int SEGUNDOS_TURNO = 15;
    private static final long INTERVALO_AUTOGUARDADO_MS = 30_000L;

    private final Consola consola;
    private final RegistroCombate registro = new RegistroCombate();
    private final Arena arena = new Arena(registro, new Random());
    private final Bestiario bestiario = new Bestiario();
    private PerfilJugador perfil = new PerfilJugador();
    private final Gremio gremio = new Gremio(NOMBRE_GREMIO);
    private final TorneoManager torneo = new TorneoManager(arena, registro);

    // Módulos de Concurrencia y Persistencia
    private final GestorTurno gestorTurno = new GestorTurno(SEGUNDOS_TURNO);
    private final GestorPartidas gestorPartidas = new GestorPartidas();
    private final AutoGuardadoDaemon daemonAutoGuardado;

    private String ultimoGuardado = "";

    public MotorJuego(Consola consola) {
        this.consola = consola;
        this.gestorPartidas.setPerfilActual(this.perfil);

        // Inicialización del AutoGuardadoDaemon como hilo daemon de fondo
        this.daemonAutoGuardado = new AutoGuardadoDaemon(this.gestorPartidas, INTERVALO_AUTOGUARDADO_MS);
        this.daemonAutoGuardado.setDaemon(true); // OBLIGATORIO: invocado antes de start()
        this.daemonAutoGuardado.start();
    }

    // ---- opciones del menú principal

    public void combateRapido() {
        ejecutarCombate(crearJugador());
    }

    private void ejecutarCombate(Personaje jugador) {
        registro.limpiar();
        Enemigo enemigo = elegirEnemigo();
        registro.agregar("%n=== %s vs %s ===", jugador.getNombre(), enemigo.getNombre());

        boolean huyo = combatir(jugador, enemigo);

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
        guardarProgreso();
        gestorPartidas.guardarPerfil(perfil);
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

    // ---- creación de personajes

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
        boolean turnoGastado;
        do {
            registro.agregar(FormatoCombate.resumen(jugador));
            registro.agregar(FormatoCombate.resumen(enemigo));
            Accion accion = pedirAccion();
            if (accion == null) {
                // El temporizador expiro: el turno se pierde por inaccion
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
        } while (!turnoGastado && enemigo.estaVivo());
        return false;
    }

    /**
     * Solicita la acción del jugador integrando un temporizador en hilo concurrente.
     * Si el jugador ingresa su opción a tiempo, el temporizador se cancela con interrupt().
     * Si el tiempo expira, la bandera se marca y el jugador pierde el turno.
     *
     * @return La {@link Accion} seleccionada, o {@code null} si el tiempo se agotó.
     */
    private Accion pedirAccion() {
        // El temporizador despierta la lectura de consola: no hace falta pulsar Enter tarde.
        TurnoCallback callback = consola::expirarEspera;

        gestorTurno.iniciarTemporizador(callback);

        System.out.println("1. Atacar  2. Habilidad especial  3. Usar pocion  4. Ver estado  0. Huir");
        OptionalInt opcion = consola.leerEnteroConLimite("Accion: ", 0, 4);

        // Respondio a tiempo (o expiro): en ambos casos el hilo del temporizador ya no hace falta
        gestorTurno.cancelar();

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
        int eleccion = consola.leerEntero("Pocion (0 = cancelar): ", 0, inventario.tamanio());
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

    private void cerrarCombate(Personaje jugador, Enemigo enemigo, boolean huyo) {
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
     * Guarda el estado en memoria y delega la sincronización de persistencia
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

        // Persistir también a disco de forma sincronizada
        gestorPartidas.guardarPerfil(perfil);
    }

    public String getUltimoGuardado() {
        return ultimoGuardado;
    }

    public PerfilJugador getPerfil() {
        return perfil;
    }

}
