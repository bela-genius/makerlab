package co.edu.eia.makerlab.servicio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstadoReserva;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.Prioridad;
import co.edu.eia.makerlab.modelo.Reserva;
import co.edu.eia.makerlab.modelo.TicketMantenimiento;
import co.edu.eia.makerlab.modelo.TipoEquipo;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;

/** Consultas e indicadores. Solo lee datos, no modifica nada. */
public class ConsultaService {

    private CatalogoMakerLab catalogo;
    private ReservaService reservaService;

    public ConsultaService() {
        this.catalogo = CatalogoMakerLab.getInstancia();
        this.reservaService = new ReservaService();
    }

    // ---------- Consultas ----------

    /** Equipos operativos del laboratorio que están libres en esa franja. */
    public List<Equipo> consultarDisponibilidad(Laboratorio lab, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        List<Equipo> libres = new ArrayList<>();
        if (!lab.isActivo()) {
            return libres;
        }
        for (Equipo equipo : filtrarOperativos(lab.getEquipos())) {
            if (!reservaService.existeCruce(equipo, fecha, inicio, fin)) {
                libres.add(equipo);
            }
        }
        return libres;
    }

    /** Sobrecarga: libres durante todo el horario del laboratorio. */
    public List<Equipo> consultarDisponibilidad(Laboratorio lab, LocalDate fecha) {
        return consultarDisponibilidad(lab, fecha, lab.getHoraApertura(), lab.getHoraCierre());
    }

    /**
     * Método genérico con tipo acotado (E extends Equipo): si recibe una
     * lista de Impresora3D, devuelve una lista de Impresora3D, sin casts.
     */
    public <E extends Equipo> List<E> filtrarOperativos(List<E> equipos) {
        List<E> operativos = new ArrayList<>();
        for (E equipo : equipos) {
            if (equipo.isOperativo()) {
                operativos.add(equipo);
            }
        }
        return operativos;
    }

    /**
     * Wildcard "? super Equipo" (el lado Consumer de PECS): solo se
     * escriben equipos en la lista, así que sirve una List<Equipo> o una
     * List<Object>.
     */
    public void copiarFueraDeServicio(List<? super Equipo> destino) {
        for (Equipo equipo : catalogo.getEquipos().listar()) {
            if (!equipo.isOperativo()) {
                destino.add(equipo);
            }
        }
    }

    public List<Equipo> consultarFueraDeServicio() {
        List<Equipo> resultado = new ArrayList<>();
        copiarFueraDeServicio(resultado);
        return resultado;
    }

    public List<Reserva> consultarReservasDe(Usuario usuario) {
        List<Reserva> resultado = new ArrayList<>();
        for (Reserva r : catalogo.getReservas().listar()) {
            if (r.getUsuario() == usuario) {
                resultado.add(r);
            }
        }
        return resultado;
    }

    /** Certificaciones activas que vencen entre hoy y hoy + dias (incluidos). */
    public List<Certificacion> consultarPorVencer(int dias) {
        List<Certificacion> resultado = new ArrayList<>();
        LocalDate hoy = catalogo.getFechaActual();
        for (Certificacion c : catalogo.getCertificaciones().listar()) {
            long faltan = ChronoUnit.DAYS.between(hoy, c.getFechaVencimiento());
            if (c.isActivo() && faltan >= 0 && faltan <= dias) {
                resultado.add(c);
            }
        }
        return resultado;
    }

    public List<TicketMantenimiento> consultarTicketsPorPrioridad(Prioridad prioridad) {
        List<TicketMantenimiento> resultado = new ArrayList<>();
        for (TicketMantenimiento t : catalogo.getTickets().listar()) {
            if (t.getPrioridad() == prioridad) {
                resultado.add(t);
            }
        }
        return resultado;
    }

    // ---------- Indicadores ----------

    /** Horas reales de uso (sesiones cerradas) por tipo de equipo. */
    public Map<TipoEquipo, Double> horasDeUsoPorTipo() {
        Map<TipoEquipo, Double> horas = new LinkedHashMap<>();
        for (TipoEquipo tipo : TipoEquipo.values()) {
            horas.put(tipo, 0.0);
        }
        for (Reserva r : catalogo.getReservas().listar()) {
            if (r.getSesionUso() != null) {
                TipoEquipo tipo = r.getEquipo().getTipoEquipo();
                horas.put(tipo, horas.get(tipo) + r.getSesionUso().getDuracionMinutos() / 60.0);
            }
        }
        return horas;
    }

    public int cantidadIncidentes() {
        int total = 0;
        for (Reserva r : catalogo.getReservas().listar()) {
            if (r.getSesionUso() != null) {
                total += r.getSesionUso().getIncidentesReportados().size();
            }
        }
        return total;
    }

    /** Reservas canceladas / reservas registradas (entre 0 y 1). */
    public double tasaCancelacion() {
        int total = catalogo.getReservas().tamano();
        if (total == 0) {
            return 0;
        }
        int canceladas = 0;
        for (Reserva r : catalogo.getReservas().listar()) {
            if (r.getEstado() == EstadoReserva.CANCELADA) {
                canceladas++;
            }
        }
        return (double) canceladas / total;
    }

    /** Tickets abiertos por técnico. Los que no tienen técnico van en "SIN ASIGNAR". */
    public Map<String, Integer> cargaMantenimiento() {
        Map<String, Integer> carga = new LinkedHashMap<>();
        for (TicketMantenimiento t : catalogo.getTickets().listar()) {
            if (!t.isCerrado()) {
                String clave = t.getTecnicoAsignado() == null ? "SIN ASIGNAR" : t.getTecnicoAsignado().getNombre();
                carga.put(clave, carga.getOrDefault(clave, 0) + 1);
            }
        }
        return carga;
    }
}
