package com.rpg.dao;

import com.rpg.model.Heroe;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Parameterized JDBC access. Errors propagate separately from absent records. */
public class HeroeDAO {
    public static final int ERROR_BD = -1;
    private static final int TIEMPO_CONSULTA_SEGUNDOS = 10;
    private static final int TAMANO_LOTE_IDS = 1000;
    private static final String COLUMNAS = "id, nombre, tipo, vida_maxima, nivel";
    private static final String INSERTAR =
            "INSERT INTO heroes (nombre, tipo, vida_maxima, nivel) VALUES (?, ?, ?, ?)";
    private static final String LISTAR = "SELECT " + COLUMNAS + " FROM heroes ORDER BY id";
    private static final String BUSCAR_TIPO =
            "SELECT " + COLUMNAS + " FROM heroes WHERE tipo = ? ORDER BY id";
    private static final String BUSCAR_ID = "SELECT " + COLUMNAS + " FROM heroes WHERE id = ?";

    @FunctionalInterface
    public interface ProveedorConexion {
        Connection abrir() throws SQLException;
    }

    private final ProveedorConexion conexiones;

    public HeroeDAO() { this(ConexionDB::obtenerConexionGrupal); }

    /** FIX: Injectable connections allow isolated JDBC failure/resource tests. */
    public HeroeDAO(ProveedorConexion conexiones) {
        this.conexiones = Objects.requireNonNull(conexiones, "conexiones");
    }

    public boolean insertar(Heroe heroe) {
        Objects.requireNonNull(heroe, "heroe");
        if (heroe.getId() != 0) {
            throw new IllegalArgumentException("El heroe ya tiene un id persistido.");
        }
        try (Connection conexion = abrirConexion()) {
            // FIX: Missing keys must not leave an auto-committed row behind.
            conexion.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement sentencia = conexion.prepareStatement(INSERTAR, Statement.RETURN_GENERATED_KEYS)) {
                    sentencia.setQueryTimeout(TIEMPO_CONSULTA_SEGUNDOS);
                    sentencia.setString(1, heroe.getNombre());
                    sentencia.setString(2, heroe.getTipo());
                    sentencia.setInt(3, heroe.getVidaMaxima());
                    sentencia.setInt(4, heroe.getNivel());
                    if (sentencia.executeUpdate() != 1) {
                        throw new SQLException("La insercion no afecto exactamente una fila.");
                    }
                    try (ResultSet claves = sentencia.getGeneratedKeys()) {
                        if (!claves.next() || (id = claves.getInt(1)) <= 0) {
                            throw new SQLException("La insercion no devolvio un id valido.");
                        }
                    }
                }
                conexion.commit();
                heroe.setId(id);
                return true;
            } catch (SQLException | RuntimeException e) {
                try {
                    conexion.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw PersistenciaException.operacion("insertar heroe", e);
        }
    }

    public List<Heroe> listar() {
        try (Connection conexion = abrirConexion();
             PreparedStatement sentencia = preparar(conexion, LISTAR);
             ResultSet resultados = sentencia.executeQuery()) {
            return leerTodos(resultados);
        } catch (SQLException e) {
            throw PersistenciaException.operacion("listar heroes", e);
        }
    }

    public List<Heroe> buscarPorTipo(String tipo) {
        Objects.requireNonNull(tipo, "tipo");
        try (Connection conexion = abrirConexion();
             PreparedStatement sentencia = preparar(conexion, BUSCAR_TIPO)) {
            sentencia.setString(1, tipo);
            try (ResultSet resultados = sentencia.executeQuery()) {
                return leerTodos(resultados);
            }
        } catch (SQLException e) {
            throw PersistenciaException.operacion("buscar heroes por tipo", e);
        }
    }

    public Heroe buscarPorId(int id) {
        validarId(id);
        try (Connection conexion = abrirConexion();
             PreparedStatement sentencia = preparar(conexion, BUSCAR_ID)) {
            sentencia.setInt(1, id);
            try (ResultSet resultados = sentencia.executeQuery()) {
                return resultados.next() ? mapear(resultados) : null;
            }
        } catch (SQLException e) {
            throw PersistenciaException.operacion("buscar heroe por id", e);
        }
    }

    /** FIX: Bounded batches avoid both N+1 and unbounded IN parameter lists. */
    public List<Heroe> buscarPorVariosIds(int[] ids) {
        if (ids == null || ids.length == 0) {
            return List.of();
        }
        int[] validos = Arrays.stream(ids).filter(id -> id > 0).distinct().sorted().toArray();
        if (validos.length == 0) {
            return List.of();
        }
        List<Heroe> heroes = new ArrayList<>();
        try (Connection conexion = abrirConexion()) {
            for (int inicio = 0; inicio < validos.length; inicio += TAMANO_LOTE_IDS) {
                int cantidad = Math.min(TAMANO_LOTE_IDS, validos.length - inicio);
                String parametros = String.join(",", Collections.nCopies(cantidad, "?"));
                // Only generated placeholders are concatenated; IDs remain bound parameters.
                String sql = "SELECT " + COLUMNAS + " FROM heroes WHERE id IN (" + parametros + ") ORDER BY id";
                try (PreparedStatement sentencia = preparar(conexion, sql)) {
                    for (int i = 0; i < cantidad; i++) {
                        sentencia.setInt(i + 1, validos[inicio + i]);
                    }
                    try (ResultSet resultados = sentencia.executeQuery()) {
                        heroes.addAll(leerTodos(resultados));
                    }
                }
            }
            return List.copyOf(heroes);
        } catch (SQLException e) {
            // FIX: A failed later batch must not return a misleading partial result.
            throw PersistenciaException.operacion("buscar heroes por varios ids", e);
        }
    }

    public int actualizarNivel(int id, int nivel) {
        validarId(id);
        if (nivel < 1) {
            throw new IllegalArgumentException("El nivel debe ser al menos 1.");
        }
        try (Connection conexion = abrirConexion();
             PreparedStatement sentencia = preparar(conexion, "UPDATE heroes SET nivel = ? WHERE id = ?")) {
            sentencia.setInt(1, nivel);
            sentencia.setInt(2, id);
            int filas = sentencia.executeUpdate();
            if (filas != 0) {
                return filas;
            }
            // FIX: MySQL useAffectedRows=true also returns zero for an unchanged level.
            try (PreparedStatement existe = preparar(conexion, "SELECT id FROM heroes WHERE id = ?")) {
                existe.setInt(1, id);
                try (ResultSet resultado = existe.executeQuery()) {
                    return resultado.next() ? 1 : 0;
                }
            }
        } catch (SQLException e) {
            throw PersistenciaException.operacion("actualizar nivel", e);
        }
    }

    public int eliminar(int id) {
        validarId(id);
        try (Connection conexion = abrirConexion();
             PreparedStatement sentencia = preparar(conexion, "DELETE FROM heroes WHERE id = ?")) {
            sentencia.setInt(1, id);
            return sentencia.executeUpdate();
        } catch (SQLException e) {
            throw PersistenciaException.operacion("eliminar heroe", e);
        }
    }

    private Connection abrirConexion() throws SQLException {
        Connection conexion = conexiones.abrir();
        if (conexion == null) {
            throw new SQLException("El proveedor no devolvio una conexion.", "08001");
        }
        return conexion;
    }

    private static PreparedStatement preparar(Connection conexion, String sql) throws SQLException {
        PreparedStatement sentencia = conexion.prepareStatement(sql);
        try {
            sentencia.setQueryTimeout(TIEMPO_CONSULTA_SEGUNDOS);
            return sentencia;
        } catch (SQLException | RuntimeException e) {
            // FIX: Initialization can fail before the caller registers this resource.
            try {
                sentencia.close();
            } catch (SQLException cierre) {
                e.addSuppressed(cierre);
            }
            throw e;
        }
    }

    private static List<Heroe> leerTodos(ResultSet resultados) throws SQLException {
        List<Heroe> heroes = new ArrayList<>();
        while (resultados.next()) {
            heroes.add(mapear(resultados));
        }
        return List.copyOf(heroes);
    }

    private static Heroe mapear(ResultSet resultado) throws SQLException {
        try {
            int id = resultado.getInt("id");
            validarId(id);
            return new Heroe(id, resultado.getString("nombre"), resultado.getString("tipo"),
                    resultado.getInt("vida_maxima"), resultado.getInt("nivel"));
        } catch (IllegalArgumentException e) {
            // FIX: External database clients can store rows invalid for the Java domain.
            throw new SQLException("La fila contiene datos de heroe invalidos.", "22000", e);
        }
    }

    private static void validarId(int id) {
        if (id < 1) {
            throw new IllegalArgumentException("El id debe ser mayor que cero.");
        }
    }
}