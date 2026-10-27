package co.edu.eia.makerlab.app;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
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
import co.edu.eia.makerlab.servicio.ConsultaService;
import co.edu.eia.makerlab.servicio.MantenimientoService;
import co.edu.eia.makerlab.servicio.RegistroService;
import co.edu.eia.makerlab.servicio.ReservaService;

/** Menú de consola para ejecutar los flujos principales sin tocar el código. */
public class MenuConsola {

    private Scanner teclado;
    private RegistroService registro;
    private ReservaService reservas;
    private MantenimientoService mantenimiento;
    private ConsultaService consultas;

    public MenuConsola(Scanner teclado) {
        this.teclado = teclado;
        this.registro = new RegistroService();
        this.reservas = new ReservaService();
        this.mantenimiento = new MantenimientoService();
        this.consultas = new ConsultaService();
    }

    public void iniciar() {
        int opcion = -1;
        while (opcion != 0) {
            String modo = Arranque.hayBaseDeDatos() ? "PostgreSQL" : "en memoria";
            System.out.println("\n===== MakerLab EIA (hoy: " + CatalogoMakerLab.getInstancia().getFechaActual()
                    + ", datos: " + modo + ") =====");
            System.out.println(" 1. Ver usuarios y equipos     2. Registrar");
            System.out.println(" 3. Crear reserva              4. Cancelar reserva");
            System.out.println(" 5. Abrir sesion de uso        6. Cerrar sesion de uso");
            System.out.println(" 7. Crear ticket               8. Asignar tecnico");
            System.out.println(" 9. Cerrar ticket             10. Consultas");
            System.out.println("11. Indicadores               12. Cambiar estrategia de espera");
            System.out.println("13. Ver notificaciones        14. Demostracion guiada");
            System.out.println(" 0. Salir");
            try {
                opcion = Integer.parseInt(leer("Opcion: "));
                ejecutar(opcion);
                if (opcion != 0) {
                    Arranque.guardar(); // si hay base de datos, guarda los cambios
                }
            } catch (MakerLabException e) {
                System.out.println("No se pudo: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                // También atrapa NumberFormatException (número mal escrito).
                System.out.println("Dato invalido: " + e.getMessage());
            } catch (DateTimeParseException e) {
                System.out.println("Fecha u hora invalida. Use AAAA-MM-DD y HH:mm.");
            }
        }
        System.out.println("Hasta luego.");
    }

    private void ejecutar(int opcion) throws MakerLabException {
        switch (opcion) {
            case 1 -> verUsuariosYEquipos();
            case 2 -> registrar();
            case 3 -> crearReserva();
            case 4 -> cancelarReserva();
            case 5 -> abrirSesion();
            case 6 -> cerrarSesion();
            case 7 -> crearTicket();
            case 8 -> asignarTecnico();
            case 9 -> cerrarTicket();
            case 10 -> consultar();
            case 11 -> DatosDemo.mostrarIndicadores(consultas);
            case 12 -> cambiarEstrategia();
            case 13 -> verNotificaciones();
            case 14 -> DatosDemo.ejecutarEscenario();
            case 0 -> { }
            default -> System.out.println("Opcion no valida.");
        }
    }

    private void verUsuariosYEquipos() {
        for (Usuario u : CatalogoMakerLab.getInstancia().getUsuarios().listar()) {
            u.mostrarDatos();
        }
        for (Laboratorio lab : CatalogoMakerLab.getInstancia().getLaboratorios().listar()) {
            lab.mostrarEquipos();
        }
    }

    private void registrar() throws MakerLabException {
        String tipo = leer("Registrar (1 usuario, 2 laboratorio, 3 equipo, 4 certificacion): ");
        String id = null; // lo genera el sistema en cada caso
        if (tipo.equals("1")) {
            String clase = leer("Tipo (1 estudiante, 2 profesor, 3 tecnico): ");
            String nombre = leer("Nombre: ");
            String correo = leer("Correo: ");
            id = registro.siguienteIdUsuario();
            Usuario usuario;
            if (clase.equals("1")) {
                usuario = new Estudiante(id, nombre, correo, leer("Carrera: "), Integer.parseInt(leer("Semestre: ")));
            } else if (clase.equals("2")) {
                usuario = new Profesor(id, nombre, correo, leer("Departamento: "));
            } else {
                usuario = new Tecnico(id, nombre, correo, leer("Especialidad: "));
            }
            registro.registrarUsuario(usuario);
        } else if (tipo.equals("2")) {
            String nombre = leer("Nombre: ");
            String ubicacion = leer("Ubicacion: ");
            LocalTime apertura = leerHora("Hora apertura (HH:mm): ");
            LocalTime cierre = leerHora("Hora cierre (HH:mm): ");
            id = registro.siguienteIdLaboratorio();
            registro.registrarLaboratorio(new Laboratorio(id, nombre, ubicacion, apertura, cierre));
        } else if (tipo.equals("3")) {
            TipoEquipo tipoEquipo = TipoEquipo.valueOf(leer("Tipo (IMPRESORA_3D, CORTADORA_LASER, ESTACION_ELECTRONICA): ").toUpperCase());
            String nombre = leer("Nombre: ");
            String riesgo = leer("Nivel de riesgo (BAJO, MEDIO, ALTO): ");
            Laboratorio lab = registro.buscarLaboratorio(leer("Codigo del laboratorio: "));
            String dato1 = leer("Tecnologia / potencia en W / voltaje maximo: ");
            String dato2 = leer("Material / area de trabajo / herramientas: ");
            id = registro.siguienteIdEquipo();
            registro.registrarEquipo(EquipoFactory.crear(tipoEquipo, id, nombre, riesgo, lab, dato1, dato2));
        } else if (tipo.equals("4")) {
            Usuario usuario = registro.buscarUsuario(leer("Id del usuario: "));
            TipoEquipo tipoEquipo = TipoEquipo.valueOf(leer("Tipo de equipo autorizado: ").toUpperCase());
            LocalDate emision = leerFecha("Emision (AAAA-MM-DD): ");
            LocalDate vencimiento = leerFecha("Vencimiento (AAAA-MM-DD): ");
            id = registro.siguienteIdCertificacion();
            registro.registrarCertificacion(new Certificacion(id, emision, vencimiento, usuario, tipoEquipo));
        }
        if (id == null) {
            System.out.println("Opcion no valida.");
        } else {
            System.out.println("Registrado con id " + id + ".");
        }
    }

    private void crearReserva() throws MakerLabException {
        Usuario usuario = registro.buscarUsuario(leer("Id del usuario: "));
        Equipo equipo = registro.buscarEquipo(leer("Codigo del equipo: "));
        Reserva reserva = reservas.crearReserva(usuario, equipo, leerFecha("Fecha (AAAA-MM-DD): "),
                leerHora("Hora inicio (HH:mm): "), leerHora("Hora fin (HH:mm): "), leer("Proposito: "));
        System.out.println("Reserva " + reserva.getId() + " confirmada: " + reserva);
    }

    private void cancelarReserva() throws MakerLabException {
        SolicitudEspera siguiente = reservas.cancelarReserva(reservas.buscarReserva(leer("Id de la reserva: ")));
        System.out.println("Cancelada." + (siguiente != null ? " Se aviso a " + siguiente : ""));
    }

    private void abrirSesion() throws MakerLabException {
        Reserva reserva = reservas.buscarReserva(leer("Id de la reserva: "));
        SesionUso sesion = reservas.abrirSesion(reserva, leerHora("Hora real de inicio (HH:mm): "));
        System.out.println("Sesion " + sesion.getId() + " abierta.");
    }

    private void cerrarSesion() throws MakerLabException {
        Reserva reserva = reservas.buscarReserva(leer("Id de la reserva: "));
        SesionUso sesion = reserva.getSesionUso();
        if (sesion == null) {
            System.out.println("La reserva no tiene sesion.");
            return;
        }
        String consumible = leer("Consumible usado (Enter si ninguno): ");
        if (!consumible.isEmpty()) {
            sesion.registrarConsumible(consumible);
        }
        String incidente = leer("Incidente (Enter si ninguno): ");
        if (!incidente.isEmpty()) {
            sesion.registrarIncidente(incidente);
        }
        sesion.cerrar(leerHora("Hora real de fin (HH:mm): "), leer("Observaciones: "));
        System.out.println("Sesion cerrada: " + sesion.getDuracionMinutos() + " minutos.");
    }

    private void crearTicket() throws MakerLabException {
        Prioridad prioridad = Prioridad.valueOf(leer("Prioridad (BAJA, MEDIA, ALTA): ").toUpperCase());
        String descripcion = leer("Descripcion: ");
        Equipo equipo = registro.buscarEquipo(leer("Codigo del equipo: "));
        TicketMantenimiento ticket = mantenimiento.crearTicket(prioridad, descripcion, equipo,
                leerFecha("Fecha (AAAA-MM-DD): "), leerHora("Hora (HH:mm): "));
        System.out.println("Ticket " + ticket.getId() + " creado. " + equipo.getCodigo() + " queda "
                + equipo.getEstado());
    }

    private void asignarTecnico() throws MakerLabException {
        TicketMantenimiento ticket = mantenimiento.buscarTicket(leer("Id del ticket: "));
        Usuario usuario = registro.buscarUsuario(leer("Id del tecnico: "));
        if (!(usuario instanceof Tecnico)) {
            System.out.println(usuario.getNombre() + " no es tecnico.");
            return;
        }
        mantenimiento.asignarTecnico(ticket, (Tecnico) usuario);
        System.out.println("Tecnico asignado.");
    }

    private void cerrarTicket() throws MakerLabException {
        TicketMantenimiento ticket = mantenimiento.buscarTicket(leer("Id del ticket: "));
        ticket.registrarAccion(leer("Accion realizada: "));
        ticket.cerrar(EstadoEquipo.valueOf(leer("Estado final (OPERATIVO o FUERA_DE_SERVICIO): ").toUpperCase()));
        for (TicketMantenimiento.Cambio cambio : ticket.getHistorial()) {
            System.out.println("  " + cambio);
        }
    }

    private void consultar() throws MakerLabException {
        String tipo = leer("1 disponibilidad, 2 reservas de un usuario, 3 fuera de servicio, "
                + "4 certificaciones por vencer, 5 tickets por prioridad, 6 lista de espera: ");
        if (tipo.equals("1")) {
            Laboratorio lab = registro.buscarLaboratorio(leer("Codigo del laboratorio: "));
            for (Equipo e : consultas.consultarDisponibilidad(lab, leerFecha("Fecha (AAAA-MM-DD): "),
                    leerHora("Desde (HH:mm): "), leerHora("Hasta (HH:mm): "))) {
                e.mostrarDetalles();
            }
        } else if (tipo.equals("2")) {
            System.out.println(consultas.consultarReservasDe(registro.buscarUsuario(leer("Id del usuario: "))));
        } else if (tipo.equals("3")) {
            for (Equipo e : consultas.consultarFueraDeServicio()) {
                e.mostrarDetalles();
            }
        } else if (tipo.equals("4")) {
            for (Certificacion c : consultas.consultarPorVencer(Integer.parseInt(leer("Dias: ")))) {
                System.out.println(c.getId() + " de " + c.getUsuario().getNombre() + " vence " + c.getFechaVencimiento());
            }
        } else if (tipo.equals("5")) {
            Prioridad p = Prioridad.valueOf(leer("Prioridad (BAJA, MEDIA, ALTA): ").toUpperCase());
            for (TicketMantenimiento t : consultas.consultarTicketsPorPrioridad(p)) {
                System.out.println(t.getId() + " " + t.getEquipo().getCodigo() + ": " + t.getDescripcion()
                        + (t.isCerrado() ? " (cerrado)" : " (abierto)"));
            }
        } else if (tipo.equals("6")) {
            System.out.println(CatalogoMakerLab.getInstancia().getListaEspera().listar());
        }
    }

    private void cambiarEstrategia() {
        String opcion = leer("1 orden de llegada, 2 profesores primero: ");
        if (opcion.equals("2")) {
            CatalogoMakerLab.getInstancia().setEstrategiaEspera(new EstrategiaProfesoresPrimero());
        } else {
            CatalogoMakerLab.getInstancia().setEstrategiaEspera(new EstrategiaFifo());
        }
        System.out.println("Estrategia cambiada.");
    }

    private void verNotificaciones() throws MakerLabException {
        Usuario usuario = registro.buscarUsuario(leer("Id del usuario: "));
        for (String n : usuario.getNotificaciones()) {
            System.out.println("- " + n);
        }
    }

    private String leer(String mensaje) {
        System.out.print(mensaje);
        return teclado.nextLine().trim();
    }

    private LocalDate leerFecha(String mensaje) {
        return LocalDate.parse(leer(mensaje));
    }

    private LocalTime leerHora(String mensaje) {
        return LocalTime.parse(leer(mensaje));
    }
}
