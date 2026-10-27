package co.edu.eia.makerlab.modelo;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import co.edu.eia.makerlab.excepcion.MakerLabException;

/**
 * El constructor no es público: solo Reserva (mismo paquete) puede crear
 * sesiones. Así ninguna sesión existe sin su reserva.
 */
public class SesionUso {

    private String id;
    private LocalTime horaInicioReal;
    private LocalTime horaFinReal;
    private List<String> consumiblesUsados;
    private String observaciones;
    private List<String> incidentesReportados;
    private boolean activa;

    SesionUso(String id, LocalTime horaInicioReal) {
        this.id = id;
        this.horaInicioReal = horaInicioReal;
        this.consumiblesUsados = new ArrayList<>();
        this.incidentesReportados = new ArrayList<>();
        this.activa = true;
    }

    public void registrarConsumible(String consumible) throws MakerLabException {
        if (!activa) {
            throw new MakerLabException("La sesion " + id + " ya esta cerrada.");
        }
        consumiblesUsados.add(consumible);
    }

    public void registrarIncidente(String incidente) throws MakerLabException {
        if (!activa) {
            throw new MakerLabException("La sesion " + id + " ya esta cerrada.");
        }
        incidentesReportados.add(incidente);
    }

    /** Regla: una sesión no se puede cerrar dos veces. */
    public void cerrar(LocalTime horaFinReal, String observaciones) throws MakerLabException {
        if (!activa) {
            throw new MakerLabException("La sesion " + id + " ya fue cerrada.");
        }
        if (!horaFinReal.isAfter(horaInicioReal)) {
            throw new MakerLabException("La hora de fin debe ser posterior a " + horaInicioReal + ".");
        }
        this.horaFinReal = horaFinReal;
        this.observaciones = observaciones;
        this.activa = false;
    }

    /** Minutos reales de uso; 0 si la sesión sigue abierta. */
    public long getDuracionMinutos() {
        if (activa) {
            return 0;
        }
        return Duration.between(horaInicioReal, horaFinReal).toMinutes();
    }

    public String getId() {
        return id;
    }

    public LocalTime getHoraInicioReal() {
        return horaInicioReal;
    }

    public LocalTime getHoraFinReal() {
        return horaFinReal;
    }

    public List<String> getConsumiblesUsados() {
        return new ArrayList<>(consumiblesUsados);
    }

    public String getObservaciones() {
        return observaciones;
    }

    public List<String> getIncidentesReportados() {
        return new ArrayList<>(incidentesReportados);
    }

    public boolean isActiva() {
        return activa;
    }
}
