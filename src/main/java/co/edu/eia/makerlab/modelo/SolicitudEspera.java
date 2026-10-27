package co.edu.eia.makerlab.modelo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * equals y hashCode comparan usuario, equipo, fecha y franja (no el id).
 * Así el Set de ListaEspera rechaza solo y sin código extra una solicitud
 * repetida de la misma persona para el mismo equipo y franja.
 */
public class SolicitudEspera {

    private String id;
    private Usuario usuario;
    private Equipo equipo;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private LocalDate fechaSolicitud;

    public SolicitudEspera(String id, Usuario usuario, Equipo equipo, LocalDate fecha, LocalTime horaInicio,
            LocalTime horaFin, LocalDate fechaSolicitud) {
        this.id = id;
        this.usuario = usuario;
        this.equipo = equipo;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.fechaSolicitud = fechaSolicitud;
    }

    public String getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Equipo getEquipo() {
        return equipo;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public LocalDate getFechaSolicitud() {
        return fechaSolicitud;
    }

    @Override
    public boolean equals(Object otro) {
        if (!(otro instanceof SolicitudEspera)) {
            return false;
        }
        SolicitudEspera s = (SolicitudEspera) otro;
        return usuario == s.usuario && equipo == s.equipo && fecha.equals(s.fecha)
                && horaInicio.equals(s.horaInicio) && horaFin.equals(s.horaFin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuario.getId(), equipo.getCodigo(), fecha, horaInicio, horaFin);
    }

    @Override
    public String toString() {
        return id + " (" + usuario.getNombre() + ", " + equipo.getCodigo() + ", " + fecha + " " + horaInicio + "-"
                + horaFin + ")";
    }
}
