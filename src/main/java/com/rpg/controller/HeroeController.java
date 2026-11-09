package com.rpg.controller;

import com.rpg.core.Consola;
import com.rpg.core.MotorJuego;
import com.rpg.dao.HeroeDAO;
import com.rpg.dao.PersistenciaException;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;
import com.rpg.model.Heroe;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Validates hero operations and coordinates the view, DAO, and game domain. */
public class HeroeController implements AutoCloseable {

    private static final String MENSAJE_NO_EXISTE = "no existe un heroe con ese id";

    /**
     * Conjunto de tipos permitidos validado usando Stream.of() y anyMatch().
     */
    public static final List<String> TIPOS_VALIDOS = Stream.of("guerrero", "mago")
            .collect(Collectors.toUnmodifiableList());

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
        } catch (PersistenciaException e) {
            informarError(e);
            return false;
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public List<Heroe> listar() {
        try {
            List<Heroe> heroes = heroeDAO.listar();
            if (heroes.isEmpty()) {
                System.out.println("No hay heroes registrados.");
            }
            return heroes;
        } catch (PersistenciaException e) {
            // FIX: Failed queries must not announce that the database is empty.
            informarError(e);
            return List.of();
        }
    }

    public List<Heroe> buscarPorTipo(String tipo) {
        if (!tipoValido(tipo)) {
            System.out.println("Tipo invalido. Use guerrero o mago.");
            return List.of();
        }
        try {
            List<Heroe> heroes = heroeDAO.buscarPorTipo(tipo.trim().toLowerCase(Locale.ROOT));
            if (heroes.isEmpty()) {
                System.out.println("No hay heroes de ese tipo.");
            }
            return heroes;
        } catch (PersistenciaException e) {
            informarError(e);
            return List.of();
        }
    }

    public Heroe buscarPorId(int id) {
        if (id < 1) {
            System.out.println("El id debe ser mayor que cero.");
            return null;
        }
        try {
            Heroe heroe = heroeDAO.buscarPorId(id);
            if (heroe == null) {
                System.out.println(MENSAJE_NO_EXISTE);
            }
            return heroe;
        } catch (PersistenciaException e) {
            informarError(e);
            return null;
        }
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
        int filas;
        try {
            filas = heroeDAO.actualizarNivel(id, nivel);
        } catch (PersistenciaException e) {
            informarError(e);
            return HeroeDAO.ERROR_BD;
        }
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
        int filas;
        try {
            filas = heroeDAO.eliminar(id);
        } catch (PersistenciaException e) {
            informarError(e);
            return HeroeDAO.ERROR_BD;
        }
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

    /**
     * Busca multiples heroes a partir de un arreglo nativo de identificadores usando Arrays.stream().
     * Delega consultas por lotes al DAO y procesa el resultado sin una consulta por cada ID.
     */
    public List<Heroe> buscarPorIds(int[] ids) {
        if (ids == null || ids.length == 0) {
            return List.of();
        }
        int[] idsValidos = Arrays.stream(ids)
                .filter(id -> id > 0)
                .distinct()
                .toArray();
        if (idsValidos.length == 0) {
            return List.of();
        }
        try {
            List<Heroe> obtenidos = heroeDAO.buscarPorVariosIds(idsValidos);
            return obtenidos.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableList());
        } catch (PersistenciaException e) {
            informarError(e);
            return List.of();
        }
    }

    /**
     * Transforma una lista de modelos Heroe a entidades de combate Personaje usando lista.stream() y map().
     */
    public List<Personaje> convertirListaAEntities(List<Heroe> heroes) {
        if (heroes == null || heroes.isEmpty()) {
            return List.of();
        }
        return heroes.stream()
                .filter(Objects::nonNull)
                .map(this::convertirAEntity)
                .collect(Collectors.toList());
    }

    private static boolean tipoValido(String tipo) {
        if (tipo == null) {
            return false;
        }
        String normalizado = tipo.trim().toLowerCase(Locale.ROOT);
        return TIPOS_VALIDOS.stream().anyMatch(normalizado::equals);
    }

    private static void informarError(PersistenciaException e) {
        // FIX: Expose actionable status without leaking JDBC URLs, SQL text or credentials.
        switch (e.getMotivo()) {
            case DUPLICADO -> System.out.println("Ya existe un heroe con ese nombre.");
            case CONFIGURACION -> System.out.println("Configuracion de MySQL invalida. Revisa config.txt.");
            case CONEXION -> System.out.println("MySQL no esta disponible. Reintenta cuando se restablezca la conexion.");
            case OPERACION -> System.out.println("Fallo la operacion de BD. Verifica los datos antes de reintentar.");
        }
    }

    @Override
    public void close() {
        // FIX: Stop the game autosave worker when the hero registry exits.
        if (motorJuego != null) {
            motorJuego.close();
        }
    }
}
