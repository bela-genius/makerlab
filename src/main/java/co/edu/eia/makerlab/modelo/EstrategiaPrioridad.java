package co.edu.eia.makerlab.modelo;

import java.util.List;

/**
 * Patrón Strategy: forma intercambiable de elegir quién sigue en la lista
 * de espera. Está en modelo por la misma razón que ObservadorEquipo.
 */
public interface EstrategiaPrioridad {

    /** Recibe una lista con al menos un elemento y devuelve el elegido. */
    SolicitudEspera elegir(List<SolicitudEspera> candidatas);
}
