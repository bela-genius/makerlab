package co.edu.eia.makerlab.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.patron.EstrategiaFifo;

/** Reglas que viven en las propias clases del modelo, sin servicios. */
class ModeloTest {

    private Usuario juanPablo;
    private Equipo impresora;
    private Reserva reserva;

    @BeforeEach
    void preparar() {
        Laboratorio lab = new Laboratorio("LAB-01", "Prototipado", "Bloque 19", LocalTime.of(7, 0), LocalTime.of(19, 0));
        impresora = new Impresora3D("EQ-101", "Creality K1", "MEDIO", lab, "FDM", "PLA/PETG");
        juanPablo = new Estudiante("U-010", "Juan Pablo Gallego", "juan.gallego24@eia.edu.co", "Sistemas", 2);
        reserva = new Reserva("R-230", LocalDate.of(2026, 9, 10), LocalTime.of(14, 0), LocalTime.of(16, 0),
                "Proyecto", juanPablo, impresora);
    }

    @Test
    void sesionRegistraDuracionEIncidentes() throws MakerLabException {
        SesionUso sesion = reserva.abrirSesion("S-088", LocalTime.of(14, 5));
        sesion.registrarIncidente("Atasco de filamento");
        sesion.cerrar(LocalTime.of(15, 50), "Ok");
        assertEquals(105, sesion.getDuracionMinutos());
        assertEquals(1, sesion.getIncidentesReportados().size());
    }

    @Test
    void sesionNoSeCierraDosVeces() throws MakerLabException {
        SesionUso sesion = reserva.abrirSesion("S-088", LocalTime.of(14, 5));
        sesion.cerrar(LocalTime.of(15, 50), "Ok");
        assertThrows(MakerLabException.class, () -> sesion.cerrar(LocalTime.of(16, 0), "Otra vez"));
    }

    @Test
    void noSeAbreSesionDeUnaReservaCancelada() throws MakerLabException {
        reserva.cancelar();
        assertThrows(MakerLabException.class, () -> reserva.abrirSesion("S-088", LocalTime.of(14, 5)));
    }

    @Test
    void certificacionVigenteHastaElDiaDeVencimiento() {
        Certificacion c = new Certificacion("C-045", LocalDate.of(2026, 2, 10), LocalDate.of(2026, 12, 15), juanPablo,
                TipoEquipo.IMPRESORA_3D);
        assertTrue(c.esVigente(LocalDate.of(2026, 12, 15)));
        assertFalse(c.esVigente(LocalDate.of(2026, 12, 16)));
    }

    @Test
    void listaDeEsperaNoAdmiteDuplicadosYEntregaAlPrimero() {
        ListaEspera lista = new ListaEspera();
        LocalDate dia = LocalDate.of(2026, 9, 11);
        LocalTime diez = LocalTime.of(10, 0);
        LocalTime doce = LocalTime.of(12, 0);
        assertTrue(lista.agregar(juanPablo, impresora, dia, diez, doce, dia));
        assertFalse(lista.agregar(juanPablo, impresora, dia, diez, doce, dia));

        SolicitudEspera siguiente = lista.obtenerSiguiente(impresora, dia, diez, doce, new EstrategiaFifo());

        assertEquals("SOL-001", siguiente.getId());
        assertEquals(0, lista.tamano());
    }
}
