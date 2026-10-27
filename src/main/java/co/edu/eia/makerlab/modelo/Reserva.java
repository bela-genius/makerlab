package co.edu.eia.makerlab.modelo;

import java.time.LocalDate;
import java.time.LocalTime;

import co.edu.eia.makerlab.excepcion.MakerLabException;

/**
 * Las validaciones que necesitan otras reservas o las certificaciones las
 * hace ReservaService antes de crear el objeto. Por eso una Reserva nace
 * CONFIRMADA.
 */
public class Reserva implements Identificable {

    private String id;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String proposito;
    private EstadoReserva estado;
    private Usuario usuario;
    private Equipo equipo;
    private SesionUso sesionUso; // null hasta que se abre la sesión

    public Reserva(String id, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, String proposito,
            Usuario usuario, Equipo equipo) {
        this.id = id;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.proposito = proposito;
        this.usuario = usuario;
        this.equipo = equipo;
        this.estado = EstadoReserva.CONFIRMADA;
    }

    public void cancelar() throws MakerLabException {
        if (estado == EstadoReserva.CANCELADA) {
            throw new MakerLabException("La reserva " + id + " ya estaba cancelada.");
        }
        if (sesionUso != null) {
            throw new MakerLabException("La reserva " + id + " ya se uso, no se puede cancelar.");
        }
        estado = EstadoReserva.CANCELADA;
    }

    /**
     * Composición: la sesión solo existe dentro de su reserva y solo se
     * crea aquí. Regla: una sesión solo se abre desde una reserva confirmada.
     */
    public SesionUso abrirSesion(String idSesion, LocalTime horaInicioReal) throws MakerLabException {
        if (estado != EstadoReserva.CONFIRMADA) {
            throw new MakerLabException("Solo se abre sesion desde una reserva confirmada.");
        }
        if (sesionUso != null) {
            throw new MakerLabException("La reserva " + id + " ya tiene sesion.");
        }
        sesionUso = new SesionUso(idSesion, horaInicioReal);
        return sesionUso;
    }

    /**
     * ¿Esta reserva confirmada ocupa ese equipo en esa franja? Las franjas
     * que solo se tocan en el borde (10:00-12:00 y 12:00-14:00) no se cruzan.
     */
    public boolean seCruzaCon(Equipo otroEquipo, LocalDate otraFecha, LocalTime inicio, LocalTime fin) {
        return estado == EstadoReserva.CONFIRMADA
                && equipo == otroEquipo
                && fecha.equals(otraFecha)
                && inicio.isBefore(horaFin)
                && horaInicio.isBefore(fin);
    }

    @Override
    public String getId() {
        return id;
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

    public String getProposito() {
        return proposito;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Equipo getEquipo() {
        return equipo;
    }

    public SesionUso getSesionUso() {
        return sesionUso;
    }

    @Override
    public String toString() {
        return id + " (" + usuario.getNombre() + ", " + equipo.getCodigo() + ", " + fecha + " " + horaInicio + "-"
                + horaFin + ", " + estado + ")";
    }
}
