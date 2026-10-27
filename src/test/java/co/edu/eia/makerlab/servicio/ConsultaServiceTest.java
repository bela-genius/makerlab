package co.edu.eia.makerlab.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.eia.makerlab.app.DatosDemo;
import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.Estudiante;
import co.edu.eia.makerlab.modelo.Impresora3D;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.TipoEquipo;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.repositorio.Repositorio;

/** Consultas, indicadores, repositorio genérico y wildcards. */
class ConsultaServiceTest {

    private ConsultaService consultas;

    @BeforeEach
    void preparar() throws MakerLabException {
        CatalogoMakerLab.getInstancia().reiniciar();
        DatosDemo.cargarDatosBase();
        consultas = new ConsultaService();
    }

    @Test
    void indicadoresDeLaDemostracionCompleta() throws MakerLabException {
        DatosDemo.ejecutarEscenario();
        Map<TipoEquipo, Double> horas = consultas.horasDeUsoPorTipo();
        assertEquals(1.75, horas.get(TipoEquipo.IMPRESORA_3D), 0.001);
        assertEquals(1.5, horas.get(TipoEquipo.ESTACION_ELECTRONICA), 0.001);
        assertEquals(1, consultas.cantidadIncidentes());
        assertEquals(1.0 / 3, consultas.tasaCancelacion(), 0.001);
        assertEquals(1, consultas.cargaMantenimiento().get("SIN ASIGNAR"));
    }

    @Test
    void sinReservasLaTasaDeCancelacionEsCero() {
        assertEquals(0, consultas.tasaCancelacion());
    }

    @Test
    void certificacionQueVenceJustoEnElLimiteSeIncluye() {
        // C-048 vence el 2026-09-30
        CatalogoMakerLab.getInstancia().setFechaActual(LocalDate.of(2026, 8, 31)); // faltan 30 dias
        assertEquals(1, consultas.consultarPorVencer(30).size());
        CatalogoMakerLab.getInstancia().setFechaActual(LocalDate.of(2026, 8, 30)); // faltan 31 dias
        assertEquals(0, consultas.consultarPorVencer(30).size());
    }

    @Test
    void disponibilidadExcluyeElEquipoReservadoEnEsaFranja() throws MakerLabException {
        RegistroService registro = new RegistroService();
        Laboratorio lab1 = registro.buscarLaboratorio("LAB-01");
        LocalDate dia = LocalDate.of(2026, 9, 10);
        new ReservaService().crearReserva("R-230", registro.buscarUsuario("U-010"), registro.buscarEquipo("EQ-101"),
                dia, LocalTime.of(14, 0), LocalTime.of(16, 0), "Prueba");

        assertEquals(0, consultas.consultarDisponibilidad(lab1, dia, LocalTime.of(15, 0), LocalTime.of(17, 0)).size());
        assertEquals(1, consultas.consultarDisponibilidad(lab1, dia, LocalTime.of(16, 0), LocalTime.of(18, 0)).size());
    }

    @Test
    void metodoGenericoDevuelveElMismoSubtipo() throws MakerLabException {
        Laboratorio lab1 = new RegistroService().buscarLaboratorio("LAB-01");
        List<Impresora3D> impresoras = new ArrayList<>();
        impresoras.add(new Impresora3D("EQ-901", "A", "BAJO", lab1, "FDM", "PLA"));
        Impresora3D danada = new Impresora3D("EQ-902", "B", "BAJO", lab1, "FDM", "PLA");
        danada.setEstado(EstadoEquipo.FUERA_DE_SERVICIO);
        impresoras.add(danada);

        List<Impresora3D> operativas = consultas.filtrarOperativos(impresoras);

        assertEquals(1, operativas.size());
        assertEquals("FDM", operativas.get(0).getTecnologia()); // sin cast
    }

    @Test
    void wildcardSuperAceptaUnaListaDeObject() throws MakerLabException {
        new RegistroService().buscarEquipo("EQ-201").setEstado(EstadoEquipo.FUERA_DE_SERVICIO);
        List<Object> destino = new ArrayList<>();
        consultas.copiarFueraDeServicio(destino);
        assertEquals(1, destino.size());
    }

    @Test
    void repositorioAceptaListaDeSubtipoYRechazaIdRepetido() throws MakerLabException {
        Repositorio<Usuario> repo = new Repositorio<>();
        List<Estudiante> estudiantes = new ArrayList<>();
        estudiantes.add(new Estudiante("U-1", "Ana", "ana@eia.edu.co", "Sistemas", 1));
        estudiantes.add(new Estudiante("U-2", "Luis", "luis@eia.edu.co", "Sistemas", 1));
        repo.agregarTodos(estudiantes); // List<Estudiante> en un Repositorio<Usuario>: ? extends T
        assertEquals(2, repo.tamano());
        assertThrows(MakerLabException.class,
                () -> repo.agregar(new Estudiante("U-1", "Otra", "otra@eia.edu.co", "Fisica", 3)));
    }
}
