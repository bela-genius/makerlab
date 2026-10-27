package co.edu.eia.makerlab.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import co.edu.eia.makerlab.excepcion.MakerLabException;

/**
 * El técnico se asigna después de crear el ticket (multiplicidad 0..1).
 * Cada paso queda en el historial, hecho con la clase interna Cambio.
 */
public class TicketMantenimiento implements Identificable {

    private String id;
    private Prioridad prioridad;
    private String descripcion;
    private String accionRealizada;
    private boolean cerrado;
    private Equipo equipo;
    private Tecnico tecnicoAsignado;
    private LocalDate fechaProgramada;
    private LocalTime horaProgramada;
    private List<Cambio> historial;

    public TicketMantenimiento(String id, Prioridad prioridad, String descripcion, Equipo equipo,
            LocalDate fechaProgramada, LocalTime horaProgramada) {
        this.id = id;
        this.prioridad = prioridad;
        this.descripcion = descripcion;
        this.equipo = equipo;
        this.fechaProgramada = fechaProgramada;
        this.horaProgramada = horaProgramada;
        this.cerrado = false;
        this.historial = new ArrayList<>();
        historial.add(new Cambio("Ticket creado: " + descripcion));
    }

    public void asignarTecnico(Tecnico tecnico) throws MakerLabException {
        if (cerrado) {
            throw new MakerLabException("El ticket " + id + " ya esta cerrado.");
        }
        this.tecnicoAsignado = tecnico;
        historial.add(new Cambio("Asignado a " + tecnico.getNombre()));
    }

    public void registrarAccion(String accion) throws MakerLabException {
        if (cerrado) {
            throw new MakerLabException("El ticket " + id + " ya esta cerrado.");
        }
        this.accionRealizada = accion;
        historial.add(new Cambio("Accion: " + accion));
    }

    /**
     * Solo se cierra con técnico y acción registrados, y el equipo debe
     * quedar OPERATIVO (reparado) o FUERA_DE_SERVICIO (sin arreglo).
     */
    public void cerrar(EstadoEquipo estadoFinal) throws MakerLabException {
        if (cerrado) {
            throw new MakerLabException("El ticket " + id + " ya esta cerrado.");
        }
        if (tecnicoAsignado == null || accionRealizada == null) {
            throw new MakerLabException("Para cerrar " + id + " falta el tecnico o la accion realizada.");
        }
        if (estadoFinal != EstadoEquipo.OPERATIVO && estadoFinal != EstadoEquipo.FUERA_DE_SERVICIO) {
            throw new MakerLabException("El equipo debe quedar OPERATIVO o FUERA_DE_SERVICIO.");
        }
        cerrado = true;
        historial.add(new Cambio("Cerrado, equipo queda " + estadoFinal));
        equipo.setEstado(estadoFinal);
    }

    /**
     * Solo para la carga desde la base de datos: devuelve el ticket al
     * estado guardado sin repetir las validaciones, que ya se hicieron
     * cuando la operación ocurrió por primera vez.
     */
    public void restaurar(Tecnico tecnico, String accion, boolean estaCerrado, List<String> cambiosGuardados) {
        this.tecnicoAsignado = tecnico;
        this.accionRealizada = accion;
        this.cerrado = estaCerrado;
        historial.clear();
        for (String descripcionCambio : cambiosGuardados) {
            historial.add(new Cambio(descripcionCambio));
        }
    }

    public LocalDateTime getInicio() {
        return LocalDateTime.of(fechaProgramada, horaProgramada);
    }

    public LocalDateTime getFin() {
        return getInicio().plusMinutes(equipo.getDuracionMantenimientoMinutos());
    }

    /** Dos mantenimientos se superponen si ambos equipos definen duración y las franjas se cruzan. */
    public boolean seSuperponeCon(TicketMantenimiento otro) {
        if (equipo.getDuracionMantenimientoMinutos() == 0 || otro.equipo.getDuracionMantenimientoMinutos() == 0) {
            return false;
        }
        return getInicio().isBefore(otro.getFin()) && otro.getInicio().isBefore(getFin());
    }

    @Override
    public String getId() {
        return id;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getAccionRealizada() {
        return accionRealizada;
    }

    public boolean isCerrado() {
        return cerrado;
    }

    public Equipo getEquipo() {
        return equipo;
    }

    public Tecnico getTecnicoAsignado() {
        return tecnicoAsignado;
    }

    public LocalDate getFechaProgramada() {
        return fechaProgramada;
    }

    public LocalTime getHoraProgramada() {
        return horaProgramada;
    }

    public List<Cambio> getHistorial() {
        return new ArrayList<>(historial);
    }

    /**
     * Clase interna: un registro del historial. No es static porque usa
     * datos de su ticket (el id y el tamaño del historial).
     */
    public class Cambio {

        private int numero;
        private String descripcion;

        private Cambio(String descripcion) {
            this.numero = historial.size() + 1;
            this.descripcion = descripcion;
        }

        public int getNumero() {
            return numero;
        }

        public String getDescripcion() {
            return descripcion;
        }

        @Override
        public String toString() {
            return id + " #" + numero + ": " + descripcion;
        }
    }
}
