package com.rpg.core;

import java.util.OptionalInt;
import java.util.Scanner;

/** Entrada por teclado con validacion y reintento. */
public class Consola {

    private static final int LARGO_MAXIMO_NOMBRE = 15;
    private static final String PATRON_NOMBRE = "[A-Za-z0-9 ]+";

    private final Scanner scanner;

    /** Canal con lectura limitada por tiempo; {@code null} si el Scanner lee directo de System.in. */
    private final EntradaConsola entrada;

    /** Se pone en true cuando el temporizador de turno avisa que el tiempo se agoto. */
    private volatile boolean tiempoExpirado;

    public Consola(Scanner scanner) {
        this(scanner, null);
    }

    /**
     * @param scanner scanner construido sobre {@code entrada}
     * @param entrada flujo que permite abandonar la espera por tiempo (puede ser null)
     */
    public Consola(Scanner scanner, EntradaConsola entrada) {
        this.scanner = scanner;
        this.entrada = entrada;
    }

    /**
     * Llamado por el temporizador de turno ({@code TurnoCallback}) cuando se agota el tiempo.
     * Despierta al hilo principal que espera en {@link #leerEnteroConLimite}.
     */
    public void expirarEspera() {
        tiempoExpirado = true;
        if (entrada != null) {
            entrada.expirar();
        }
    }

    /**
     * Igual que {@link #leerEntero} pero la espera termina si se llama a {@link #expirarEspera()}.
     * El usuario no tiene que pulsar Enter para que el juego continue.
     *
     * @return el numero elegido, o vacio si el tiempo se agoto (o el hilo fue interrumpido)
     */
    public OptionalInt leerEnteroConLimite(String mensaje, int minimo, int maximo) {
        tiempoExpirado = false;
        if (entrada != null) {
            entrada.armar();
        }
        try {
            while (true) {
                System.out.print(mensaje);
                if (entrada != null) {
                    try {
                        if (!entrada.esperarLinea()) {
                            System.out.println();
                            return OptionalInt.empty();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return OptionalInt.empty();
                    }
                }
                String texto = scanner.nextLine().trim();
                if (entrada == null && tiempoExpirado) {
                    return OptionalInt.empty(); // sin canal especial: la respuesta tardia se descarta
                }
                if (texto.isEmpty()) {
                    System.out.println("No escribiste nada.");
                    continue;
                }
                try {
                    int valor = Integer.parseInt(texto);
                    if (valor >= minimo && valor <= maximo) {
                        return OptionalInt.of(valor);
                    }
                    System.out.printf("Numero fuera de rango (%d a %d).%n", minimo, maximo);
                } catch (NumberFormatException e) {
                    System.out.println("Entrada no valida. Ingresa un numero entero.");
                }
            }
        } finally {
            if (entrada != null) {
                entrada.desarmar();
            }
        }
    }

    public String leerLinea(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().trim();
    }

    public int leerEntero(String mensaje, int minimo, int maximo) {
        while (true) {
            String texto = leerLinea(mensaje);
            if (texto.isEmpty()) {
                System.out.println("No escribiste nada.");
                continue;
            }
            try {
                int valor = Integer.parseInt(texto);
                if (valor >= minimo && valor <= maximo) {
                    return valor;
                }
                System.out.printf("Numero fuera de rango (%d a %d).%n", minimo, maximo);
            } catch (NumberFormatException e) {
                System.out.println("Entrada no valida. Ingresa un numero entero.");
            }
        }
    }

    public String leerNombre(String mensaje) {
        while (true) {
            String nombre = leerLinea(mensaje);
            if (nombre.isEmpty()) {
                System.out.println("El nombre no puede estar vacio.");
            } else if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
                System.out.println("Maximo " + LARGO_MAXIMO_NOMBRE + " caracteres.");
            } else if (!nombre.matches(PATRON_NOMBRE)) {
                System.out.println("Usa solo letras ASCII, numeros y espacios.");
            } else {
                return nombre;
            }
        }
    }
}
