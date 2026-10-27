package co.edu.eia.makerlab.patron;

import co.edu.eia.makerlab.modelo.EstadoReserva;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.ObservadorEquipo;
import co.edu.eia.makerlab.modelo.Reserva;

/**
 * Observador concreto: cuando un equipo deja de estar operativo, avisa a
 * cada usuario que tiene una reserva futura sobre ese equipo (confirmada,
 * todavía sin usar y de hoy en adelante).
 */
public class NotificadorReservas implements ObservadorEquipo {

    @Override
    public void estadoCambiado(Equipo equipo) {
        if (equipo.isOperativo()) {
            return;
        }
        CatalogoMakerLab catalogo = CatalogoMakerLab.getInstancia();
        for (Reserva r : catalogo.getReservas().listar()) {
            boolean esFutura = !r.getFecha().isBefore(catalogo.getFechaActual());
            if (r.getEquipo() == equipo && r.getEstado() == EstadoReserva.CONFIRMADA && r.getSesionUso() == null
                    && esFutura) {
                r.getUsuario().agregarNotificacion("El equipo " + equipo.getCodigo() + " paso a " + equipo.getEstado()
                        + ". Tu reserva " + r.getId() + " del " + r.getFecha() + " puede verse afectada.");
            }
        }
    }
}
