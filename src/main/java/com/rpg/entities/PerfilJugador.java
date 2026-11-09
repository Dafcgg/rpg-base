package com.rpg.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Perfil estadistico del jugador: victorias, derrotas y logros desbloqueados.
 *
 * <p>Implementa {@link Serializable} para permitir la persistencia binaria de la partida
 * mediante {@link com.rpg.persistencia.GestorPartidas}.</p>
 */
// TALLER ARCHIVOS: implementa Serializable (victorias, derrotas y logros son parte de la partida).
public class PerfilJugador implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int victorias;
    private int derrotas;
    private final TreeSet<String> logros = new TreeSet<>();

    public synchronized void registrarVictoria() {
        if (victorias < Integer.MAX_VALUE) victorias++; // FIX: impedir estadisticas negativas por overflow.
    }

    public synchronized void registrarDerrota() {
        if (derrotas < Integer.MAX_VALUE) derrotas++;
    }

    /** @return true si el logro era nuevo; false si ya estaba desbloqueado */
    public synchronized boolean desbloquearLogro(String logro) {
        if (logro == null || logro.isBlank()) throw new IllegalArgumentException("Logro vacio"); // FIX
        return logros.add(logro);
    }

    public synchronized int getVictorias() {
        return victorias;
    }

    public synchronized int getDerrotas() {
        return derrotas;
    }

    /** Los logros salen ordenados alfabeticamente porque se guardan en un TreeSet. */
    public synchronized Set<String> getLogros() {
        return Collections.unmodifiableSet(new TreeSet<>(logros));
    }

    @Serial
    private synchronized void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        out.defaultWriteObject();
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        // FIX: aceptar clases permitidas no basta: tambien hay que validar sus invariantes.
        if (victorias < 0 || derrotas < 0 || logros == null) {
            throw new java.io.InvalidObjectException("Perfil guardado invalido");
        }
        for (Object logro : logros) {
            if (!(logro instanceof String texto) || texto.isBlank()) {
                throw new java.io.InvalidObjectException("Logro guardado invalido");
            }
        }
    }
}
