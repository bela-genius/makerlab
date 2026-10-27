package co.edu.eia.makerlab.repositorio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Identificable;

/**
 * Generación de ids. Identificable tiene un solo método, así que cada
 * elemento de prueba es una lambda que devuelve su id.
 */
class RepositorioTest {

    private Repositorio<Identificable> repositorio;

    @BeforeEach
    void preparar() {
        repositorio = new Repositorio<>();
    }

    @Test
    void siguienteIdTomaElNumeroMasAltoYLeSumaUno() throws MakerLabException {
        repositorio.agregar(() -> "R-230");
        repositorio.agregar(() -> "R-238");
        assertEquals("R-239", repositorio.siguienteId("R-", 3));
    }

    @Test
    void repositorioVacioEmpiezaEnUno() {
        assertEquals("R-001", repositorio.siguienteId("R-", 3));
    }

    @Test
    void siguienteIdConDosDigitos() throws MakerLabException {
        repositorio.agregar(() -> "LAB-01");
        repositorio.agregar(() -> "LAB-02");
        assertEquals("LAB-03", repositorio.siguienteId("LAB-", 2));
    }

    @Test
    void siguienteIdIgnoraOtrosPrefijosYNumerosInvalidos() throws MakerLabException {
        repositorio.agregar(() -> "R-005");
        repositorio.agregar(() -> "TK-900");
        repositorio.agregar(() -> "R-abc");
        repositorio.agregar(() -> "R-");
        assertEquals("R-006", repositorio.siguienteId("R-", 3));
        assertEquals("S-001", repositorio.siguienteId("S-", 3));
    }
}
