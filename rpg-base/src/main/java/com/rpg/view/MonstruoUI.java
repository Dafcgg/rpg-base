package com.rpg.view;

import com.rpg.controller.MonstruoController;
import com.rpg.model.Jugador;
import com.rpg.model.Monstruo;

import java.util.List;
import java.util.Scanner;

/**
 * Vista para la gestion del bestiario de monstruos.
 * <p>
 * <strong>Regla MVC:</strong> SOLO usa {@link Scanner} y {@code System.out}.
 * Las operaciones de escritura en BD (crear, editar, eliminar) requieren rol
 * {@code admin}, regla que se aplica en {@link MonstruoController}.
 * </p>
 */
public class MonstruoUI {

    private final Scanner             scanner;
    private final MonstruoController  monstruoController;

    public MonstruoUI(Scanner scanner, Jugador jugadorActual) {
        this.scanner            = scanner;
        this.monstruoController = new MonstruoController(jugadorActual);
    }

    /**
     * Muestra el menu del bestiario. Las opciones de escritura aparecen solo
     * si el jugador tiene rol admin (el Controller las bloqueara de todos modos).
     *
     * @param esAdmin {@code true} si se deben mostrar las opciones de administracion
     */
    public void mostrarMenu(boolean esAdmin) {
        boolean continuar = true;
        while (continuar) {
            System.out.println("\n=== BESTIARIO (BD) ===");
            System.out.println("1. Listar todos los monstruos");
            System.out.println("2. Buscar monstruo por nombre");
            System.out.println("3. Filtrar por vida minima");
            if (esAdmin) {
                System.out.println("--- Administracion (solo admin) ---");
                System.out.println("4. Agregar nuevo monstruo");
                System.out.println("5. Editar monstruo existente");
                System.out.println("6. Eliminar monstruo");
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
                case 1 -> mostrarTodos();
                case 2 -> buscarPorNombre();
                case 3 -> filtrarPorVida();
                case 4 -> agregarMonstruo();
                case 5 -> editarMonstruo();
                case 6 -> eliminarMonstruo();
                case 0 -> continuar = false;
                default -> System.out.println("Opcion no reconocida.");
            }
        }
    }

    // ---------------------------------------------------------------- opciones de consulta

    private void mostrarTodos() {
        List<Monstruo> lista = monstruoController.listarTodos();
        if (lista.isEmpty()) return;
        System.out.println("\n  ID  | Nombre               | Vida | Ataque");
        System.out.println("------|----------------------|------|-------");
        for (Monstruo m : lista) {
            System.out.printf("  %-4d| %-20s | %-4d | %d%n",
                    m.getId(), m.getNombre(), m.getVida(), m.getAtaque());
        }
    }

    private void buscarPorNombre() {
        System.out.print("Termino de busqueda: ");
        String termino = scanner.nextLine().trim();
        List<Monstruo> resultados = monstruoController.buscarPorNombre(termino);
        if (resultados.isEmpty()) {
            System.out.println("No se encontraron monstruos con ese nombre.");
            return;
        }
        for (Monstruo m : resultados) {
            System.out.printf("  [%d] %s - Vida: %d, Ataque: %d%n",
                    m.getId(), m.getNombre(), m.getVida(), m.getAtaque());
        }
    }

    private void filtrarPorVida() {
        System.out.print("Vida minima: ");
        int vidaMin = leerEntero();
        List<Monstruo> resultados = monstruoController.listarPorVidaMinima(vidaMin);
        if (resultados.isEmpty()) {
            System.out.println("No hay monstruos con vida >= " + vidaMin + ".");
            return;
        }
        for (Monstruo m : resultados) {
            System.out.printf("  [%d] %-20s Vida: %d, Ataque: %d%n",
                    m.getId(), m.getNombre(), m.getVida(), m.getAtaque());
        }
    }

    // ---------------------------------------------------------------- opciones de administracion

    private void agregarMonstruo() {
        System.out.println("-- Nuevo Monstruo --");
        System.out.print("Nombre: ");
        String nombre = scanner.nextLine().trim();
        System.out.print("Vida: ");
        int vida = leerEntero();
        System.out.print("Ataque: ");
        int ataque = leerEntero();
        monstruoController.crear(nombre, vida, ataque);
    }

    private void editarMonstruo() {
        System.out.println("-- Editar Monstruo --");
        mostrarTodos();
        System.out.print("ID del monstruo a editar: ");
        int id = leerEntero();
        Monstruo existente = monstruoController.obtenerPorId(id);
        if (existente == null) return;
        System.out.println("Monstruo actual: " + existente);
        System.out.print("Nuevo nombre (" + existente.getNombre() + "): ");
        String nombre = scanner.nextLine().trim();
        if (nombre.isEmpty()) nombre = existente.getNombre();
        System.out.print("Nueva vida (" + existente.getVida() + "): ");
        String vidaStr = scanner.nextLine().trim();
        int vida = vidaStr.isEmpty() ? existente.getVida() : Integer.parseInt(vidaStr);
        System.out.print("Nuevo ataque (" + existente.getAtaque() + "): ");
        String ataqueStr = scanner.nextLine().trim();
        int ataque = ataqueStr.isEmpty() ? existente.getAtaque() : Integer.parseInt(ataqueStr);
        monstruoController.actualizar(id, nombre, vida, ataque);
    }

    private void eliminarMonstruo() {
        System.out.println("-- Eliminar Monstruo --");
        mostrarTodos();
        System.out.print("ID del monstruo a eliminar: ");
        int id = leerEntero();
        System.out.print("Confirmar eliminacion de monstruo id=" + id + " (s/n): ");
        String conf = scanner.nextLine().trim();
        if ("s".equalsIgnoreCase(conf) || "si".equalsIgnoreCase(conf)) {
            monstruoController.eliminar(id);
        } else {
            System.out.println("Eliminacion cancelada.");
        }
    }

    // ---------------------------------------------------------------- utilidad

    private int leerEntero() {
        while (true) {
            String linea = scanner.nextLine().trim();
            try {
                return Integer.parseInt(linea);
            } catch (NumberFormatException e) {
                System.out.print("Valor no valido. Ingresa un numero entero: ");
            }
        }
    }
}
