package co.edu.eia.makerlab.modelo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Se usa un Set porque la lista de espera no admite duplicados.
 * LinkedHashSet además guarda el orden de llegada.
 */
public class ListaEspera {

    private Set<SolicitudEspera> solicitudes;
    private int consecutivo;

    public ListaEspera() {
        this.solicitudes = new LinkedHashSet<>();
        this.consecutivo = 0;
    }

    /**
     * Crea y agrega la solicitud. Devuelve false si ya había una igual
     * (misma persona, equipo y franja); en ese caso no se gasta el número.
     */
    public boolean agregar(Usuario usuario, Equipo equipo, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
            LocalDate fechaSolicitud) {
        String id = String.format("SOL-%03d", consecutivo + 1);
        SolicitudEspera solicitud = new SolicitudEspera(id, usuario, equipo, fecha, horaInicio, horaFin, fechaSolicitud);
        boolean agregada = solicitudes.add(solicitud);
        if (agregada) {
            consecutivo++;
        }
        return agregada;
    }

    /**
     * Solo para la carga desde la base de datos: agrega una solicitud que
     * ya tenía id y ajusta el consecutivo para que el siguiente id no se repita.
     */
    public void restaurar(SolicitudEspera solicitud) {
        solicitudes.add(solicitud);
        int numero = Integer.parseInt(solicitud.getId().substring(4)); // "SOL-007" -> 7
        if (numero > consecutivo) {
            consecutivo = numero;
        }
    }

    /**
     * Cuando se libera una franja, busca las solicitudes del mismo equipo y
     * día que se cruzan con ella, deja que la estrategia elija una y la saca
     * de la lista. Devuelve null si nadie esperaba esa franja.
     */
    public SolicitudEspera obtenerSiguiente(Equipo equipo, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
            EstrategiaPrioridad estrategia) {
        List<SolicitudEspera> candidatas = new ArrayList<>();
        for (SolicitudEspera s : solicitudes) {
            boolean seCruza = s.getHoraInicio().isBefore(horaFin) && horaInicio.isBefore(s.getHoraFin());
            if (s.getEquipo() == equipo && s.getFecha().equals(fecha) && seCruza) {
                candidatas.add(s);
            }
        }
        if (candidatas.isEmpty()) {
            return null;
        }
        SolicitudEspera elegida = estrategia.elegir(candidatas);
        solicitudes.remove(elegida);
        return elegida;
    }

    public List<SolicitudEspera> listar() {
        return new ArrayList<>(solicitudes);
    }

    public int tamano() {
        return solicitudes.size();
    }
}
