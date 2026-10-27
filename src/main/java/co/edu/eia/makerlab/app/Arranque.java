package co.edu.eia.makerlab.app;

import java.nio.file.Path;
import java.sql.SQLException;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.persistencia.ConfiguracionBD;
import co.edu.eia.makerlab.persistencia.PersistenciaPostgres;

/**
 * Prepara los datos al iniciar el programa. Lo usan tanto el menú de
 * consola como la interfaz gráfica.
 *
 * Si existe el archivo .env y la base responde, carga los datos desde
 * PostgreSQL (o la llena con los datos de demostración si está vacía).
 * Si no, el programa trabaja solo en memoria con los datos de demostración.
 */
public class Arranque {

    private static PersistenciaPostgres persistencia; // null = modo en memoria

    /** Devuelve un mensaje que explica de dónde salieron los datos. */
    public static String iniciar() throws MakerLabException {
        CatalogoMakerLab catalogo = CatalogoMakerLab.getInstancia();
        catalogo.reiniciar();
        try {
            ConfiguracionBD configuracion = ConfiguracionBD.desdeArchivo(Path.of(".env"));
            PersistenciaPostgres base = new PersistenciaPostgres(configuracion);
            if (!base.probarConexion()) {
                DatosDemo.cargarDatosBase();
                return "No se pudo conectar a " + configuracion.getUrl() + ". Se trabaja en memoria.";
            }
            persistencia = base;
            if (base.estaVacia()) {
                DatosDemo.cargarDatosBase();
                base.guardar(catalogo);
                return "Base de datos vacia: se guardaron los datos de demostracion.";
            }
            base.cargar(catalogo);
            catalogo.setFechaActual(DatosDemo.HOY);
            return "Datos cargados desde PostgreSQL.";
        } catch (SQLException e) {
            persistencia = null;
            catalogo.reiniciar();
            DatosDemo.cargarDatosBase();
            return "Error con la base de datos (" + e.getMessage() + "). Se trabaja en memoria.";
        } catch (MakerLabException e) {
            // No hay archivo .env o le faltan datos.
            DatosDemo.cargarDatosBase();
            return e.getMessage() + ". Se trabaja en memoria.";
        }
    }

    /** Guarda el catálogo en la base. Si se trabaja en memoria, no hace nada. */
    public static void guardar() throws MakerLabException {
        if (persistencia == null) {
            return;
        }
        try {
            persistencia.guardar(CatalogoMakerLab.getInstancia());
        } catch (SQLException e) {
            throw new MakerLabException("No se pudo guardar en la base de datos: " + e.getMessage());
        }
    }

    public static boolean hayBaseDeDatos() {
        return persistencia != null;
    }
}
