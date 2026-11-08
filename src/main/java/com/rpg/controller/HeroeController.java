package com.rpg.controller;

import com.rpg.core.Consola;
import com.rpg.core.MotorJuego;
import com.rpg.dao.HeroeDAO;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;
import com.rpg.model.Heroe;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/** Validates hero operations and coordinates the view, DAO, and game domain. */
public class HeroeController {

    private static final String MENSAJE_NO_EXISTE = "no existe un heroe con ese id";

    /**
     * Funcion lambda para transformar un modelo Heroe de la base de datos a su respectiva entidad Personaje.
     */
    public static final Function<Heroe, Personaje> TRANSFORMADOR_HEROE = heroe -> {
        Objects.requireNonNull(heroe, "heroe");
        Personaje personaje = switch (heroe.getTipo()) {
            case "guerrero" -> new Guerrero(heroe.getNombre());
            case "mago" -> new Mago(heroe.getNombre());
            default -> throw new IllegalArgumentException("Tipo de heroe desconocido.");
        };
        personaje.setVidaMaxima(heroe.getVidaMaxima());
        personaje.restaurarVida();
        personaje.setNivel(heroe.getNivel());
        return personaje;
    };

    private final HeroeDAO heroeDAO;
    private final Consola consola;
    private MotorJuego motorJuego;

    public HeroeController() {
        this(new HeroeDAO(), null);
    }

    public HeroeController(HeroeDAO heroeDAO) {
        this(heroeDAO, null);
    }

    public HeroeController(Consola consola) {
        this(new HeroeDAO(), Objects.requireNonNull(consola, "consola"));
    }

    private HeroeController(HeroeDAO heroeDAO, Consola consola) {
        this.heroeDAO = Objects.requireNonNull(heroeDAO, "heroeDAO");
        this.consola = consola;
    }

    public boolean insertar(String nombre, String tipo, int vidaMaxima, int nivel) {
        if (nombre == null || nombre.trim().isEmpty()) {
            System.out.println("El nombre no puede estar vacio.");
            return false;
        }
        if (!tipoValido(tipo)) {
            System.out.println("Tipo invalido. Use guerrero o mago.");
            return false;
        }
        if (vidaMaxima <= 0) {
            System.out.println("La vida maxima debe ser mayor que cero.");
            return false;
        }
        if (nivel < 1) {
            System.out.println("El nivel debe ser al menos 1.");
            return false;
        }

        try {
            Heroe heroe = new Heroe(nombre, tipo, vidaMaxima, nivel);
            if (!heroeDAO.insertar(heroe)) {
                System.out.println("No se pudo guardar el heroe.");
                return false;
            }
            System.out.printf("Heroe guardado con id %d.%n", heroe.getId());
            return true;
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public List<Heroe> listar() {
        List<Heroe> heroes = heroeDAO.listar();
        if (heroes.isEmpty()) {
            System.out.println("No hay heroes registrados.");
        }
        return heroes;
    }

    public List<Heroe> buscarPorTipo(String tipo) {
        if (!tipoValido(tipo)) {
            System.out.println("Tipo invalido. Use guerrero o mago.");
            return List.of();
        }
        List<Heroe> heroes = heroeDAO.buscarPorTipo(tipo.trim().toLowerCase(Locale.ROOT));
        if (heroes.isEmpty()) {
            System.out.println("No hay heroes de ese tipo.");
        }
        return heroes;
    }

    public Heroe buscarPorId(int id) {
        if (id < 1) {
            System.out.println("El id debe ser mayor que cero.");
            return null;
        }
        Heroe heroe = heroeDAO.buscarPorId(id);
        if (heroe == null) {
            System.out.println(MENSAJE_NO_EXISTE);
        }
        return heroe;
    }

    public void cargarYCombatir(int id) {
        if (consola == null) {
            System.err.println("No se configuro la consola para iniciar el combate.");
            return;
        }
        Heroe heroe = buscarPorId(id);
        if (heroe == null) {
            return;
        }
        if (motorJuego == null) {
            motorJuego = new MotorJuego(consola);
        }
        motorJuego.combateRapidoCon(convertirAEntity(heroe));
    }

    public int actualizarNivel(int id, int nivel) {
        if (id < 1) {
            System.out.println("El id debe ser mayor que cero.");
            return HeroeDAO.ERROR_BD;
        }
        if (nivel < 1) {
            System.out.println("El nivel debe ser al menos 1.");
            return HeroeDAO.ERROR_BD;
        }
        int filas = heroeDAO.actualizarNivel(id, nivel);
        if (filas == 0) {
            System.out.println(MENSAJE_NO_EXISTE);
        } else if (filas > 0) {
            System.out.println("Nivel actualizado.");
        } else {
            System.out.println("No se pudo actualizar el nivel.");
        }
        return filas;
    }

    public int eliminar(int id) {
        if (id < 1) {
            System.out.println("El id debe ser mayor que cero.");
            return HeroeDAO.ERROR_BD;
        }
        int filas = heroeDAO.eliminar(id);
        if (filas == 0) {
            System.out.println(MENSAJE_NO_EXISTE);
        } else if (filas > 0) {
            System.out.println("Heroe eliminado.");
        } else {
            System.out.println("No se pudo eliminar el heroe.");
        }
        return filas;
    }

    /**
     * Converts a stored row to its combat class and copies health and level.
     * Personaje.calcularDanio includes level, so the stored level affects combat.
     * Implementado mediante la funcion lambda {@link #TRANSFORMADOR_HEROE}.
     */
    public Personaje convertirAEntity(Heroe heroe) {
        return convertirConFuncion(heroe, TRANSFORMADOR_HEROE);
    }

    /**
     * Convierte un modelo {@link Heroe} aplicando una funcion de mapeo {@link Function}.
     *
     * @param heroe Modelo a transformar
     * @param mapper Funcion de transformacion
     * @return Entidad Personaje resultante
     */
    public Personaje convertirConFuncion(Heroe heroe, Function<Heroe, Personaje> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return mapper.apply(heroe);
    }

    private static boolean tipoValido(String tipo) {
        if (tipo == null) {
            return false;
        }
        String normalizado = tipo.trim().toLowerCase(Locale.ROOT);
        return normalizado.equals("guerrero") || normalizado.equals("mago");
    }
}
