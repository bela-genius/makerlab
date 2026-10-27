package co.edu.eia.makerlab.patron;

import java.util.List;

import co.edu.eia.makerlab.modelo.EstrategiaPrioridad;
import co.edu.eia.makerlab.modelo.SolicitudEspera;

/** Estrategia por defecto: el primero que llegó a la lista. */
public class EstrategiaFifo implements EstrategiaPrioridad {

    @Override
    public SolicitudEspera elegir(List<SolicitudEspera> candidatas) {
        // La lista viene en orden de llegada (LinkedHashSet), así que el primero es el más antiguo.
        return candidatas.get(0);
    }
}
