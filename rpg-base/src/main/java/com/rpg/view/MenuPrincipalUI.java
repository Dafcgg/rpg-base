package com.rpg.view;

import com.rpg.controller.VictoriaController;
import com.rpg.core.Consola;
import com.rpg.core.EntradaConsola;
import com.rpg.core.MotorJuego;
import com.rpg.model.Jugador;

import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Vista principal de la aplicacion: login, menu del juego y submenus.
 * Toda la logica de menus que antes vivia en Main esta aqui; Main solo la arranca.
 */
public class MenuPrincipalUI {

    private final Scanner scanner;
    private final Consola consola;

    public MenuPrincipalUI() {
        EntradaConsola entrada = new EntradaConsola(System.in);
        this.scanner = new Scanner(entrada);
        this.consola = new Consola(scanner, entrada);
    }

    public void iniciar() {
        try {
            LoginUI loginUI = new LoginUI(scanner);
            Jugador jugadorActual = loginUI.mostrarMenuLogin();

            if (jugadorActual == null) {
                System.out.println("\nSaliendo sin sesion iniciada. Algunas funciones estaran deshabilitadas.");
                ejecutarJuego(null);
                return;
            }

            System.out.println("\n=== Sesion activa: " + jugadorActual.getUsuario()
                    + " [" + jugadorActual.getRol() + "] ===");
            ejecutarJuego(jugadorActual);
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada finalizada. Hasta luego.");
        } finally {
            scanner.close();
        }
    }

    private void ejecutarJuego(Jugador jugadorActual) {
        MotorJuego motor = new MotorJuego(consola);
        HeroeUI heroeUI = new HeroeUI(scanner, motor);
        boolean esAdmin  = jugadorActual != null && jugadorActual.esAdmin();

        // Controller de victorias para integracion con el combate
        VictoriaController victoriaCtrl = jugadorActual != null
                ? new VictoriaController(jugadorActual)
                : null;

        int opcion;
        do {
            mostrarMenu(esAdmin, jugadorActual != null);
            opcion = consola.leerEntero("Opcion: ", 0, esAdmin ? 12 : 11);

            switch (opcion) {
                case 1 -> {
                    // Combate rapido con registro automatico de victoria en BD
                    motor.combateRapido();
                    if (victoriaCtrl != null) {
                        // Intentar registrar en BD si el enemigo coincide con un monstruo de la tabla
                        String ultimoEnemigo = motor.getUltimoEnemigoNombre();
                        if (ultimoEnemigo != null && motor.ganoElUltimoCombate()) {
                            victoriaCtrl.intentarRegistrarPorNombre(ultimoEnemigo);
                        }
                    }
                }
                case 2 -> motor.jugarTorneo();
                case 3 -> motor.mostrarBestiario();
                case 4 -> {
                    System.out.println("\n--- Guardando Partida ---");
                    motor.guardarPartidaEnDisco();
                    motor.verMetadatosGuardado();
                }
                case 5 -> {
                    System.out.println("\n--- Cargando Partida ---");
                    motor.cargarPartidaDesdeDisco();
                }
                case 6 -> motor.verHistorialCombates();
                case 7 -> {
                    System.out.println("\n--- Borrar Partida Guardada ---");
                    motor.verMetadatosGuardado();
                    String conf = consola.leerLinea("Seguro de eliminar la partida guardada? (s/n): ");
                    if ("s".equalsIgnoreCase(conf) || "si".equalsIgnoreCase(conf)) {
                        motor.borrarPartidaGuardada();
                    } else {
                        System.out.println("Operacion cancelada.");
                    }
                }
                case 8 -> {
                    // Bestiario BD (nuevo modulo 5.2)
                    MonstruoUI monstruoUI = new MonstruoUI(scanner, jugadorActual);
                    monstruoUI.mostrarMenu(esAdmin);
                }
                case 9 -> {
                    // Ranking y victorias BD (nuevo modulo 5.2)
                    RankingUI rankingUI = new RankingUI(scanner, jugadorActual);
                    rankingUI.mostrarMenu(esAdmin);
                }
                case 10 -> heroeUI.iniciar();
                case 11 -> heroeUI.cargarYCombatir();
                case 12 -> {
                    // Solo admin: gestion avanzada de jugadores (cambio de rol)
                    if (esAdmin) {
                        mostrarGestionJugadores(jugadorActual);
                    } else {
                        System.out.println("Opcion no disponible.");
                    }
                }
                case 0 -> System.out.println("Gracias por jugar. Hasta luego!");
                default -> System.out.println("Opcion no reconocida.");
            }
        } while (opcion != 0);
    }

    /**
     * Muestra el menu principal adaptado segun el estado de sesion y el rol.
     *
     * @param esAdmin   si el jugador actual tiene rol admin
     * @param haySeison si hay una sesion activa
     */
    private void mostrarMenu(boolean esAdmin, boolean haySeison) {
        System.out.println("\n=== SIMULADOR DE BATALLA RPG ===");
        System.out.println("--- Juego ---");
        System.out.println("1. Combate rapido");
        System.out.println("2. Torneo");
        System.out.println("3. Ver bestiario (local)");
        System.out.println("--- Partida ---");
        System.out.println("4. Guardar partida");
        System.out.println("5. Cargar partida");
        System.out.println("6. Ver historial de combates (.txt)");
        System.out.println("7. Borrar partida (.dat)");
        System.out.println("--- Arena Segura (BD) ---");
        System.out.println("8. Bestiario en base de datos");
        System.out.println("9. Ranking y victorias");
        System.out.println("--- Registro de Heroes (BD) ---");
        System.out.println("10. Registro de heroes (insertar, listar, buscar, nivel, eliminar)");
        System.out.println("11. Cargar heroe desde BD y combatir");
        if (esAdmin) {
            System.out.println("12. Gestion de jugadores (admin)");
        }
        System.out.println("0. Salir");
    }

    /**
     * Sub-menu de administracion de jugadores: cambiar rol o eliminar.
     * Solo accesible para usuarios con rol admin.
     * Utiliza JugadorController con la sesion del admin para aplicar MVC estricto.
     */
    private void mostrarGestionJugadores(Jugador jugadorActual) {
        com.rpg.controller.JugadorController jc = new com.rpg.controller.JugadorController(jugadorActual);

        System.out.println("\n=== GESTION DE JUGADORES (Admin) ===");
        System.out.println("1. Cambiar rol de jugador");
        System.out.println("2. Eliminar jugador");
        System.out.println("0. Volver");
        int op = consola.leerEntero("Opcion: ", 0, 2);

        switch (op) {
            case 1 -> {
                int id = consola.leerEntero("ID del jugador: ", 1, Integer.MAX_VALUE);
                String rol = consola.leerLinea("Nuevo rol (admin/jugador): ");
                jc.cambiarRol(id, rol.trim());
            }
            case 2 -> {
                int id = consola.leerEntero("ID del jugador a eliminar: ", 1, Integer.MAX_VALUE);
                String conf = consola.leerLinea("Confirmar eliminacion de jugador id=" + id + " (s/n): ");
                if ("s".equalsIgnoreCase(conf)) {
                    jc.eliminar(id);
                } else {
                    System.out.println("Eliminacion cancelada.");
                }
            }
            case 0 -> System.out.println("Volviendo...");
        }
    }
}
