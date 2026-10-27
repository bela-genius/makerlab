package co.edu.eia.makerlab.servicio;

import java.time.LocalDate;
import java.time.LocalTime;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.Prioridad;
import co.edu.eia.makerlab.modelo.Tecnico;
import co.edu.eia.makerlab.modelo.TicketMantenimiento;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;

public class MantenimientoService {

    private CatalogoMakerLab catalogo;

    public MantenimientoService() {
        this.catalogo = CatalogoMakerLab.getInstancia();
    }

    /**
     * Abrir un ticket deja el equipo EN_MANTENIMIENTO. Eso bloquea nuevas
     * reservas y, por el Observer, avisa a quienes tenían reservas futuras.
     */
    public TicketMantenimiento crearTicket(String id, Prioridad prioridad, String descripcion, Equipo equipo,
            LocalDate fecha, LocalTime hora) throws MakerLabException {
        TicketMantenimiento ticket = new TicketMantenimiento(id, prioridad, descripcion, equipo, fecha, hora);
        catalogo.getTickets().agregar(ticket);
        equipo.setEstado(EstadoEquipo.EN_MANTENIMIENTO);
        return ticket;
    }

    /** Igual que el anterior, pero el id (TK-003, TK-004...) lo genera el sistema. */
    public TicketMantenimiento crearTicket(Prioridad prioridad, String descripcion, Equipo equipo, LocalDate fecha,
            LocalTime hora) throws MakerLabException {
        String id = catalogo.getTickets().siguienteId("TK-", 3);
        return crearTicket(id, prioridad, descripcion, equipo, fecha, hora);
    }

    /** Regla: un técnico no puede tener dos mantenimientos abiertos que se crucen. */
    public void asignarTecnico(TicketMantenimiento ticket, Tecnico tecnico) throws MakerLabException {
        for (TicketMantenimiento otro : catalogo.getTickets().listar()) {
            if (otro != ticket && !otro.isCerrado() && otro.getTecnicoAsignado() == tecnico
                    && ticket.seSuperponeCon(otro)) {
                throw new MakerLabException(tecnico.getNombre() + " ya tiene el ticket " + otro.getId() + " de "
                        + otro.getInicio().toLocalTime() + " a " + otro.getFin().toLocalTime() + ".");
            }
        }
        ticket.asignarTecnico(tecnico);
    }

    public TicketMantenimiento buscarTicket(String id) throws MakerLabException {
        TicketMantenimiento ticket = catalogo.getTickets().buscar(id);
        if (ticket == null) {
            throw new MakerLabException("No existe el ticket " + id + ".");
        }
        return ticket;
    }
}
