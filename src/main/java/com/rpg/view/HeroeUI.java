package com.rpg.view;

import com.rpg.controller.HeroeController;
import com.rpg.core.Consola;
import com.rpg.core.EntradaConsola;
import com.rpg.model.Heroe;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Console view for managing persistent heroes. */
public class HeroeUI {

    private final Scanner scanner;
    private final HeroeController controller;

    public HeroeUI() {
        EntradaConsola entrada = new EntradaConsola(System.in);
        scanner = new Scanner(entrada);
        controller = new HeroeController(new Consola(scanner, entrada));
    }

    public void iniciar() {
        try {
            ejecutarMenu();
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada finalizada. Hasta luego.");
        } finally {
            try {
                // FIX: Release background game resources on exit and end of input.
                controller.close();
            } finally {
                scanner.close();
            }
        }
    }

    private void ejecutarMenu() {
        int opcion;
        do {
            mostrarMenu();
            opcion = leerEntero("Opcion: ", 0, 6);
            switch (opcion) {
                case 1 -> insertar();
                case 2 -> mostrarHeroes();
                case 3 -> buscarPorTipo();
                case 4 -> actualizarNivel();
                case 5 -> eliminar();
                case 6 -> cargarYCombatir();
                case 0 -> System.out.println("Hasta luego.");
                default -> System.out.println("Opcion no reconocida.");
            }
        } while (opcion != 0);
    }

    private void mostrarMenu() {
        System.out.println("\n=== REGISTRO DE HEROES ===");
        System.out.println("1. Insertar heroe");
        System.out.println("2. Listar heroes");
        System.out.println("3. Buscar por tipo");
        System.out.println("4. Actualizar nivel");
        System.out.println("5. Eliminar heroe");
        System.out.println("6. Cargar heroe desde BD y combatir");
        System.out.println("0. Salir");
    }

    private void insertar() {
        System.out.println("\n--- Insertar heroe ---");
        String nombre = leerLinea("Nombre: ");
        String tipo = leerLinea("Tipo (guerrero/mago): ");
        int vidaMaxima = leerEntero("Vida maxima: ", 1, Integer.MAX_VALUE);
        int nivel = leerEntero("Nivel: ", 1, Integer.MAX_VALUE);
        controller.insertar(nombre, tipo, vidaMaxima, nivel);
    }

    private void mostrarHeroes() {
        List<Heroe> heroes = controller.listar();
        imprimirHeroes(heroes);
    }

    private void buscarPorTipo() {
        String tipo = leerLinea("Tipo (guerrero/mago): ");
        imprimirHeroes(controller.buscarPorTipo(tipo));
    }

    private void actualizarNivel() {
        int id = leerEntero("Id del heroe: ", 1, Integer.MAX_VALUE);
        int nivel = leerEntero("Nuevo nivel: ", 1, Integer.MAX_VALUE);
        controller.actualizarNivel(id, nivel);
    }

    private void eliminar() {
        int id = leerEntero("Id del heroe a eliminar: ", 1, Integer.MAX_VALUE);
        String confirmacion = leerLinea("Confirmar eliminacion (s/n): ");
        if ("s".equalsIgnoreCase(confirmacion)) {
            controller.eliminar(id);
        } else {
            System.out.println("Operacion cancelada.");
        }
    }

    private void cargarYCombatir() {
        List<Heroe> heroes = controller.listar();
        imprimirHeroes(heroes);
        if (!heroes.isEmpty()) {
            int id = leerEntero("Id del heroe para combatir: ", 1, Integer.MAX_VALUE);
            controller.cargarYCombatir(id);
        }
    }

    private static void imprimirHeroes(List<Heroe> heroes) {
        if (heroes.isEmpty()) {
            return;
        }
        System.out.println(" ID | Nombre | Tipo | Vida maxima | Nivel");
        for (Heroe heroe : heroes) {
            System.out.printf("%3d | %s | %s | %d | %d%n",
                    heroe.getId(), heroe.getNombre(), heroe.getTipo(),
                    heroe.getVidaMaxima(), heroe.getNivel());
        }
    }

    private int leerEntero(String mensaje, int minimo, int maximo) {
        while (true) {
            String texto = leerLinea(mensaje);
            try {
                int valor = Integer.parseInt(texto);
                if (valor >= minimo && valor <= maximo) {
                    return valor;
                }
            } catch (NumberFormatException e) {
                System.out.println("Ingresa un numero entero valido.");
                continue;
            }
            System.out.printf("Ingresa un numero entre %d y %d.%n", minimo, maximo);
        }
    }

    private String leerLinea(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().trim();
    }
}
