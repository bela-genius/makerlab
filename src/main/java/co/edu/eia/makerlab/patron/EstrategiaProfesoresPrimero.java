package co.edu.eia.makerlab.patron;

import java.util.List;

import co.edu.eia.makerlab.modelo.EstrategiaPrioridad;
import co.edu.eia.makerlab.modelo.Profesor;
import co.edu.eia.makerlab.modelo.SolicitudEspera;

/** Si hay un profesor esperando, va primero; si no, se atiende por orden de llegada. */
public class EstrategiaProfesoresPrimero implements EstrategiaPrioridad {

    @Override
    public SolicitudEspera elegir(List<SolicitudEspera> candidatas) {
        for (SolicitudEspera s : candidatas) {
            if (s.getUsuario() instanceof Profesor) {
                return s;
            }
        }
        return candidatas.get(0);
    }
}
