package com.rpg.view;

import com.rpg.controller.HeroeController;
import com.rpg.core.MotorJuego;
import com.rpg.entities.Personaje;
import com.rpg.model.Heroe;

import java.util.List;
import java.util.Scanner;

/**
 * Vista de consola del Registro de Heroes. Sin JDBC: solo Scanner y System.out.
 */
public class HeroeUI {

    private final Scanner scanner;
    private final HeroeController controller;
    private final MotorJuego motor;

    public HeroeUI(Scanner scanner, MotorJuego motor) {
        this.scanner = scanner;
        this.motor = motor;
        this.controller = new HeroeController();
    }

    public void iniciar() {
        int opcion;
        do {
            imprimirMenu();
            opcion = leerEntero("Opcion: ");
            switch (opcion) {
                case 1 -> insertar();
                case 2 -> imprimirHeroes(controller.listar());
                case 3 -> imprimirHeroes(controller.buscarPorTipo(leerTexto("Tipo (guerrero/mago): ")));
                case 4 -> controller.actualizarNivel(leerEntero("ID del heroe: "), leerEntero("Nuevo nivel: "));
                case 5 -> controller.eliminar(leerEntero("ID del heroe a eliminar: "));
                case 6 -> cargarYCombatir();
                case 0 -> System.out.println("Volviendo...");
                default -> System.out.println("Opcion no reconocida.");
            }
        } while (opcion != 0);
    }

    /** Opcion "Cargar heroe desde BD": instancia un Guerrero o Mago y lo usa en un combate rapido. */
    public void cargarYCombatir() {
        imprimirHeroes(controller.listar());
        int id = leerEntero("ID del heroe a cargar (0 = cancelar): ");
        if (id == 0) {
            return;
        }
        Personaje personaje = controller.crearPersonaje(id);
        if (personaje != null) {
            motor.combateRapidoCon(personaje);
        }
    }

    private void imprimirMenu() {
        System.out.println("\n=== REGISTRO DE HEROES (BD) ===");
        System.out.println("1. Insertar heroe");
        System.out.println("2. Listar heroes");
        System.out.println("3. Buscar por tipo");
        System.out.println("4. Actualizar nivel");
        System.out.println("5. Eliminar heroe");
        System.out.println("6. Cargar heroe desde BD y combatir");
        System.out.println("0. Volver");
    }

    private void insertar() {
        String nombre = leerTexto("Nombre: ");
        String tipo = leerTexto("Tipo (guerrero/mago): ");
        int vida = leerEntero("Vida maxima: ");
        int nivel = leerEntero("Nivel: ");
        controller.insertar(nombre, tipo, vida, nivel);
    }

    private void imprimirHeroes(List<Heroe> heroes) {
        for (Heroe h : heroes) {
            System.out.printf("  [%d] %-15s %-9s vida=%-4d nivel=%d%n",
                    h.getId(), h.getNombre(), h.getTipo(), h.getVidaMaxima(), h.getNivel());
        }
    }

    private String leerTexto(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().trim();
    }

    private int leerEntero(String mensaje) {
        while (true) {
            String texto = leerTexto(mensaje);
            try {
                return Integer.parseInt(texto);
            } catch (NumberFormatException e) {
                System.out.println("\"" + texto + "\" no es un numero.");
            }
        }
    }
}
