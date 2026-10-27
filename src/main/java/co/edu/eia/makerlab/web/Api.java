package co.edu.eia.makerlab.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import co.edu.eia.makerlab.excepcion.CruceHorarioException;
import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.EstadoReserva;
import co.edu.eia.makerlab.modelo.Estudiante;
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
import co.edu.eia.makerlab.patron.EquipoFactory;
import co.edu.eia.makerlab.patron.EstrategiaFifo;
import co.edu.eia.makerlab.patron.EstrategiaProfesoresPrimero;

/**
 * La API REST. Cada método llama a los mismos servicios que usan la interfaz
 * JavaFX y el menú de consola (RegistroService, ReservaService,
 * MantenimientoService, ConsultaService) y devuelve JSON. Aquí no hay reglas
 * de negocio: las rechazos salen de las excepciones del sistema, con su
 * mensaje exacto. Lo único propio de la capa web es revisar que los datos
 * lleguen completos y que quepan en las columnas de la base.
 */
public class Api {

    /** Respuesta lista para escribir. */
    public static final class Respuesta {
        final int estado;
        final Object cuerpo;

        Respuesta(int estado, Object cuerpo) {
            this.estado = estado;
            this.cuerpo = cuerpo;
        }
    }

    @FunctionalInterface
    interface Manejador {
        Respuesta manejar(Peticion p) throws Exception;
    }

    private static final String[] DIAS = { "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo" };
    private static final String[] MESES = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
            "septiembre", "octubre", "noviembre", "diciembre" };

    /** Igual que la vista JavaFX ("martes 8 de septiembre de 2026"), pero sin depender de los datos de idioma del JRE. */
    static String fechaLarga(LocalDate f) {
        return DIAS[f.getDayOfWeek().getValue() - 1] + " " + f.getDayOfMonth() + " de " + MESES[f.getMonthValue() - 1]
                + " de " + f.getYear();
    }

    private final AlmacenMakerLab almacen;
    private final CatalogoMakerLab catalogo;
    private final String tokenDemo; // null = modo demostración desactivado

    // Freno simple contra adivinar el token: 8 fallos por minuto y por servidor.
    private final AtomicInteger fallosToken = new AtomicInteger();
    private final AtomicLong ventanaToken = new AtomicLong();

    public Api(AlmacenMakerLab almacen, String tokenDemo) {
        this.almacen = almacen;
        this.catalogo = almacen.catalogo();
        this.tokenDemo = tokenDemo == null || tokenDemo.isBlank() ? null : tokenDemo;
    }

    /** Registra todas las rutas. */
    public void registrar(Router r) {
        r.agregar("GET", "/api/salud", p -> ok(Json.mapa("ok", true)));
        r.agregar("GET", "/api/estado", this::estado);
        r.agregar("GET", "/api/inicio", this::inicio);

        r.agregar("GET", "/api/usuarios", this::usuarios);
        r.agregar("POST", "/api/usuarios", this::crearUsuario);
        r.agregar("POST", "/api/usuarios/{id}/certificaciones", this::crearCertificacion);

        r.agregar("GET", "/api/equipos", this::equipos);
        r.agregar("POST", "/api/equipos", this::crearEquipo);
        r.agregar("POST", "/api/equipos/{codigo}/fuera-de-servicio", this::ponerFueraDeServicio);

        r.agregar("GET", "/api/reservas", this::reservas);
        r.agregar("POST", "/api/reservas", this::crearReserva);
        r.agregar("POST", "/api/reservas/{id}/cancelar", this::cancelarReserva);
        r.agregar("POST", "/api/reservas/{id}/sesion/abrir", this::abrirSesion);
        r.agregar("POST", "/api/reservas/{id}/sesion/cerrar", this::cerrarSesion);
        r.agregar("PUT", "/api/espera/estrategia", this::cambiarEstrategia);

        r.agregar("GET", "/api/mantenimiento", this::mantenimiento);
        r.agregar("POST", "/api/tickets", this::crearTicket);
        r.agregar("POST", "/api/tickets/{id}/asignar", this::asignarTicket);
        r.agregar("POST", "/api/tickets/{id}/cerrar", this::cerrarTicket);

        r.agregar("GET", "/api/indicadores", this::indicadores);
        r.agregar("GET", "/api/disponibilidad", this::disponibilidad);

        r.agregar("POST", "/api/demo/restaurar", this::restaurarDemo);
    }

    // ----------------------------------------------------- manejo de resultados

    static Respuesta ok(Object cuerpo) {
        return new Respuesta(200, cuerpo);
    }

    private static Respuesta creado(Object cuerpo) {
        return new Respuesta(201, cuerpo);
    }

    static Map<String, Object> error(String tipo, String mensaje) {
        return Json.mapa("ok", false, "tipo", tipo, "mensaje", mensaje);
    }

    /** Convierte cualquier excepción en la respuesta HTTP que corresponde. */
    Respuesta ejecutar(Manejador manejador, Peticion p) {
        try {
            return manejador.manejar(p);
        } catch (CruceHorarioException e) {
            // No es un fallo de quien reserva: la solicitud quedó en la lista de espera.
            Map<String, Object> cuerpo = error("CruceHorarioException", e.getMessage());
            cuerpo.put("enListaEspera", true);
            return new Respuesta(409, cuerpo);
        } catch (MakerLabException e) {
            return new Respuesta(422, error(e.getClass().getSimpleName(), e.getMessage()));
        } catch (ApiError e) {
            return new Respuesta(e.getEstado(), error(e.getTipo(), e.getMessage()));
        } catch (IllegalArgumentException e) {
            // Los constructores del modelo validan sus datos con IllegalArgumentException.
            return new Respuesta(400, error("DatosInvalidos", "Revise los datos: " + e.getMessage()));
        } catch (Exception e) {
            System.err.println("[api] Error interno en " + p.metodo() + " " + p.ruta() + ": " + e);
            e.printStackTrace();
            return new Respuesta(500, error("ErrorInterno", "Error interno del servidor. Intente de nuevo."));
        }
    }

    /** Las búsquedas que fallan se responden 404 con el mensaje del propio servicio. */
    private static <T> T buscar(AlmacenMakerLab.Operacion<T> busqueda) {
        try {
            return busqueda.ejecutar();
        } catch (MakerLabException e) {
            throw ApiError.noExiste(e.getMessage());
        }
    }

    private Usuario usuario(String id) {
        return buscar(() -> almacen.registro().buscarUsuario(id));
    }

    private Equipo equipo(String codigo) {
        return buscar(() -> almacen.registro().buscarEquipo(codigo));
    }

    private Laboratorio laboratorio(String codigo) {
        return buscar(() -> almacen.registro().buscarLaboratorio(codigo));
    }

    private Reserva reserva(String id) {
        return buscar(() -> almacen.reservas().buscarReserva(id));
    }

    private TicketMantenimiento ticket(String id) {
        return buscar(() -> almacen.mantenimiento().buscarTicket(id));
    }

    private static <E extends Enum<E>> E enumerado(Class<E> tipo, String valor, String etiqueta) {
        try {
            return Enum.valueOf(tipo, valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiError.datos("Valor no válido para " + etiqueta + ": " + valor + ".");
        }
    }

    // ------------------------------------------------------------------- estado

    private Respuesta estado(Peticion p) throws MakerLabException {
        return ok(almacen.leer(() -> {
            Map<String, Object> m = Json.mapa("ok", true, "baseDeDatos", almacen.hayBaseDeDatos(), "modo",
                    almacen.hayBaseDeDatos() ? "PostgreSQL" : "Memoria", "mensaje", almacen.getMensajeArranque(),
                    "fechaSistema", catalogo.getFechaActual().toString(), "estrategia", nombreEstrategia(),
                    "guardados", almacen.getGuardados(), "ultimoGuardadoMs", almacen.getUltimoGuardadoMs(),
                    "demoHabilitado", tokenDemo != null, "version", "2.0-web");
            return m;
        }));
    }

    private String nombreEstrategia() {
        return catalogo.getEstrategiaEspera() instanceof EstrategiaProfesoresPrimero ? "PROFESORES_PRIMERO" : "FIFO";
    }

    // ------------------------------------------------------------------- inicio

    private Respuesta inicio(Peticion p) throws MakerLabException {
        return ok(almacen.leer(() -> {
            LocalDate hoy = catalogo.getFechaActual();
            List<Object> labs = new ArrayList<>();
            for (Laboratorio lab : catalogo.getLaboratorios().listar()) {
                labs.add(Mapeo.laboratorio(lab));
            }
            List<Reserva> futuras = new ArrayList<>();
            for (Reserva r : catalogo.getReservas().listar()) {
                if (r.getEstado() == EstadoReserva.CONFIRMADA && !r.getFecha().isBefore(hoy)) {
                    futuras.add(r);
                }
            }
            futuras.sort(Comparator.comparing(Reserva::getFecha).thenComparing(Reserva::getHoraInicio));
            List<Object> proximas = new ArrayList<>();
            for (Reserva r : futuras) {
                proximas.add(Mapeo.reserva(r));
            }
            return Json.mapa("fechaSistema", hoy.toString(), "fechaTexto", fechaLarga(hoy), "laboratorios",
                    labs, "proximas", proximas);
        }));
    }

    // ----------------------------------------------------------------- usuarios

    private Respuesta usuarios(Peticion p) throws MakerLabException {
        return ok(almacen.leer(() -> {
            LocalDate hoy = catalogo.getFechaActual();
            List<Certificacion> certs = catalogo.getCertificaciones().listar();
            List<Object> lista = new ArrayList<>();
            for (Usuario u : catalogo.getUsuarios().listar()) {
                lista.add(Mapeo.usuario(u, certs, hoy));
            }
            return Json.mapa("fechaSistema", hoy.toString(), "usuarios", lista, "tiposEquipo", tiposEquipo());
        }));
    }

    private static List<Object> tiposEquipo() {
        List<Object> tipos = new ArrayList<>();
        for (TipoEquipo t : TipoEquipo.values()) {
            tipos.add(Json.mapa("codigo", t.name(), "nombre", Mapeo.nombreTipo(t)));
        }
        return tipos;
    }

    private Respuesta crearUsuario(Peticion p) throws MakerLabException {
        String tipo = p.texto("tipo", "el tipo de usuario", 20);
        String nombre = p.texto("nombre", "el nombre", 100);
        String correo = p.texto("correo", "el correo", 100);
        String detalle = p.texto("detalle", tipo.equalsIgnoreCase("ESTUDIANTE") ? "la carrera"
                : tipo.equalsIgnoreCase("PROFESOR") ? "el departamento" : "la especialidad", 100);
        if (!correo.matches("[^@\\s]+@[^@\\s]+")) {
            throw ApiError.datos("Correo inválido: escríbalo como nombre@eia.edu.co.");
        }
        String tipoNormal = tipo.toUpperCase(Locale.ROOT);
        int semestre = 0;
        if (tipoNormal.equals("ESTUDIANTE")) {
            semestre = entero(p, "semestre", "el semestre");
            if (semestre < 1 || semestre > 12) {
                throw ApiError.datos("El semestre debe estar entre 1 y 12.");
            }
        } else if (!tipoNormal.equals("PROFESOR") && !tipoNormal.equals("TECNICO")) {
            throw ApiError.datos("El tipo de usuario debe ser Estudiante, Profesor o Técnico.");
        }
        final int semestreFinal = semestre;
        return creado(almacen.escribir(() -> {
            // La base exige correos únicos; el modelo no lo revisa, así que se revisa aquí.
            for (Usuario existente : catalogo.getUsuarios().listar()) {
                if (existente.getCorreo().equalsIgnoreCase(correo)) {
                    throw ApiError.datos("Ya existe un usuario con el correo " + correo + ".");
                }
            }
            String id = almacen.registro().siguienteIdUsuario(); // el id lo genera el sistema
            Usuario usuario;
            if (tipoNormal.equals("ESTUDIANTE")) {
                usuario = new Estudiante(id, nombre, correo, detalle, semestreFinal);
            } else if (tipoNormal.equals("PROFESOR")) {
                usuario = new Profesor(id, nombre, correo, detalle);
            } else {
                usuario = new Tecnico(id, nombre, correo, detalle);
            }
            almacen.registro().registrarUsuario(usuario);
            return Json.mapa("ok", true, "mensaje", "Usuario " + id + " registrado.", "id", id);
        }));
    }

    private Respuesta crearCertificacion(Peticion p) throws MakerLabException {
        String idUsuario = p.ruta("id");
        TipoEquipo tipo = enumerado(TipoEquipo.class, p.texto("tipoEquipo", "el tipo de equipo", 30),
                "el tipo de equipo");
        LocalDate emision = p.fecha("emision", "la fecha de emisión");
        LocalDate vencimiento = p.fecha("vencimiento", "la fecha de vencimiento");
        return creado(almacen.escribir(() -> {
            Usuario usuario = usuario(idUsuario);
            String id = almacen.registro().siguienteIdCertificacion();
            // Certificacion lanza IllegalArgumentException si el vencimiento es anterior a la emisión.
            almacen.registro().registrarCertificacion(new Certificacion(id, emision, vencimiento, usuario, tipo));
            return Json.mapa("ok", true, "mensaje",
                    "Certificación " + id + " registrada para " + usuario.getNombre() + ".", "id", id);
        }));
    }

    // ------------------------------------------------------------------ equipos

    private Respuesta equipos(Peticion p) throws MakerLabException {
        return ok(almacen.leer(() -> {
            List<Object> lista = new ArrayList<>();
            for (Equipo e : catalogo.getEquipos().listar()) {
                lista.add(Mapeo.equipo(e));
            }
            List<Object> labs = new ArrayList<>();
            for (Laboratorio l : catalogo.getLaboratorios().listar()) {
                labs.add(Json.mapa("codigo", l.getCodigo(), "nombre", l.getNombre()));
            }
            return Json.mapa("equipos", lista, "laboratorios", labs, "tiposEquipo", tiposEquipo());
        }));
    }

    private Respuesta crearEquipo(Peticion p) throws MakerLabException {
        TipoEquipo tipo = enumerado(TipoEquipo.class, p.texto("tipo", "el tipo de equipo", 30), "el tipo de equipo");
        String nombre = p.texto("nombre", "el nombre", 100);
        String riesgo = p.texto("riesgo", "el nivel de riesgo", 10).toUpperCase(Locale.ROOT);
        if (!riesgo.equals("BAJO") && !riesgo.equals("MEDIO") && !riesgo.equals("ALTO")) {
            throw ApiError.datos("El riesgo debe ser BAJO, MEDIO o ALTO.");
        }
        String codigoLab = p.texto("laboratorio", "el laboratorio", 20);
        String dato1;
        String dato2;
        if (tipo == TipoEquipo.IMPRESORA_3D) {
            dato1 = p.texto("dato1", "la tecnología", 100);
            dato2 = p.texto("dato2", "el material soportado", 100);
        } else if (tipo == TipoEquipo.CORTADORA_LASER) {
            dato1 = p.texto("dato1", "la potencia", 9);
            if (!dato1.matches("[0-9]{1,9}") || Long.parseLong(dato1) < 1 || Long.parseLong(dato1) > 1_000_000) {
                throw ApiError.datos("La potencia debe ser un número entero de watts (por ejemplo 60).");
            }
            dato2 = p.texto("dato2", "el área de trabajo", 100);
        } else {
            dato1 = p.texto("dato1", "el voltaje máximo", 9).replace(',', '.');
            if (!dato1.matches("[0-9]{1,5}(\\.[0-9]{1,2})?") || Double.parseDouble(dato1) <= 0) {
                throw ApiError.datos("El voltaje máximo debe ser un número mayor que 0 (por ejemplo 24.0).");
            }
            dato2 = p.texto("dato2", "las herramientas", 200);
        }
        final String d1 = dato1;
        final String d2 = dato2;
        return creado(almacen.escribir(() -> {
            Laboratorio lab = laboratorio(codigoLab);
            String codigo = almacen.registro().siguienteIdEquipo(); // el código lo genera el sistema
            // Patrón Factory: no hace falta saber qué subclase se crea.
            Equipo equipo = EquipoFactory.crear(tipo, codigo, nombre, riesgo, lab, d1, d2);
            almacen.registro().registrarEquipo(equipo);
            return Json.mapa("ok", true, "mensaje", "Equipo " + codigo + " registrado.", "codigo", codigo);
        }));
    }

    private Respuesta ponerFueraDeServicio(Peticion p) throws MakerLabException {
        String codigo = p.ruta("codigo");
        return ok(almacen.escribir(() -> {
            Equipo equipo = equipo(codigo);
            equipo.setEstado(EstadoEquipo.FUERA_DE_SERVICIO); // el Observer avisa a quien tenía reservas
            return Json.mapa("ok", true, "mensaje", "El equipo " + codigo + " quedó fuera de servicio.");
        }));
    }

    // ------------------------------------------------------------------ reservas

    private Respuesta reservas(Peticion p) throws MakerLabException {
        return ok(almacen.leer(() -> {
            List<Object> lista = new ArrayList<>();
            for (Reserva r : catalogo.getReservas().listar()) {
                lista.add(Mapeo.reserva(r));
            }
            List<Object> espera = new ArrayList<>();
            for (SolicitudEspera s : catalogo.getListaEspera().listar()) {
                espera.add(Mapeo.solicitud(s));
            }
            List<Object> usuarios = new ArrayList<>();
            for (Usuario u : catalogo.getUsuarios().listar()) {
                usuarios.add(Json.mapa("id", u.getId(), "nombre", u.getNombre(), "tipoNombre",
                        Mapeo.nombreTipo(u)));
            }
            List<Object> equipos = new ArrayList<>();
            for (Equipo e : catalogo.getEquipos().listar()) {
                equipos.add(Json.mapa("codigo", e.getCodigo(), "nombre", e.getNombre(), "estado",
                        e.getEstado().name(), "estadoNombre", Mapeo.nombreEstado(e.getEstado())));
            }
            return Json.mapa("fechaSistema", catalogo.getFechaActual().toString(), "fechaSugerida",
                    catalogo.getFechaActual().plusDays(1).toString(), "estrategia", nombreEstrategia(), "reservas",
                    lista, "espera", espera, "usuarios", usuarios, "equipos", equipos);
        }));
    }

    private Respuesta crearReserva(Peticion p) throws MakerLabException {
        String idUsuario = p.texto("usuario", "el usuario", 20);
        String codigoEquipo = p.texto("equipo", "el equipo", 20);
        LocalDate fecha = p.fecha("fecha", "la fecha");
        LocalTime inicio = p.hora("inicio", "la hora de inicio");
        LocalTime fin = p.hora("fin", "la hora final");
        String proposito = p.texto("proposito", "el propósito", 200);
        return creado(almacen.escribir(() -> {
            Usuario u = usuario(idUsuario);
            Equipo eq = equipo(codigoEquipo);
            // Si hay cruce, el servicio deja la solicitud en la lista de espera y lanza CruceHorarioException.
            Reserva nueva = almacen.reservas().crearReserva(u, eq, fecha, inicio, fin, proposito);
            return Json.mapa("ok", true, "mensaje", "Reserva " + nueva.getId() + " creada.", "id", nueva.getId());
        }));
    }

    private Respuesta cancelarReserva(Peticion p) throws MakerLabException {
        String id = p.ruta("id");
        return ok(almacen.escribir(() -> {
            Reserva r = reserva(id);
            SolicitudEspera avisada = almacen.reservas().cancelarReserva(r);
            Map<String, Object> m = Json.mapa("ok", true, "mensaje", "Reserva " + id + " cancelada.");
            if (avisada != null) {
                // Mismo texto que muestra la interfaz JavaFX.
                m.put("mensaje", "Reserva cancelada. Se avisó a " + avisada.getUsuario().getNombre()
                        + ", que esperaba esa franja.");
                m.put("avisada", Mapeo.solicitud(avisada));
            }
            return m;
        }));
    }

    private Respuesta abrirSesion(Peticion p) throws MakerLabException {
        String id = p.ruta("id");
        LocalTime horaInicio = p.hora("horaInicio", "la hora real de inicio");
        return ok(almacen.escribir(() -> {
            SesionUso sesion = almacen.reservas().abrirSesion(reserva(id), horaInicio);
            return Json.mapa("ok", true, "mensaje", "Sesión " + sesion.getId() + " abierta.", "id", sesion.getId());
        }));
    }

    private Respuesta cerrarSesion(Peticion p) throws MakerLabException {
        String id = p.ruta("id");
        LocalTime horaFin = p.hora("horaFin", "la hora real de fin");
        String consumible = p.textoOpcional("consumible", "el consumible", 200);
        String incidente = p.textoOpcional("incidente", "el incidente", 200);
        String observaciones = p.textoOpcional("observaciones", "las observaciones", 200);
        return ok(almacen.escribir(() -> {
            Reserva r = reserva(id);
            SesionUso sesion = r.getSesionUso();
            if (sesion == null) {
                throw ApiError.datos("La reserva " + id + " no tiene una sesión abierta.");
            }
            // Si cerrar() va a rechazar (sesión ya cerrada u hora inválida), se llama primero para que
            // salga el mensaje exacto del sistema sin dejar a medias el consumible o el incidente.
            if (!sesion.isActiva() || !horaFin.isAfter(sesion.getHoraInicioReal())) {
                sesion.cerrar(horaFin, observaciones);
            }
            if (!consumible.isEmpty()) {
                sesion.registrarConsumible(consumible);
            }
            if (!incidente.isEmpty()) {
                sesion.registrarIncidente(incidente);
            }
            sesion.cerrar(horaFin, observaciones);
            return Json.mapa("ok", true, "mensaje", "Sesión " + sesion.getId() + " cerrada: "
                    + sesion.getDuracionMinutos() + " minutos de uso.");
        }));
    }

    private Respuesta cambiarEstrategia(Peticion p) throws MakerLabException {
        String valor = p.texto("estrategia", "el criterio", 30).toUpperCase(Locale.ROOT);
        if (!valor.equals("FIFO") && !valor.equals("PROFESORES_PRIMERO")) {
            throw ApiError.datos("El criterio debe ser FIFO o PROFESORES_PRIMERO.");
        }
        return ok(almacen.escribirSoloMemoria(() -> {
            catalogo.setEstrategiaEspera(
                    valor.equals("PROFESORES_PRIMERO") ? new EstrategiaProfesoresPrimero() : new EstrategiaFifo());
            return Json.mapa("ok", true, "estrategia", valor);
        }));
    }

    // -------------------------------------------------------------- mantenimiento

    private Respuesta mantenimiento(Peticion p) throws MakerLabException {
        return ok(almacen.leer(() -> {
            List<Object> tickets = new ArrayList<>();
            for (TicketMantenimiento t : catalogo.getTickets().listar()) {
                tickets.add(Mapeo.ticket(t));
            }
            List<Object> tecnicos = new ArrayList<>();
            for (Usuario u : catalogo.getUsuarios().listar()) {
                if (u instanceof Tecnico) {
                    tecnicos.add(Json.mapa("id", u.getId(), "nombre", u.getNombre(), "especialidad",
                            ((Tecnico) u).getEspecialidad()));
                }
            }
            List<Object> equipos = new ArrayList<>();
            for (Equipo e : catalogo.getEquipos().listar()) {
                equipos.add(Json.mapa("codigo", e.getCodigo(), "nombre", e.getNombre(), "estado",
                        e.getEstado().name(), "estadoNombre", Mapeo.nombreEstado(e.getEstado())));
            }
            return Json.mapa("fechaSistema", catalogo.getFechaActual().toString(), "tickets", tickets, "tecnicos",
                    tecnicos, "equipos", equipos);
        }));
    }

    private Respuesta crearTicket(Peticion p) throws MakerLabException {
        String codigoEquipo = p.texto("equipo", "el equipo", 20);
        Prioridad prioridad = enumerado(Prioridad.class, p.texto("prioridad", "la prioridad", 10), "la prioridad");
        // "Ticket creado: " + descripción tiene que caber en los 300 caracteres del historial.
        String descripcion = p.texto("descripcion", "la descripción de la falla", 200);
        LocalDate fecha = p.fecha("fecha", "la fecha");
        LocalTime hora = p.hora("hora", "la hora");
        return creado(almacen.escribir(() -> {
            Equipo eq = equipo(codigoEquipo);
            TicketMantenimiento t = almacen.mantenimiento().crearTicket(prioridad, descripcion, eq, fecha, hora);
            return Json.mapa("ok", true, "mensaje",
                    "Ticket " + t.getId() + " creado. " + eq.getCodigo() + " queda en mantenimiento.", "id",
                    t.getId());
        }));
    }

    private Respuesta asignarTicket(Peticion p) throws MakerLabException {
        String id = p.ruta("id");
        String idTecnico = p.texto("tecnico", "el técnico", 20);
        return ok(almacen.escribir(() -> {
            Usuario u = usuario(idTecnico);
            if (!(u instanceof Tecnico)) {
                throw ApiError.datos(u.getNombre() + " no es técnico.");
            }
            almacen.mantenimiento().asignarTecnico(ticket(id), (Tecnico) u);
            return Json.mapa("ok", true, "mensaje", "Ticket " + id + " asignado a " + u.getNombre() + ".");
        }));
    }

    private Respuesta cerrarTicket(Peticion p) throws MakerLabException {
        String id = p.ruta("id");
        // "Accion: " + acción tiene que caber en los 300 caracteres del historial.
        String accion = p.texto("accion", "la acción realizada", 280);
        boolean reparado = p.booleano("reparado");
        EstadoEquipo estadoFinal = reparado ? EstadoEquipo.OPERATIVO : EstadoEquipo.FUERA_DE_SERVICIO;
        return ok(almacen.escribir(() -> {
            TicketMantenimiento t = ticket(id);
            // Si cerrar() va a rechazar por falta de técnico o porque ya estaba cerrado, se llama primero para
            // que salga el mensaje exacto del sistema sin dejar la acción registrada a medias.
            if (t.isCerrado()) {
                t.registrarAccion(accion);
            } else if (t.getTecnicoAsignado() == null) {
                t.cerrar(estadoFinal);
            }
            t.registrarAccion(accion);
            t.cerrar(estadoFinal);
            return Json.mapa("ok", true, "mensaje", "Ticket " + id + " cerrado. El equipo queda "
                    + Mapeo.nombreEstado(estadoFinal).toLowerCase(Locale.ROOT) + ".");
        }));
    }

    // -------------------------------------------------------------- indicadores

    private Respuesta indicadores(Peticion p) throws MakerLabException {
        return ok(almacen.leer(() -> {
            List<Object> horas = new ArrayList<>();
            for (Map.Entry<TipoEquipo, Double> d : almacen.consultas().horasDeUsoPorTipo().entrySet()) {
                horas.add(Json.mapa("tipo", d.getKey().name(), "nombre", Mapeo.nombreTipo(d.getKey()), "horas",
                        Math.round(d.getValue() * 100) / 100.0));
            }
            List<Object> carga = new ArrayList<>();
            for (Map.Entry<String, Integer> d : almacen.consultas().cargaMantenimiento().entrySet()) {
                carga.add(Json.mapa("tecnico", d.getKey().equals("SIN ASIGNAR") ? "Sin asignar" : d.getKey(),
                        "cantidad", d.getValue()));
            }
            List<Object> porVencer = new ArrayList<>();
            for (Certificacion c : almacen.consultas().consultarPorVencer(30)) {
                porVencer.add(Json.mapa("id", c.getId(), "usuario", c.getUsuario().getNombre(), "tipoEquipoNombre",
                        Mapeo.nombreTipo(c.getTipoEquipoAutorizado()), "vencimiento",
                        c.getFechaVencimiento().toString()));
            }
            List<Object> fuera = new ArrayList<>();
            for (Equipo e : almacen.consultas().consultarFueraDeServicio()) {
                fuera.add(Json.mapa("codigo", e.getCodigo(), "nombre", e.getNombre(), "estado",
                        e.getEstado().name(), "estadoNombre", Mapeo.nombreEstado(e.getEstado())));
            }
            List<Object> labs = new ArrayList<>();
            for (Laboratorio l : catalogo.getLaboratorios().listar()) {
                labs.add(Json.mapa("codigo", l.getCodigo(), "nombre", l.getNombre()));
            }
            return Json.mapa("fechaSistema", catalogo.getFechaActual().toString(), "horasPorTipo", horas,
                    "incidentes", almacen.consultas().cantidadIncidentes(), "tasaCancelacion",
                    Math.round(almacen.consultas().tasaCancelacion() * 1000) / 10.0, "cargaMantenimiento", carga,
                    "porVencer", porVencer, "fueraDeServicio", fuera, "laboratorios", labs);
        }));
    }

    private Respuesta disponibilidad(Peticion p) throws MakerLabException {
        String codigoLab = requerido(p, "laboratorio", "el laboratorio");
        LocalDate fecha = Peticion.parseFecha(requerido(p, "fecha", "la fecha"), "la fecha");
        LocalTime desde = Peticion.parseHora(requerido(p, "desde", "la hora de inicio"), "la hora de inicio");
        LocalTime hasta = Peticion.parseHora(requerido(p, "hasta", "la hora final"), "la hora final");
        if (!hasta.isAfter(desde)) {
            throw ApiError.datos("La hora final debe ser posterior a la inicial.");
        }
        return ok(almacen.leer(() -> {
            Laboratorio lab = laboratorio(codigoLab);
            List<Object> libres = new ArrayList<>();
            for (Equipo e : almacen.consultas().consultarDisponibilidad(lab, fecha, desde, hasta)) {
                libres.add(Json.mapa("codigo", e.getCodigo(), "nombre", e.getNombre()));
            }
            return Json.mapa("laboratorio", lab.getCodigo(), "libres", libres);
        }));
    }

    private static String requerido(Peticion p, String nombre, String etiqueta) {
        String v = p.consulta(nombre);
        if (v == null || v.isBlank()) {
            throw ApiError.datos("Falta " + etiqueta + ".");
        }
        return v.trim();
    }

    private static int entero(Peticion p, String campo, String etiqueta) {
        String t = p.texto(campo, etiqueta, 6);
        if (!t.matches("-?[0-9]{1,6}")) {
            throw ApiError.datos("Revise " + etiqueta + ": debe ser un número entero.");
        }
        return Integer.parseInt(t);
    }

    // ----------------------------------------------------------- modo demostración

    /**
     * Restaura los datos de DatosDemo. Está protegido con el secreto DEMO_TOKEN
     * (cabecera X-Demo-Token): sin él, cualquiera con el QR podría borrar todo.
     */
    private Respuesta restaurarDemo(Peticion p) throws MakerLabException {
        if (tokenDemo == null) {
            throw new ApiError(403, "DemoDeshabilitado",
                    "El modo demostración no está activado en este servidor (falta la variable DEMO_TOKEN).");
        }
        long ahora = System.currentTimeMillis();
        if (ahora - ventanaToken.get() > 60_000) {
            ventanaToken.set(ahora);
            fallosToken.set(0);
        }
        if (fallosToken.get() >= 8) {
            throw new ApiError(429, "DemasiadosIntentos", "Demasiados intentos con un código incorrecto. Espere un minuto.");
        }
        String enviado = p.cabecera("X-Demo-Token");
        boolean coincide = enviado != null && MessageDigest.isEqual(enviado.getBytes(StandardCharsets.UTF_8),
                tokenDemo.getBytes(StandardCharsets.UTF_8));
        if (!coincide) {
            fallosToken.incrementAndGet();
            throw new ApiError(403, "TokenIncorrecto", "Código de demostración incorrecto.");
        }
        boolean escenario = p.booleano("escenario");
        String mensaje = almacen.restaurarDemo(escenario);
        System.out.println("[demo] Datos restaurados (escenario=" + escenario + ").");
        return ok(Json.mapa("ok", true, "mensaje", mensaje));
    }
}
