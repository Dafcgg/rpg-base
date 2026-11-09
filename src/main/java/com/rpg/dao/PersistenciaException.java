package com.rpg.dao;

import java.sql.SQLException;

/** Persistence failures differ from successful queries without results. */
public final class PersistenciaException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public enum Motivo { CONFIGURACION, CONEXION, DUPLICADO, OPERACION }
    private final Motivo motivo;

    public PersistenciaException(Motivo motivo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.motivo = motivo;
    }

    public Motivo getMotivo() { return motivo; }

    static PersistenciaException operacion(String operacion, SQLException causa) {
        // FIX: Other integrity violations must not be reported as duplicate names.
        boolean duplicado = causa.getErrorCode() == 1062 || "23505".equals(causa.getSQLState());
        return new PersistenciaException(duplicado ? Motivo.DUPLICADO : Motivo.OPERACION,
                "Error al " + operacion + ".", causa);
    }
}
