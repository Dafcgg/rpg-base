package com.rpg.controller;

import com.rpg.dao.HeroeDAO;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.Personaje;
import com.rpg.model.Heroe;

import java.util.Collections;
import java.util.List;

/**
 * Controller del modulo de heroes: valida datos, coordina al DAO y decide los mensajes.
 * No importa java.sql: aisla a la View de la capa de datos.
 */
public class HeroeController {

    private static final String MENSAJE_NO_EXISTE = "no existe un heroe con ese id";

    private final HeroeDAO heroeDAO;

    public HeroeController() {
        this(new HeroeDAO());
    }

    public HeroeController(HeroeDAO heroeDAO) {
        this.heroeDAO = heroeDAO;
    }

    public boolean insertar(String nombre, String tipo, int vidaMaxima, int nivel) {
        if (nombre == null || nombre.isBlank()) {
            System.out.println("[HeroeController] El nombre no puede estar vacio.");
            return false;
        }
        if (!Heroe.esTipoValido(tipo)) {
            System.out.println("[HeroeController] Tipo invalido. Use guerrero o mago.");
            return false;
        }
        if (vidaMaxima <= 0) {
            System.out.println("[HeroeController] La vida maxima debe ser mayor que 0.");
            return false;
        }
        if (nivel < 1) {
            System.out.println("[HeroeController] El nivel debe ser mayor o igual a 1.");
            return false;
        }
        Heroe heroe = new Heroe(nombre, tipo, vidaMaxima, nivel);
        boolean ok = heroeDAO.insertar(heroe);
        if (ok) {
            System.out.println("[HeroeController] Heroe '" + heroe.getNombre() + "' creado con id=" + heroe.getId() + ".");
        } else {
            System.out.println("[HeroeController] No se pudo guardar el heroe '" + heroe.getNombre() + "'.");
        }
        return ok;
    }

    public List<Heroe> listar() {
        List<Heroe> heroes = heroeDAO.listar();
        if (heroes.isEmpty()) {
            System.out.println("[HeroeController] No hay heroes registrados.");
        }
        return heroes;
    }

    public List<Heroe> buscarPorTipo(String tipo) {
        if (!Heroe.esTipoValido(tipo)) {
            System.out.println("[HeroeController] Tipo invalido. Use guerrero o mago.");
            return Collections.emptyList();
        }
        List<Heroe> heroes = heroeDAO.buscarPorTipo(tipo.trim().toLowerCase());
        if (heroes.isEmpty()) {
            System.out.println("[HeroeController] No hay heroes de tipo " + tipo.trim().toLowerCase() + ".");
        }
        return heroes;
    }

    public int actualizarNivel(int id, int nivel) {
        if (nivel < 1) {
            System.out.println("[HeroeController] El nivel debe ser mayor o igual a 1.");
            return 0;
        }
        int filas = heroeDAO.actualizarNivel(id, nivel);
        informarFilas(filas, "Nivel actualizado para el heroe id=" + id + ".");
        return filas;
    }

    public int eliminar(int id) {
        int filas = heroeDAO.eliminar(id);
        informarFilas(filas, "Heroe id=" + id + " eliminado.");
        return filas;
    }

    /**
     * Carga una fila de la tabla heroes e instancia dinamicamente un Guerrero o Mago.
     *
     * @return el personaje listo para combatir, o null si no existe o el tipo es invalido
     */
    public Personaje crearPersonaje(int id) {
        Heroe heroe = heroeDAO.obtenerPorId(id);
        if (heroe == null) {
            System.out.println("[HeroeController] " + MENSAJE_NO_EXISTE + ".");
            return null;
        }
        Personaje personaje;
        if (Heroe.TIPO_GUERRERO.equals(heroe.getTipo())) {
            personaje = new Guerrero(heroe.getNombre());
        } else if (Heroe.TIPO_MAGO.equals(heroe.getTipo())) {
            personaje = new Mago(heroe.getNombre());
        } else {
            System.out.println("[HeroeController] Tipo de heroe desconocido: " + heroe.getTipo());
            return null;
        }
        personaje.setVidaMaxima(heroe.getVidaMaxima());
        personaje.setNivel(heroe.getNivel());
        personaje.restaurarVida();
        System.out.printf("[HeroeController] Heroe cargado: %s (%s, nivel %d, vida %d).%n",
                heroe.getNombre(), heroe.getTipo(), heroe.getNivel(), heroe.getVidaMaxima());
        return personaje;
    }

    private void informarFilas(int filas, String mensajeExito) {
        if (filas == HeroeDAO.ERROR_BD) {
            System.out.println("[HeroeController] No se pudo completar la operacion en la base de datos.");
        } else if (filas == 0) {
            System.out.println("[HeroeController] " + MENSAJE_NO_EXISTE + ".");
        } else {
            System.out.println("[HeroeController] " + mensajeExito);
        }
    }
}
