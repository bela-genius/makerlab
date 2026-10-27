package co.edu.eia.makerlab.app;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
import co.edu.eia.makerlab.patron.EstrategiaProfesoresPrimero;
import co.edu.eia.makerlab.servicio.ConsultaService;
import co.edu.eia.makerlab.servicio.MantenimientoService;
import co.edu.eia.makerlab.servicio.RegistroService;
import co.edu.eia.makerlab.servicio.ReservaService;

/**
 * Datos y escenario de demostración. Son los mismos objetos y valores de
 * los diagramas de objetos, del README y de sql/inserts_selects_prueba.sql.
 */
public class DatosDemo {

    public static final LocalDate HOY = LocalDate.of(2026, 9, 8);

    /** Usuarios, laboratorios, equipos y certificaciones. */
    public static void cargarDatosBase() throws MakerLabException {
        CatalogoMakerLab.getInstancia().setFechaActual(HOY);
        RegistroService registro = new RegistroService();

        Estudiante juanPablo = new Estudiante("U-010", "Juan Pablo Gallego", "juan.gallego24@eia.edu.co",
                "Ingeniería de Sistemas", 2);
        Estudiante mariaCamila = new Estudiante("U-011", "Maria Camila Ricardo", "maria.ricardo24@eia.edu.co",
                "Ingeniería de Sistemas", 2);
        Estudiante samuel = new Estudiante("U-012", "Samuel Pérez", "samuel.perez@eia.edu.co",
                "Ingeniería de Sistemas", 2);
        Estudiante isabella = new Estudiante("U-013", "Isabella Navarro", "isabella.navarro@eia.edu.co",
                "Ingeniería de Sistemas", 2);
        Profesor johan = new Profesor("U-020", "Johan Gabriel Vélez", "johan.velez@eia.edu.co",
                "Ingeniería de Sistemas y Computación");
        Tecnico carlos = new Tecnico("U-030", "Carlos Ramírez", "carlos.ramirez@eia.edu.co",
                "Mantenimiento de equipos de fabricación digital");
        registro.registrarUsuario(juanPablo);
        registro.registrarUsuario(mariaCamila);
        registro.registrarUsuario(samuel);
        registro.registrarUsuario(isabella);
        registro.registrarUsuario(johan);
        registro.registrarUsuario(carlos);

        Laboratorio lab1 = new Laboratorio("LAB-01", "Laboratorio de Prototipado", "Bloque 19 - Piso 2",
                LocalTime.of(7, 0), LocalTime.of(19, 0));
        Laboratorio lab2 = new Laboratorio("LAB-02", "Laboratorio de Electrónica", "Bloque 20 - Piso 1",
                LocalTime.of(8, 0), LocalTime.of(18, 0));
        registro.registrarLaboratorio(lab1);
        registro.registrarLaboratorio(lab2);

        registro.registrarEquipo(EquipoFactory.crear(TipoEquipo.IMPRESORA_3D, "EQ-101", "Creality K1", "MEDIO", lab1,
                "FDM", "PLA/PETG"));
        registro.registrarEquipo(EquipoFactory.crear(TipoEquipo.CORTADORA_LASER, "EQ-201", "Trotec Speedy 100", "ALTO",
                lab2, "60", "610x305 mm"));
        registro.registrarEquipo(EquipoFactory.crear(TipoEquipo.ESTACION_ELECTRONICA, "EQ-202",
                "Estación Hakko FX-888D", "BAJO", lab2, "24.0", "cautín, multímetro, osciloscopio"));

        registro.registrarCertificacion(new Certificacion("C-045", LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 12, 15), juanPablo, TipoEquipo.IMPRESORA_3D));
        registro.registrarCertificacion(new Certificacion("C-046", LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 12, 15), mariaCamila, TipoEquipo.IMPRESORA_3D));
        registro.registrarCertificacion(new Certificacion("C-047", LocalDate.of(2025, 8, 1),
                LocalDate.of(2026, 8, 1), mariaCamila, TipoEquipo.ESTACION_ELECTRONICA));
        registro.registrarCertificacion(new Certificacion("C-048", LocalDate.of(2025, 9, 30),
                LocalDate.of(2026, 9, 30), samuel, TipoEquipo.CORTADORA_LASER));
        registro.registrarCertificacion(new Certificacion("C-049", LocalDate.of(2026, 3, 1),
                LocalDate.of(2027, 3, 1), isabella, TipoEquipo.ESTACION_ELECTRONICA));
        registro.registrarCertificacion(new Certificacion("C-050", LocalDate.of(2026, 3, 1),
                LocalDate.of(2027, 3, 1), johan, TipoEquipo.IMPRESORA_3D));
    }

    /** Recorre todos los flujos obligatorios. Los errores esperados se atrapan y se imprimen. */
    public static void ejecutarEscenario() throws MakerLabException {
        RegistroService registro = new RegistroService();
        ReservaService reservas = new ReservaService();
        MantenimientoService mantenimiento = new MantenimientoService();
        ConsultaService consultas = new ConsultaService();
        CatalogoMakerLab catalogo = CatalogoMakerLab.getInstancia();
        if (catalogo.getReservas().existe("R-230")) {
            System.out.println("La demostracion ya se ejecuto: la reserva R-230 ya existe.");
            return;
        }

        Usuario juanPablo = registro.buscarUsuario("U-010");
        Usuario mariaCamila = registro.buscarUsuario("U-011");
        Usuario samuel = registro.buscarUsuario("U-012");
        Usuario isabella = registro.buscarUsuario("U-013");
        Usuario johan = registro.buscarUsuario("U-020");
        Tecnico carlos = (Tecnico) registro.buscarUsuario("U-030");
        Equipo impresora = registro.buscarEquipo("EQ-101");
        Equipo cortadora = registro.buscarEquipo("EQ-201");
        Equipo estacion = registro.buscarEquipo("EQ-202");

        titulo("1. Polimorfismo: usuarios y equipos");
        for (Usuario u : catalogo.getUsuarios().listar()) {
            u.mostrarDatos();
        }
        for (Laboratorio lab : catalogo.getLaboratorios().listar()) {
            lab.mostrarEquipos();
        }

        titulo("2. Reservas validas y sesiones de uso");
        Reserva r230 = reservas.crearReserva("R-230", juanPablo, impresora, dia(10), hora(14), hora(16),
                "Impresion de pieza para proyecto de curso");
        System.out.println("Confirmada: " + r230);
        SesionUso s088 = r230.abrirSesion("S-088", LocalTime.of(14, 5));
        s088.registrarConsumible("120g PLA");
        s088.cerrar(LocalTime.of(15, 50), "Impresion exitosa, sin incidentes");
        System.out.println("Sesion S-088 cerrada: " + s088.getDuracionMinutos() + " minutos");
        try {
            s088.cerrar(LocalTime.of(16, 0), "Segundo cierre");
        } catch (MakerLabException e) {
            System.out.println("Rechazado: " + e.getMessage());
        }
        Reserva r231 = reservas.crearReserva("R-231", juanPablo, impresora, dia(11), hora(10), hora(12),
                "Segunda pieza del proyecto");
        System.out.println("Confirmada: " + r231);
        Reserva r238 = reservas.crearReserva("R-238", isabella, estacion, dia(9), hora(8), hora(10),
                "Preparacion de practica de laboratorio");
        System.out.println("Confirmada: " + r238);
        SesionUso s089 = r238.abrirSesion("S-089", LocalTime.of(8, 10));
        s089.registrarConsumible("5g estano");
        s089.registrarIncidente("Cautin con temperatura inestable");
        s089.cerrar(LocalTime.of(9, 40), "Se reporto el cautin al tecnico");

        titulo("3. Reservas rechazadas");
        intentar(reservas, "R-232", mariaCamila, impresora, dia(11), 10, 12, "cruce de horario");
        intentar(reservas, "R-233", johan, impresora, dia(11), 10, 12, "cruce de horario");
        intentar(reservas, "R-234", mariaCamila, impresora, dia(11), 10, 12, "solicitud repetida");
        intentar(reservas, "R-235", mariaCamila, estacion, dia(12), 9, 10, "certificacion vencida");
        intentar(reservas, "R-236", juanPablo, impresora, dia(12), 18, 20, "fuera del horario");
        cortadora.setEstado(EstadoEquipo.FUERA_DE_SERVICIO);
        intentar(reservas, "R-237", samuel, cortadora, dia(12), 9, 11, "equipo no operativo");
        System.out.println("Lista de espera: " + catalogo.getListaEspera().listar());

        titulo("4. Mantenimiento");
        TicketMantenimiento tk1 = mantenimiento.crearTicket("TK-001", Prioridad.ALTA, "Boquilla obstruida", impresora,
                dia(9), hora(8));
        TicketMantenimiento tk2 = mantenimiento.crearTicket("TK-002", Prioridad.MEDIA, "Tubo laser sin potencia",
                cortadora, dia(9), LocalTime.of(8, 30));
        mantenimiento.asignarTecnico(tk1, carlos);
        System.out.println("TK-001 asignado a " + carlos.getNombre());
        try {
            mantenimiento.asignarTecnico(tk2, carlos);
        } catch (MakerLabException e) {
            System.out.println("TK-002 rechazado: " + e.getMessage());
        }
        try {
            tk1.cerrar(EstadoEquipo.OPERATIVO);
        } catch (MakerLabException e) {
            System.out.println("Cierre rechazado: " + e.getMessage());
        }
        tk1.registrarAccion("Limpieza de boquilla y calibracion de cama");
        tk1.cerrar(EstadoEquipo.OPERATIVO);
        for (TicketMantenimiento.Cambio cambio : tk1.getHistorial()) {
            System.out.println("  " + cambio);
        }

        titulo("5. Cancelacion y lista de espera (estrategia: profesores primero)");
        catalogo.setEstrategiaEspera(new EstrategiaProfesoresPrimero());
        SolicitudEspera siguiente = reservas.cancelarReserva(r231);
        System.out.println("R-231 cancelada. Se avisa a: " + siguiente);

        titulo("6. Consultas");
        Laboratorio lab1 = registro.buscarLaboratorio("LAB-01");
        System.out.println("Libres en LAB-01 el 2026-09-11 10:00-12:00: "
                + codigos(consultas.consultarDisponibilidad(lab1, dia(11), hora(10), hora(12))));
        System.out.println("Reservas de Juan Pablo: " + consultas.consultarReservasDe(juanPablo));
        System.out.println("Fuera de servicio: " + codigos(consultas.consultarFueraDeServicio()));
        for (Certificacion c : consultas.consultarPorVencer(30)) {
            System.out.println("Vence en 30 dias o menos: " + c.getId() + " (" + c.getFechaVencimiento() + ")");
        }
        for (TicketMantenimiento t : consultas.consultarTicketsPorPrioridad(Prioridad.ALTA)) {
            System.out.println("Ticket ALTA: " + t.getId() + (t.isCerrado() ? " (cerrado)" : " (abierto)"));
        }

        titulo("7. Indicadores");
        mostrarIndicadores(consultas);

        titulo("8. Notificaciones");
        for (Usuario u : catalogo.getUsuarios().listar()) {
            System.out.println(u.getNombre() + ": " + u.getNotificaciones());
        }
    }

    public static void mostrarIndicadores(ConsultaService consultas) {
        System.out.println("Horas de uso por tipo: " + consultas.horasDeUsoPorTipo());
        System.out.println("Incidentes: " + consultas.cantidadIncidentes());
        System.out.println("Tasa de cancelacion: " + Math.round(consultas.tasaCancelacion() * 1000) / 10.0 + "%");
        System.out.println("Tickets abiertos por tecnico: " + consultas.cargaMantenimiento());
    }

    private static void intentar(ReservaService reservas, String id, Usuario usuario, Equipo equipo, LocalDate fecha,
            int inicio, int fin, String caso) {
        try {
            reservas.crearReserva(id, usuario, equipo, fecha, hora(inicio), hora(fin), "Prueba de " + caso);
            System.out.println(id + " (" + caso + "): confirmada");
        } catch (MakerLabException e) {
            System.out.println(id + " (" + caso + "): " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String codigos(List<Equipo> equipos) {
        String texto = "";
        for (Equipo e : equipos) {
            texto += e.getCodigo() + " ";
        }
        return "[" + texto.trim() + "]";
    }

    private static LocalDate dia(int dia) {
        return LocalDate.of(2026, 9, dia);
    }

    private static LocalTime hora(int hora) {
        return LocalTime.of(hora, 0);
    }

    private static void titulo(String texto) {
        System.out.println("\n=== " + texto + " ===");
    }
}
