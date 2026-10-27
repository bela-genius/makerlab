package co.edu.eia.makerlab.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.eia.makerlab.app.DatosDemo;
import co.edu.eia.makerlab.excepcion.CertificacionInvalidaException;
import co.edu.eia.makerlab.excepcion.CruceHorarioException;
import co.edu.eia.makerlab.excepcion.EquipoNoDisponibleException;
import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.EstadoReserva;
import co.edu.eia.makerlab.modelo.Reserva;
import co.edu.eia.makerlab.modelo.SesionUso;
import co.edu.eia.makerlab.modelo.SolicitudEspera;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.patron.EstrategiaProfesoresPrimero;

/** Usa los mismos datos de la demostración (DatosDemo). */
class ReservaServiceTest {

    private ReservaService servicio;
    private Usuario juanPablo;
    private Usuario mariaCamila;
    private Usuario johan;
    private Equipo impresora;

    @BeforeEach
    void preparar() throws MakerLabException {
        CatalogoMakerLab.getInstancia().reiniciar();
        DatosDemo.cargarDatosBase();
        servicio = new ReservaService();
        RegistroService registro = new RegistroService();
        juanPablo = registro.buscarUsuario("U-010");
        mariaCamila = registro.buscarUsuario("U-011");
        johan = registro.buscarUsuario("U-020"); // profesor
        impresora = registro.buscarEquipo("EQ-101");
    }

    /** Atajo: reserva el 2026-09-10 entre dos horas en punto. */
    private Reserva reservar(String id, Usuario usuario, Equipo equipo, int inicio, int fin) throws MakerLabException {
        return servicio.crearReserva(id, usuario, equipo, LocalDate.of(2026, 9, 10), LocalTime.of(inicio, 0),
                LocalTime.of(fin, 0), "Prueba");
    }

    @Test
    void reservaValidaQuedaConfirmada() throws MakerLabException {
        Reserva r = reservar("R-230", juanPablo, impresora, 14, 16);
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
    }

    @Test
    void cruceLanzaExcepcionYVaALaListaDeEspera() throws MakerLabException {
        reservar("R-230", juanPablo, impresora, 14, 16);
        assertThrows(CruceHorarioException.class, () -> reservar("R-232", mariaCamila, impresora, 15, 17));
        assertEquals(1, CatalogoMakerLab.getInstancia().getListaEspera().tamano());
    }

    @Test
    void solicitudRepetidaNoSeAgregaDosVeces() throws MakerLabException {
        reservar("R-230", juanPablo, impresora, 14, 16);
        assertThrows(CruceHorarioException.class, () -> reservar("R-232", mariaCamila, impresora, 14, 16));
        assertThrows(CruceHorarioException.class, () -> reservar("R-234", mariaCamila, impresora, 14, 16));
        assertEquals(1, CatalogoMakerLab.getInstancia().getListaEspera().tamano());
    }

    @Test
    void franjasQueSoloSeTocanNoSeCruzan() throws MakerLabException {
        reservar("R-230", juanPablo, impresora, 14, 16);
        Reserva siguiente = reservar("R-240", mariaCamila, impresora, 16, 18);
        assertEquals(EstadoReserva.CONFIRMADA, siguiente.getEstado());
    }

    @Test
    void certificacionVencidaLanzaExcepcion() throws MakerLabException {
        Equipo estacion = new RegistroService().buscarEquipo("EQ-202");
        assertThrows(CertificacionInvalidaException.class, () -> reservar("R-235", mariaCamila, estacion, 9, 10));
    }

    @Test
    void equipoNoOperativoLanzaExcepcion() {
        impresora.setEstado(EstadoEquipo.FUERA_DE_SERVICIO);
        assertThrows(EquipoNoDisponibleException.class, () -> reservar("R-237", juanPablo, impresora, 9, 11));
    }

    @Test
    void reservaFueraDelHorarioLanzaExcepcion() {
        // LAB-01 cierra a las 19:00
        assertThrows(MakerLabException.class, () -> reservar("R-236", juanPablo, impresora, 18, 20));
    }

    @Test
    void reservaQueTerminaJustoAlCierreSeAcepta() throws MakerLabException {
        Reserva r = reservar("R-241", juanPablo, impresora, 17, 19);
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
    }

    @Test
    void idRepetidoLanzaExcepcion() throws MakerLabException {
        reservar("R-230", juanPablo, impresora, 8, 9);
        assertThrows(MakerLabException.class, () -> reservar("R-230", juanPablo, impresora, 10, 11));
    }

    @Test
    void alCancelarSeAvisaAlProfesorSiLaEstrategiaLoPrioriza() throws MakerLabException {
        Reserva r = reservar("R-231", juanPablo, impresora, 10, 12);
        assertThrows(CruceHorarioException.class, () -> reservar("R-232", mariaCamila, impresora, 10, 12));
        assertThrows(CruceHorarioException.class, () -> reservar("R-233", johan, impresora, 10, 12));
        CatalogoMakerLab.getInstancia().setEstrategiaEspera(new EstrategiaProfesoresPrimero());

        SolicitudEspera elegida = servicio.cancelarReserva(r);

        assertEquals(johan, elegida.getUsuario());
        assertEquals(1, johan.getNotificaciones().size());
    }

    @Test
    void alCancelarSinNadieEsperandoNoSeEligeANadie() throws MakerLabException {
        Reserva r = reservar("R-231", juanPablo, impresora, 10, 12);
        assertNull(servicio.cancelarReserva(r));
    }

    @Test
    void reservaSinIdRecibeElSiguienteDeLaDemostracion() throws MakerLabException {
        DatosDemo.ejecutarEscenario(); // deja R-230, R-231 y R-238
        Reserva r = servicio.crearReserva(juanPablo, impresora, LocalDate.of(2026, 9, 14), LocalTime.of(10, 0),
                LocalTime.of(12, 0), "Prueba");
        assertEquals("R-239", r.getId());
    }

    @Test
    void sesionSinIdRecibeElSiguienteDeLaDemostracion() throws MakerLabException {
        DatosDemo.ejecutarEscenario(); // usa S-088 y S-089
        Reserva r = servicio.crearReserva(juanPablo, impresora, LocalDate.of(2026, 9, 14), LocalTime.of(10, 0),
                LocalTime.of(12, 0), "Prueba");
        SesionUso sesion = servicio.abrirSesion(r, LocalTime.of(10, 5));
        assertEquals("S-090", sesion.getId());
        assertEquals(sesion, r.getSesionUso());
    }
}
