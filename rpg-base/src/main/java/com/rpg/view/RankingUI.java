package com.rpg.view;

import com.rpg.controller.VictoriaController;
import com.rpg.model.Jugador;
import com.rpg.model.RankingDTO;
import com.rpg.model.Victoria;

import java.util.List;
import java.util.Scanner;

/**
 * Vista para la consulta del ranking global y el historial de victorias.
 * <p>
 * <strong>Regla MVC:</strong> SOLO usa {@link Scanner} y {@code System.out}.
 * No contiene logica de negocio ni importaciones de {@code java.sql} ni DAO.
 * </p>
 */
public class RankingUI {

    private final Scanner            scanner;
    private final VictoriaController victoriaController;

    public RankingUI(Scanner scanner, Jugador jugadorActual) {
        this.scanner            = scanner;
        this.victoriaController = new VictoriaController(jugadorActual);
    }

    /**
     * Muestra el menu de ranking e historial en bucle hasta que el usuario salga.
     *
     * @param esAdmin si es true, muestra opciones adicionales de consulta por usuario
     */
    public void mostrarMenu(boolean esAdmin) {
        boolean continuar = true;
        while (continuar) {
            System.out.println("\n=== RANKING Y VICTORIAS ===");
            System.out.println("1. Ver ranking global");
            System.out.println("2. Ver mi historial de victorias");
            if (esAdmin) {
                System.out.println("3. Ver historial de otro jugador (admin)");
            }
            System.out.println("0. Volver");
            System.out.print("Opcion: ");

            String lineaOpcion = scanner.nextLine().trim();
            int opcion;
            try {
                opcion = Integer.parseInt(lineaOpcion);
            } catch (NumberFormatException e) {
                System.out.println("Opcion no valida.");
                continue;
            }

            switch (opcion) {
                case 1 -> mostrarRankingGlobal();
                case 2 -> mostrarHistorialPropio();
                case 3 -> {
                    if (esAdmin) {
                        mostrarHistorialDeOtroJugador();
                    } else {
                        System.out.println("Opcion no disponible.");
                    }
                }
                case 0 -> continuar = false;
                default -> System.out.println("Opcion no reconocida.");
            }
        }
    }

    // ---------------------------------------------------------------- pantallas

    private void mostrarRankingGlobal() {
        List<RankingDTO> ranking = victoriaController.obtenerRanking();
        if (ranking.isEmpty()) return;

        System.out.println("\n  #   | Jugador              | Victorias");
        System.out.println("------|----------------------|----------");
        int posicion = 1;
        for (RankingDTO dto : ranking) {
            System.out.printf("  %-4d| %-20s | %d%n",
                    posicion++, dto.getUsuario(), dto.getTotalVictorias());
        }
    }

    private void mostrarHistorialPropio() {
        List<Victoria> victorias = victoriaController.obtenerHistorialPropio();
        if (victorias.isEmpty()) {
            System.out.println("No tienes victorias registradas aun. !A combatir!");
            return;
        }
        System.out.println("\n  Monstruo derrotado   | Fecha");
        System.out.println("---------------------|---------------------");
        for (Victoria v : victorias) {
            String nombre = v.getNombreMonstruo() != null ? v.getNombreMonstruo() : ("Monstruo id=" + v.getMonstruoId());
            System.out.printf("  %-20s | %s%n", nombre, v.getFechaFormateada());
        }
    }

    private void mostrarHistorialDeOtroJugador() {
        System.out.print("Nombre de usuario a consultar: ");
        String usuario = scanner.nextLine().trim();
        List<Victoria> victorias = victoriaController.obtenerHistorialDeJugador(usuario);
        if (victorias.isEmpty()) {
            System.out.println("No hay victorias para el jugador '" + usuario + "'.");
            return;
        }
        System.out.println("\nHistorial de " + usuario + ":");
        System.out.println("  Monstruo derrotado   | Fecha");
        System.out.println("---------------------|---------------------");
        for (Victoria v : victorias) {
            String nombre = v.getNombreMonstruo() != null ? v.getNombreMonstruo() : ("Monstruo id=" + v.getMonstruoId());
            System.out.printf("  %-20s | %s%n", nombre, v.getFechaFormateada());
        }
    }

    /** @return el controller de victorias para uso externo (por ejemplo, registrar victorias de combate) */
    public VictoriaController getVictoriaController() {
        return victoriaController;
    }
}
