package co.edu.eia.makerlab.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.persistencia.ConfiguracionBD;

/**
 * Crea las tablas la primera vez que la app arranca contra una base vacía
 * (por ejemplo, un proyecto nuevo de Neon), ejecutando sql/tablas.sql y
 * sql/relaciones.sql en ese orden y dentro de una transacción.
 *
 * Es seguro: solo actúa si NO existe ninguna de las 18 tablas. tablas.sql
 * empieza con DROP TABLE, así que jamás se ejecuta sobre un esquema que
 * ya tiene datos. crear_base.sql no se usa: en la nube la base ya existe.
 */
public final class EsquemaBD {

    private static final String[] TABLAS = { "laboratorio", "equipo", "impresora_3d", "cortadora_laser",
            "estacion_electronica", "usuario", "estudiante", "profesor", "tecnico", "notificacion", "certificacion",
            "reserva", "sesion_uso", "sesion_consumible", "sesion_incidente", "solicitud_espera",
            "ticket_mantenimiento", "ticket_cambio" };

    private EsquemaBD() {
    }

    /** Devuelve un texto que explica qué pasó. */
    public static String asegurar(ConfiguracionBD configuracion, Path carpetaSql)
            throws SQLException, MakerLabException {
        try (Connection conexion = DriverManager.getConnection(configuracion.getUrl(), configuracion.getUsuario(),
                configuracion.getClave())) {
            int existentes = contarTablas(conexion);
            if (existentes == TABLAS.length) {
                return "El esquema ya existe.";
            }
            if (existentes != 0) {
                throw new MakerLabException("La base tiene un esquema incompleto (" + existentes + " de "
                        + TABLAS.length + " tablas). No se modifica sola: ejecute sql/tablas.sql y "
                        + "sql/relaciones.sql a mano.");
            }
            String tablas = leer(carpetaSql.resolve("tablas.sql"));
            String relaciones = leer(carpetaSql.resolve("relaciones.sql"));
            conexion.setAutoCommit(false);
            try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute(tablas);
                sentencia.execute(relaciones);
                conexion.commit();
            } catch (SQLException e) {
                conexion.rollback();
                throw e;
            }
            return "Base vacía: se ejecutaron sql/tablas.sql y sql/relaciones.sql.";
        }
    }

    private static int contarTablas(Connection conexion) throws SQLException {
        StringBuilder lista = new StringBuilder();
        for (String t : TABLAS) {
            lista.append(lista.length() == 0 ? "'" : ",'").append(t).append("'");
        }
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() "
                + "AND table_name IN (" + lista + ")";
        try (Statement s = conexion.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static String leer(Path archivo) throws MakerLabException {
        try {
            return Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MakerLabException("La base está vacía y no se pudo leer " + archivo.toAbsolutePath()
                    + ". Ejecute los scripts de sql/ a mano (tablas.sql y luego relaciones.sql).");
        }
    }
}
