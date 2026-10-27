package co.edu.eia.makerlab.persistencia;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.CortadoraLaser;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstacionElectronica;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.EstadoReserva;
import co.edu.eia.makerlab.modelo.Estudiante;
import co.edu.eia.makerlab.modelo.Impresora3D;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.Prioridad;
import co.edu.eia.makerlab.modelo.Profesor;
import co.edu.eia.makerlab.modelo.Reserva;
import co.edu.eia.makerlab.modelo.SesionUso;
import co.edu.eia.makerlab.modelo.SolicitudEspera;
import co.edu.eia.makerlab.modelo.Tecnico;
import co.edu.eia.makerlab.modelo.TicketMantenimiento;
import co.edu.eia.makerlab.modelo.TipoEquipo;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.servicio.RegistroService;

/**
 * Guarda y carga el catálogo en PostgreSQL con JDBC.
 *
 * El programa sigue trabajando en memoria con las colecciones del catálogo
 * (List, Set, Map). La base solo sirve para que los datos no se pierdan:
 * cargar() llena el catálogo al iniciar y guardar() escribe todo el
 * catálogo después de cada operación.
 */
public class PersistenciaPostgres {

    private ConfiguracionBD configuracion;

    public PersistenciaPostgres(ConfiguracionBD configuracion) {
        this.configuracion = configuracion;
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(configuracion.getUrl(), configuracion.getUsuario(),
                configuracion.getClave());
    }

    /** true si la base responde. Sirve para avisar al usuario antes de empezar. */
    public boolean probarConexion() {
        try (Connection conexion = conectar()) {
            return conexion.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean estaVacia() throws SQLException {
        try (Connection conexion = conectar();
                Statement consulta = conexion.createStatement();
                ResultSet filas = consulta.executeQuery("SELECT COUNT(*) FROM USUARIO")) {
            filas.next();
            return filas.getInt(1) == 0;
        }
    }

    // =====================================================================
    // GUARDAR
    // =====================================================================

    /**
     * Reemplaza el contenido de la base por el del catálogo. Todo va en una
     * transacción: si algo falla, se deshace y la base queda como estaba.
     */
    public void guardar(CatalogoMakerLab catalogo) throws SQLException {
        try (Connection conexion = conectar()) {
            conexion.setAutoCommit(false);
            try {
                borrarTodo(conexion);
                guardarLaboratoriosYEquipos(conexion, catalogo);
                guardarUsuarios(conexion, catalogo);
                guardarCertificaciones(conexion, catalogo);
                guardarReservasYSesiones(conexion, catalogo);
                guardarSolicitudes(conexion, catalogo);
                guardarTickets(conexion, catalogo);
                conexion.commit();
            } catch (SQLException e) {
                conexion.rollback();
                throw e;
            }
        }
    }

    private void borrarTodo(Connection conexion) throws SQLException {
        // Orden inverso a las llaves foráneas: primero los hijos.
        String[] tablas = { "TICKET_CAMBIO", "TICKET_MANTENIMIENTO", "SOLICITUD_ESPERA", "SESION_INCIDENTE",
                "SESION_CONSUMIBLE", "SESION_USO", "RESERVA", "CERTIFICACION", "NOTIFICACION", "ESTUDIANTE",
                "PROFESOR", "TECNICO", "USUARIO", "IMPRESORA_3D", "CORTADORA_LASER", "ESTACION_ELECTRONICA",
                "EQUIPO", "LABORATORIO" };
        try (Statement sentencia = conexion.createStatement()) {
            for (String tabla : tablas) {
                sentencia.executeUpdate("DELETE FROM " + tabla);
            }
        }
    }

    private void guardarLaboratoriosYEquipos(Connection conexion, CatalogoMakerLab catalogo) throws SQLException {
        try (PreparedStatement sql = conexion.prepareStatement(
                "INSERT INTO LABORATORIO (codigo, nombre, ubicacion, hora_apertura, hora_cierre, activo) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (Laboratorio lab : catalogo.getLaboratorios().listar()) {
                sql.setString(1, lab.getCodigo());
                sql.setString(2, lab.getNombre());
                sql.setString(3, lab.getUbicacion());
                sql.setTime(4, Time.valueOf(lab.getHoraApertura()));
                sql.setTime(5, Time.valueOf(lab.getHoraCierre()));
                sql.setBoolean(6, lab.isActivo());
                sql.executeUpdate();
            }
        }
        for (Equipo equipo : catalogo.getEquipos().listar()) {
            try (PreparedStatement sql = conexion.prepareStatement(
                    "INSERT INTO EQUIPO (codigo, nombre, nivel_riesgo, estado, tipo_equipo, laboratorio_codigo) VALUES (?, ?, ?, ?, ?, ?)")) {
                sql.setString(1, equipo.getCodigo());
                sql.setString(2, equipo.getNombre());
                sql.setString(3, equipo.getNivelRiesgo());
                sql.setString(4, equipo.getEstado().name());
                sql.setString(5, equipo.getTipoEquipo().name());
                sql.setString(6, equipo.getLaboratorio().getCodigo());
                sql.executeUpdate();
            }
            guardarSubtipoEquipo(conexion, equipo);
        }
    }

    private void guardarSubtipoEquipo(Connection conexion, Equipo equipo) throws SQLException {
        if (equipo instanceof Impresora3D) {
            Impresora3D impresora = (Impresora3D) equipo;
            try (PreparedStatement sql = conexion.prepareStatement(
                    "INSERT INTO IMPRESORA_3D (equipo_codigo, tecnologia, material_soportado) VALUES (?, ?, ?)")) {
                sql.setString(1, impresora.getCodigo());
                sql.setString(2, impresora.getTecnologia());
                sql.setString(3, impresora.getMaterialSoportado());
                sql.executeUpdate();
            }
        } else if (equipo instanceof CortadoraLaser) {
            CortadoraLaser cortadora = (CortadoraLaser) equipo;
            try (PreparedStatement sql = conexion.prepareStatement(
                    "INSERT INTO CORTADORA_LASER (equipo_codigo, potencia_watts, area_trabajo) VALUES (?, ?, ?)")) {
                sql.setString(1, cortadora.getCodigo());
                sql.setInt(2, cortadora.getPotenciaWatts());
                sql.setString(3, cortadora.getAreaTrabajo());
                sql.executeUpdate();
            }
        } else if (equipo instanceof EstacionElectronica) {
            EstacionElectronica estacion = (EstacionElectronica) equipo;
            try (PreparedStatement sql = conexion.prepareStatement(
                    "INSERT INTO ESTACION_ELECTRONICA (equipo_codigo, voltaje_maximo, herramientas) VALUES (?, ?, ?)")) {
                sql.setString(1, estacion.getCodigo());
                sql.setDouble(2, estacion.getVoltajeMaximo());
                sql.setString(3, estacion.getHerramientas());
                sql.executeUpdate();
            }
        }
    }

    private void guardarUsuarios(Connection conexion, CatalogoMakerLab catalogo) throws SQLException {
        for (Usuario usuario : catalogo.getUsuarios().listar()) {
            String tipo = usuario instanceof Estudiante ? "ESTUDIANTE" : usuario instanceof Profesor ? "PROFESOR" : "TECNICO";
            try (PreparedStatement sql = conexion.prepareStatement(
                    "INSERT INTO USUARIO (id_usuario, nombre, correo, tipo_usuario) VALUES (?, ?, ?, ?)")) {
                sql.setString(1, usuario.getId());
                sql.setString(2, usuario.getNombre());
                sql.setString(3, usuario.getCorreo());
                sql.setString(4, tipo);
                sql.executeUpdate();
            }
            if (usuario instanceof Estudiante) {
                Estudiante estudiante = (Estudiante) usuario;
                try (PreparedStatement sql = conexion.prepareStatement(
                        "INSERT INTO ESTUDIANTE (id_usuario, carrera, semestre) VALUES (?, ?, ?)")) {
                    sql.setString(1, estudiante.getId());
                    sql.setString(2, estudiante.getCarrera());
                    sql.setInt(3, estudiante.getSemestre());
                    sql.executeUpdate();
                }
            } else if (usuario instanceof Profesor) {
                try (PreparedStatement sql = conexion.prepareStatement(
                        "INSERT INTO PROFESOR (id_usuario, departamento) VALUES (?, ?)")) {
                    sql.setString(1, usuario.getId());
                    sql.setString(2, ((Profesor) usuario).getDepartamento());
                    sql.executeUpdate();
                }
            } else {
                try (PreparedStatement sql = conexion.prepareStatement(
                        "INSERT INTO TECNICO (id_usuario, especialidad) VALUES (?, ?)")) {
                    sql.setString(1, usuario.getId());
                    sql.setString(2, ((Tecnico) usuario).getEspecialidad());
                    sql.executeUpdate();
                }
            }
            guardarLista(conexion, "INSERT INTO NOTIFICACION (id_usuario, numero, mensaje) VALUES (?, ?, ?)",
                    usuario.getId(), usuario.getNotificaciones());
        }
    }

    private void guardarCertificaciones(Connection conexion, CatalogoMakerLab catalogo) throws SQLException {
        try (PreparedStatement sql = conexion.prepareStatement(
                "INSERT INTO CERTIFICACION (id_certificacion, id_usuario, tipo_equipo_autorizado, fecha_emision, fecha_vencimiento, activo) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (Certificacion c : catalogo.getCertificaciones().listar()) {
                sql.setString(1, c.getId());
                sql.setString(2, c.getUsuario().getId());
                sql.setString(3, c.getTipoEquipoAutorizado().name());
                sql.setDate(4, Date.valueOf(c.getFechaEmision()));
                sql.setDate(5, Date.valueOf(c.getFechaVencimiento()));
                sql.setBoolean(6, c.isActivo());
                sql.executeUpdate();
            }
        }
    }

    private void guardarReservasYSesiones(Connection conexion, CatalogoMakerLab catalogo) throws SQLException {
        for (Reserva r : catalogo.getReservas().listar()) {
            try (PreparedStatement sql = conexion.prepareStatement(
                    "INSERT INTO RESERVA (id_reserva, id_usuario, equipo_codigo, fecha, hora_inicio, hora_fin, proposito, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                sql.setString(1, r.getId());
                sql.setString(2, r.getUsuario().getId());
                sql.setString(3, r.getEquipo().getCodigo());
                sql.setDate(4, Date.valueOf(r.getFecha()));
                sql.setTime(5, Time.valueOf(r.getHoraInicio()));
                sql.setTime(6, Time.valueOf(r.getHoraFin()));
                sql.setString(7, r.getProposito());
                sql.setString(8, r.getEstado().name());
                sql.executeUpdate();
            }
            SesionUso sesion = r.getSesionUso();
            if (sesion == null) {
                continue;
            }
            try (PreparedStatement sql = conexion.prepareStatement(
                    "INSERT INTO SESION_USO (id_sesion, id_reserva, hora_inicio_real, hora_fin_real, observaciones, activa) VALUES (?, ?, ?, ?, ?, ?)")) {
                sql.setString(1, sesion.getId());
                sql.setString(2, r.getId());
                sql.setTime(3, Time.valueOf(sesion.getHoraInicioReal()));
                sql.setTime(4, sesion.getHoraFinReal() == null ? null : Time.valueOf(sesion.getHoraFinReal()));
                sql.setString(5, sesion.getObservaciones());
                sql.setBoolean(6, sesion.isActiva());
                sql.executeUpdate();
            }
            guardarLista(conexion, "INSERT INTO SESION_CONSUMIBLE (id_sesion, numero, descripcion) VALUES (?, ?, ?)",
                    sesion.getId(), sesion.getConsumiblesUsados());
            guardarLista(conexion, "INSERT INTO SESION_INCIDENTE (id_sesion, numero, descripcion) VALUES (?, ?, ?)",
                    sesion.getId(), sesion.getIncidentesReportados());
        }
    }

    private void guardarSolicitudes(Connection conexion, CatalogoMakerLab catalogo) throws SQLException {
        try (PreparedStatement sql = conexion.prepareStatement(
                "INSERT INTO SOLICITUD_ESPERA (id_solicitud, id_usuario, equipo_codigo, fecha, hora_inicio, hora_fin, fecha_solicitud) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (SolicitudEspera s : catalogo.getListaEspera().listar()) {
                sql.setString(1, s.getId());
                sql.setString(2, s.getUsuario().getId());
                sql.setString(3, s.getEquipo().getCodigo());
                sql.setDate(4, Date.valueOf(s.getFecha()));
                sql.setTime(5, Time.valueOf(s.getHoraInicio()));
                sql.setTime(6, Time.valueOf(s.getHoraFin()));
                sql.setDate(7, Date.valueOf(s.getFechaSolicitud()));
                sql.executeUpdate();
            }
        }
    }

    private void guardarTickets(Connection conexion, CatalogoMakerLab catalogo) throws SQLException {
        for (TicketMantenimiento t : catalogo.getTickets().listar()) {
            try (PreparedStatement sql = conexion.prepareStatement(
                    "INSERT INTO TICKET_MANTENIMIENTO (id_ticket, equipo_codigo, id_tecnico, prioridad, descripcion, accion_realizada, cerrado, fecha_programada, hora_programada) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                sql.setString(1, t.getId());
                sql.setString(2, t.getEquipo().getCodigo());
                sql.setString(3, t.getTecnicoAsignado() == null ? null : t.getTecnicoAsignado().getId());
                sql.setString(4, t.getPrioridad().name());
                sql.setString(5, t.getDescripcion());
                sql.setString(6, t.getAccionRealizada());
                sql.setBoolean(7, t.isCerrado());
                sql.setDate(8, Date.valueOf(t.getFechaProgramada()));
                sql.setTime(9, Time.valueOf(t.getHoraProgramada()));
                sql.executeUpdate();
            }
            List<String> cambios = new ArrayList<>();
            for (TicketMantenimiento.Cambio cambio : t.getHistorial()) {
                cambios.add(cambio.getDescripcion());
            }
            guardarLista(conexion, "INSERT INTO TICKET_CAMBIO (id_ticket, numero, descripcion) VALUES (?, ?, ?)",
                    t.getId(), cambios);
        }
    }

    /** Guarda una lista de textos como filas (dueño, número, texto). */
    private void guardarLista(Connection conexion, String insert, String idDueno, List<String> textos)
            throws SQLException {
        try (PreparedStatement sql = conexion.prepareStatement(insert)) {
            for (int i = 0; i < textos.size(); i++) {
                sql.setString(1, idDueno);
                sql.setInt(2, i + 1);
                sql.setString(3, textos.get(i));
                sql.executeUpdate();
            }
        }
    }

    // =====================================================================
    // CARGAR
    // =====================================================================

    /**
     * Llena el catálogo (que debe estar vacío) con lo que hay en la base.
     * El orden importa: primero lo que otros objetos necesitan referenciar.
     */
    public void cargar(CatalogoMakerLab catalogo) throws SQLException, MakerLabException {
        RegistroService registro = new RegistroService();
        try (Connection conexion = conectar()) {
            cargarLaboratorios(conexion, registro);
            cargarEquipos(conexion, catalogo, registro);
            cargarUsuarios(conexion, registro);
            cargarCertificaciones(conexion, catalogo, registro);
            cargarReservas(conexion, catalogo);
            cargarSolicitudes(conexion, catalogo);
            cargarTickets(conexion, catalogo);
        }
    }

    private void cargarLaboratorios(Connection conexion, RegistroService registro)
            throws SQLException, MakerLabException {
        try (Statement consulta = conexion.createStatement();
                ResultSet fila = consulta.executeQuery("SELECT * FROM LABORATORIO ORDER BY codigo")) {
            while (fila.next()) {
                Laboratorio lab = new Laboratorio(fila.getString("codigo"), fila.getString("nombre"),
                        fila.getString("ubicacion"), fila.getTime("hora_apertura").toLocalTime(),
                        fila.getTime("hora_cierre").toLocalTime());
                lab.setActivo(fila.getBoolean("activo"));
                registro.registrarLaboratorio(lab);
            }
        }
    }

    private void cargarEquipos(Connection conexion, CatalogoMakerLab catalogo, RegistroService registro)
            throws SQLException, MakerLabException {
        String sql = "SELECT e.*, i.tecnologia, i.material_soportado, c.potencia_watts, c.area_trabajo, "
                + "s.voltaje_maximo, s.herramientas FROM EQUIPO e "
                + "LEFT JOIN IMPRESORA_3D i ON i.equipo_codigo = e.codigo "
                + "LEFT JOIN CORTADORA_LASER c ON c.equipo_codigo = e.codigo "
                + "LEFT JOIN ESTACION_ELECTRONICA s ON s.equipo_codigo = e.codigo ORDER BY e.codigo";
        try (Statement consulta = conexion.createStatement(); ResultSet fila = consulta.executeQuery(sql)) {
            while (fila.next()) {
                Laboratorio lab = catalogo.getLaboratorios().buscar(fila.getString("laboratorio_codigo"));
                TipoEquipo tipo = TipoEquipo.valueOf(fila.getString("tipo_equipo"));
                String codigo = fila.getString("codigo");
                String nombre = fila.getString("nombre");
                String riesgo = fila.getString("nivel_riesgo");
                Equipo equipo;
                if (tipo == TipoEquipo.IMPRESORA_3D) {
                    equipo = new Impresora3D(codigo, nombre, riesgo, lab, fila.getString("tecnologia"),
                            fila.getString("material_soportado"));
                } else if (tipo == TipoEquipo.CORTADORA_LASER) {
                    equipo = new CortadoraLaser(codigo, nombre, riesgo, lab, fila.getInt("potencia_watts"),
                            fila.getString("area_trabajo"));
                } else {
                    equipo = new EstacionElectronica(codigo, nombre, riesgo, lab, fila.getDouble("voltaje_maximo"),
                            fila.getString("herramientas"));
                }
                // El estado se fija ANTES de registrar el equipo, cuando todavía no tiene
                // observadores. Así cargar datos no dispara notificaciones.
                equipo.setEstado(EstadoEquipo.valueOf(fila.getString("estado")));
                registro.registrarEquipo(equipo);
            }
        }
    }

    private void cargarUsuarios(Connection conexion, RegistroService registro) throws SQLException, MakerLabException {
        String sql = "SELECT u.*, e.carrera, e.semestre, p.departamento, t.especialidad FROM USUARIO u "
                + "LEFT JOIN ESTUDIANTE e ON e.id_usuario = u.id_usuario "
                + "LEFT JOIN PROFESOR p ON p.id_usuario = u.id_usuario "
                + "LEFT JOIN TECNICO t ON t.id_usuario = u.id_usuario ORDER BY u.id_usuario";
        Map<String, Usuario> cargados = new HashMap<>();
        try (Statement consulta = conexion.createStatement(); ResultSet fila = consulta.executeQuery(sql)) {
            while (fila.next()) {
                String id = fila.getString("id_usuario");
                String nombre = fila.getString("nombre");
                String correo = fila.getString("correo");
                String tipo = fila.getString("tipo_usuario");
                Usuario usuario;
                if (tipo.equals("ESTUDIANTE")) {
                    usuario = new Estudiante(id, nombre, correo, fila.getString("carrera"), fila.getInt("semestre"));
                } else if (tipo.equals("PROFESOR")) {
                    usuario = new Profesor(id, nombre, correo, fila.getString("departamento"));
                } else {
                    usuario = new Tecnico(id, nombre, correo, fila.getString("especialidad"));
                }
                registro.registrarUsuario(usuario);
                cargados.put(id, usuario);
            }
        }
        try (Statement consulta = conexion.createStatement();
                ResultSet fila = consulta.executeQuery("SELECT * FROM NOTIFICACION ORDER BY id_usuario, numero")) {
            while (fila.next()) {
                cargados.get(fila.getString("id_usuario")).agregarNotificacion(fila.getString("mensaje"));
            }
        }
    }

    private void cargarCertificaciones(Connection conexion, CatalogoMakerLab catalogo, RegistroService registro)
            throws SQLException, MakerLabException {
        try (Statement consulta = conexion.createStatement();
                ResultSet fila = consulta.executeQuery("SELECT * FROM CERTIFICACION ORDER BY id_certificacion")) {
            while (fila.next()) {
                Certificacion c = new Certificacion(fila.getString("id_certificacion"),
                        fila.getDate("fecha_emision").toLocalDate(), fila.getDate("fecha_vencimiento").toLocalDate(),
                        catalogo.getUsuarios().buscar(fila.getString("id_usuario")),
                        TipoEquipo.valueOf(fila.getString("tipo_equipo_autorizado")));
                if (!fila.getBoolean("activo")) {
                    c.revocar();
                }
                registro.registrarCertificacion(c);
            }
        }
    }

    /**
     * Las reservas ya pasaron las validaciones cuando se crearon, así que se
     * reconstruyen directamente. La sesión se rehace con los mismos métodos
     * del modelo: abrir, registrar consumibles e incidentes y cerrar.
     */
    private void cargarReservas(Connection conexion, CatalogoMakerLab catalogo) throws SQLException, MakerLabException {
        String sql = "SELECT r.*, s.id_sesion, s.hora_inicio_real, s.hora_fin_real, s.observaciones, s.activa "
                + "FROM RESERVA r LEFT JOIN SESION_USO s ON s.id_reserva = r.id_reserva ORDER BY r.id_reserva";
        try (Statement consulta = conexion.createStatement(); ResultSet fila = consulta.executeQuery(sql)) {
            while (fila.next()) {
                Reserva reserva = new Reserva(fila.getString("id_reserva"), fila.getDate("fecha").toLocalDate(),
                        fila.getTime("hora_inicio").toLocalTime(), fila.getTime("hora_fin").toLocalTime(),
                        fila.getString("proposito"), catalogo.getUsuarios().buscar(fila.getString("id_usuario")),
                        catalogo.getEquipos().buscar(fila.getString("equipo_codigo")));
                if (EstadoReserva.valueOf(fila.getString("estado")) == EstadoReserva.CANCELADA) {
                    reserva.cancelar();
                }
                String idSesion = fila.getString("id_sesion");
                if (idSesion != null) {
                    SesionUso sesion = reserva.abrirSesion(idSesion, fila.getTime("hora_inicio_real").toLocalTime());
                    for (String consumible : leerLista(conexion, "SESION_CONSUMIBLE", "id_sesion", idSesion)) {
                        sesion.registrarConsumible(consumible);
                    }
                    for (String incidente : leerLista(conexion, "SESION_INCIDENTE", "id_sesion", idSesion)) {
                        sesion.registrarIncidente(incidente);
                    }
                    if (!fila.getBoolean("activa")) {
                        sesion.cerrar(fila.getTime("hora_fin_real").toLocalTime(), fila.getString("observaciones"));
                    }
                }
                catalogo.getReservas().agregar(reserva);
            }
        }
    }

    private void cargarSolicitudes(Connection conexion, CatalogoMakerLab catalogo) throws SQLException {
        try (Statement consulta = conexion.createStatement();
                ResultSet fila = consulta.executeQuery("SELECT * FROM SOLICITUD_ESPERA ORDER BY id_solicitud")) {
            while (fila.next()) {
                catalogo.getListaEspera().restaurar(new SolicitudEspera(fila.getString("id_solicitud"),
                        catalogo.getUsuarios().buscar(fila.getString("id_usuario")),
                        catalogo.getEquipos().buscar(fila.getString("equipo_codigo")),
                        fila.getDate("fecha").toLocalDate(), fila.getTime("hora_inicio").toLocalTime(),
                        fila.getTime("hora_fin").toLocalTime(), fila.getDate("fecha_solicitud").toLocalDate()));
            }
        }
    }

    private void cargarTickets(Connection conexion, CatalogoMakerLab catalogo) throws SQLException, MakerLabException {
        try (Statement consulta = conexion.createStatement();
                ResultSet fila = consulta.executeQuery("SELECT * FROM TICKET_MANTENIMIENTO ORDER BY id_ticket")) {
            while (fila.next()) {
                String id = fila.getString("id_ticket");
                TicketMantenimiento ticket = new TicketMantenimiento(id, Prioridad.valueOf(fila.getString("prioridad")),
                        fila.getString("descripcion"), catalogo.getEquipos().buscar(fila.getString("equipo_codigo")),
                        fila.getDate("fecha_programada").toLocalDate(), fila.getTime("hora_programada").toLocalTime());
                String idTecnico = fila.getString("id_tecnico");
                Tecnico tecnico = idTecnico == null ? null : (Tecnico) catalogo.getUsuarios().buscar(idTecnico);
                ticket.restaurar(tecnico, fila.getString("accion_realizada"), fila.getBoolean("cerrado"),
                        leerLista(conexion, "TICKET_CAMBIO", "id_ticket", id));
                catalogo.getTickets().agregar(ticket);
            }
        }
    }

    /** Lee la columna "descripcion" de una tabla de lista, en orden. */
    private List<String> leerLista(Connection conexion, String tabla, String columnaDueno, String idDueno)
            throws SQLException {
        List<String> textos = new ArrayList<>();
        try (PreparedStatement sql = conexion.prepareStatement(
                "SELECT descripcion FROM " + tabla + " WHERE " + columnaDueno + " = ? ORDER BY numero")) {
            sql.setString(1, idDueno);
            try (ResultSet fila = sql.executeQuery()) {
                while (fila.next()) {
                    textos.add(fila.getString(1));
                }
            }
        }
        return textos;
    }
}
