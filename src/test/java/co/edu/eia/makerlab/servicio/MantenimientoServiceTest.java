package co.edu.eia.makerlab.servicio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.eia.makerlab.app.DatosDemo;
import co.edu.eia.makerlab.excepcion.EquipoNoDisponibleException;
import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.Prioridad;
import co.edu.eia.makerlab.modelo.Tecnico;
import co.edu.eia.makerlab.modelo.TicketMantenimiento;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;

class MantenimientoServiceTest {

    private MantenimientoService servicio;
    private RegistroService registro;
    private Tecnico carlos;
    private Equipo impresora;
    private Equipo cortadora;

    @BeforeEach
    void preparar() throws MakerLabException {
        CatalogoMakerLab.getInstancia().reiniciar();
        DatosDemo.cargarDatosBase();
        servicio = new MantenimientoService();
        registro = new RegistroService();
        carlos = (Tecnico) registro.buscarUsuario("U-030");
        impresora = registro.buscarEquipo("EQ-101");
        cortadora = registro.buscarEquipo("EQ-201");
    }

    private TicketMantenimiento ticket(String id, Equipo equipo, int hora, int minuto) throws MakerLabException {
        return servicio.crearTicket(id, Prioridad.ALTA, "Falla", equipo, LocalDate.of(2026, 9, 9),
                LocalTime.of(hora, minuto));
    }

    @Test
    void crearTicketBloqueaElEquipoYAvisaAQuienTieneReserva() throws MakerLabException {
        Usuario juanPablo = registro.buscarUsuario("U-010");
        ReservaService reservas = new ReservaService();
        reservas.crearReserva("R-231", juanPablo, impresora, LocalDate.of(2026, 9, 11), LocalTime.of(10, 0),
                LocalTime.of(12, 0), "Prueba");

        ticket("TK-001", impresora, 8, 0);

        assertEquals(EstadoEquipo.EN_MANTENIMIENTO, impresora.getEstado());
        assertEquals(1, juanPablo.getNotificaciones().size());
        assertThrows(EquipoNoDisponibleException.class, () -> reservas.crearReserva("R-250", juanPablo, impresora,
                LocalDate.of(2026, 9, 12), LocalTime.of(8, 0), LocalTime.of(9, 0), "Prueba"));
    }

    @Test
    void noSeCierraSinAccionRegistrada() throws MakerLabException {
        TicketMantenimiento tk = ticket("TK-001", impresora, 8, 0);
        servicio.asignarTecnico(tk, carlos);
        assertThrows(MakerLabException.class, () -> tk.cerrar(EstadoEquipo.OPERATIVO));
    }

    @Test
    void cierreCorrectoDejaElEquipoOperativoYConservaLosDatos() throws MakerLabException {
        TicketMantenimiento tk = ticket("TK-001", impresora, 8, 0);
        servicio.asignarTecnico(tk, carlos);
        tk.registrarAccion("Limpieza de boquilla");
        tk.cerrar(EstadoEquipo.OPERATIVO);

        assertTrue(tk.isCerrado());
        assertEquals(EstadoEquipo.OPERATIVO, impresora.getEstado());
        assertEquals(carlos, tk.getTecnicoAsignado());
        assertEquals("Limpieza de boquilla", tk.getAccionRealizada());
        assertEquals(4, tk.getHistorial().size());
    }

    @Test
    void tecnicoNoPuedeTenerDosMantenimientosCruzados() throws MakerLabException {
        TicketMantenimiento tk1 = ticket("TK-001", impresora, 8, 0); // 08:00 a 09:00
        TicketMantenimiento tk2 = ticket("TK-002", cortadora, 8, 30); // 08:30 a 10:30
        servicio.asignarTecnico(tk1, carlos);
        assertThrows(MakerLabException.class, () -> servicio.asignarTecnico(tk2, carlos));
    }

    @Test
    void mantenimientoQueEmpiezaCuandoTerminaElOtroSePermite() throws MakerLabException {
        TicketMantenimiento tk1 = ticket("TK-001", impresora, 8, 0); // 08:00 a 09:00
        TicketMantenimiento tk2 = ticket("TK-002", cortadora, 9, 0); // 09:00 a 11:00
        servicio.asignarTecnico(tk1, carlos);
        assertDoesNotThrow(() -> servicio.asignarTecnico(tk2, carlos));
    }

    @Test
    void ticketSinIdRecibeElSiguienteDeLaDemostracion() throws MakerLabException {
        DatosDemo.ejecutarEscenario(); // deja TK-001 y TK-002
        Equipo estacion = registro.buscarEquipo("EQ-202");
        TicketMantenimiento tk = servicio.crearTicket(Prioridad.BAJA, "Falla", estacion, LocalDate.of(2026, 9, 14),
                LocalTime.of(8, 0));
        assertEquals("TK-003", tk.getId());
        assertEquals(EstadoEquipo.EN_MANTENIMIENTO, estacion.getEstado());
    }
}
