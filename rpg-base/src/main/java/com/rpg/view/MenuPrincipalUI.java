package com.rpg.view;

import com.rpg.core.Consola;
import com.rpg.core.EntradaConsola;
import com.rpg.core.MotorJuego;

import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Vista principal de la aplicacion: menu del juego y submenus.
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
            ejecutarJuego();
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada finalizada. Hasta luego.");
        } finally {
            scanner.close();
        }
    }

    private void ejecutarJuego() {
        MotorJuego motor = new MotorJuego(consola);

        int opcion;
        do {
            mostrarMenu();
            opcion = consola.leerEntero("Opcion: ", 0, 7);

            switch (opcion) {
                case 1 -> motor.combateRapido();
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
                case 0 -> System.out.println("Gracias por jugar. Hasta luego!");
                default -> System.out.println("Opcion no reconocida.");
            }
        } while (opcion != 0);
    }

    private void mostrarMenu() {
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
        System.out.println("0. Salir");
    }
}
