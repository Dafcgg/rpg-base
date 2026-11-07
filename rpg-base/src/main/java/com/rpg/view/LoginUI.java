package com.rpg.view;

import com.rpg.controller.JugadorController;
import com.rpg.model.Jugador;

import java.util.Scanner;

/**
 * Vista de autenticacion y registro de jugadores.
 * <p>
 * <strong>Regla MVC:</strong> esta clase SOLO usa {@link Scanner} y {@code System.out}.
 * No contiene logica de negocio ni importaciones de {@code java.sql}.
 * Toda la coordinacion se delega a {@link JugadorController}.
 * </p>
 */
public class LoginUI {

    private final Scanner            scanner;
    private final JugadorController  jugadorController;

    public LoginUI(Scanner scanner) {
        this.scanner           = scanner;
        this.jugadorController = new JugadorController();
    }

    /**
     * Muestra el menu de autenticacion en bucle hasta que el usuario inicie sesion
     * correctamente, decida salir o se bloquee por intentos fallidos.
     *
     * @return el {@link Jugador} autenticado, o {@code null} si el usuario opto por salir
     */
    public Jugador mostrarMenuLogin() {
        System.out.println("\n===========================================");
        System.out.println("   ARENA SEGURA - Unidad 5.2 JDBC + MVC");
        System.out.println("===========================================");

        while (true) {
            if (jugadorController.estaBloqueado()) {
                System.out.println("\n[LoginUI] Acceso bloqueado. Reinicie la aplicacion.");
                return null;
            }

            System.out.println("\n--- Menu de Acceso ---");
            System.out.println("1. Iniciar sesion");
            System.out.println("2. Registrar nuevo jugador");
            System.out.println("0. Volver al menu principal");
            System.out.print("Opcion: ");

            String lineaOpcion = scanner.nextLine().trim();
            int opcion;
            try {
                opcion = Integer.parseInt(lineaOpcion);
            } catch (NumberFormatException e) {
                System.out.println("Opcion no valida. Ingresa un numero.");
                continue;
            }

            switch (opcion) {
                case 1 -> {
                    Jugador jugador = solicitarLogin();
                    if (jugador != null) {
                        return jugador;
                    }
                }
                case 2 -> solicitarRegistro();
                case 0 -> {
                    System.out.println("Volviendo al menu principal...");
                    return null;
                }
                default -> System.out.println("Opcion no reconocida. Elige 0, 1 o 2.");
            }
        }
    }

    // ---------------------------------------------------------------- metodos privados

    /**
     * Solicita credenciales al usuario y delega la autenticacion al controller.
     *
     * @return {@link Jugador} si el login fue exitoso, {@code null} en caso contrario
     */
    private Jugador solicitarLogin() {
        System.out.println("\n-- Iniciar Sesion --");
        System.out.print("Usuario: ");
        String usuario = scanner.nextLine().trim();

        System.out.print("Contrasena: ");
        String clave = scanner.nextLine().trim();

        return jugadorController.login(usuario, clave);
    }

    /**
     * Solicita datos para registrar un nuevo jugador y delega al controller.
     */
    private void solicitarRegistro() {
        System.out.println("\n-- Registrar Nuevo Jugador --");
        System.out.print("Nombre de usuario deseado: ");
        String usuario = scanner.nextLine().trim();

        System.out.print("Contrasena (minimo 4 caracteres): ");
        String clave = scanner.nextLine().trim();

        System.out.print("Confirmar contrasena: ");
        String confirmacion = scanner.nextLine().trim();

        if (!clave.equals(confirmacion)) {
            System.out.println("[LoginUI] Las contrasenas no coinciden. Registro cancelado.");
            return;
        }

        jugadorController.registrar(usuario, clave);
    }

    /** @return el controller de jugadores gestionado por esta vista */
    public JugadorController getJugadorController() {
        return jugadorController;
    }
}
