package co.edu.eia.makerlab.servicio;

import java.time.LocalDate;
import java.time.LocalTime;

import co.edu.eia.makerlab.excepcion.CertificacionInvalidaException;
import co.edu.eia.makerlab.excepcion.CruceHorarioException;
import co.edu.eia.makerlab.excepcion.EquipoNoDisponibleException;
import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.Reserva;
import co.edu.eia.makerlab.modelo.SesionUso;
import co.edu.eia.makerlab.modelo.SolicitudEspera;
import co.edu.eia.makerlab.modelo.TipoEquipo;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;

/** Crear y cancelar reservas con todas sus validaciones. */
public class ReservaService {

    private CatalogoMakerLab catalogo;

    public ReservaService() {
        this.catalogo = CatalogoMakerLab.getInstancia();
    }

    /**
     * Valida en este orden y lanza la excepción del primer problema:
     * id repetido, horas, laboratorio activo y horario, certificación,
     * equipo operativo y cruce. Si el problema es el cruce, la solicitud
     * queda en la lista de espera.
     */
    public Reserva crearReserva(String id, Usuario usuario, Equipo equipo, LocalDate fecha, LocalTime horaInicio,
            LocalTime horaFin, String proposito) throws MakerLabException {
        if (catalogo.getReservas().existe(id)) {
            throw new MakerLabException("Ya existe la reserva " + id + ".");
        }
        if (!horaFin.isAfter(horaInicio)) {
            throw new MakerLabException("La hora final debe ser posterior a la inicial.");
        }
        Laboratorio laboratorio = equipo.getLaboratorio();
        if (!laboratorio.isActivo()) {
            throw new EquipoNoDisponibleException("El laboratorio " + laboratorio.getCodigo() + " esta inactivo.");
        }
        if (!laboratorio.cubreFranja(horaInicio, horaFin)) {
            throw new MakerLabException("La franja " + horaInicio + "-" + horaFin + " esta fuera del horario de "
                    + laboratorio.getCodigo() + " (" + laboratorio.getHoraApertura() + "-"
                    + laboratorio.getHoraCierre() + ").");
        }
        if (!tieneCertificacionVigente(usuario, equipo.getTipoEquipo(), fecha)) {
            throw new CertificacionInvalidaException(usuario.getNombre() + " no tiene certificacion vigente de "
                    + equipo.getTipoEquipo() + ".");
        }
        if (!equipo.isOperativo()) {
            throw new EquipoNoDisponibleException("El equipo " + equipo.getCodigo() + " esta " + equipo.getEstado() + ".");
        }
        if (existeCruce(equipo, fecha, horaInicio, horaFin)) {
            boolean agregada = catalogo.getListaEspera().agregar(usuario, equipo, fecha, horaInicio, horaFin,
                    catalogo.getFechaActual());
            String espera = agregada ? "Quedo en lista de espera." : "Ya estaba en lista de espera.";
            throw new CruceHorarioException("La franja ya esta reservada. " + espera);
        }
        Reserva reserva = new Reserva(id, fecha, horaInicio, horaFin, proposito, usuario, equipo);
        catalogo.getReservas().agregar(reserva);
        return reserva;
    }

    /** Igual que el anterior, pero el id (R-239, R-240...) lo genera el sistema. */
    public Reserva crearReserva(Usuario usuario, Equipo equipo, LocalDate fecha, LocalTime horaInicio,
            LocalTime horaFin, String proposito) throws MakerLabException {
        String id = catalogo.getReservas().siguienteId("R-", 3);
        return crearReserva(id, usuario, equipo, fecha, horaInicio, horaFin, proposito);
    }

    /**
     * Abre la sesión de uso de la reserva con el siguiente id S-.
     * Las sesiones viven dentro de su reserva, así que se recorren todas
     * las reservas para encontrar el número de sesión más alto.
     */
    public SesionUso abrirSesion(Reserva reserva, LocalTime horaInicioReal) throws MakerLabException {
        int mayor = 0;
        for (Reserva r : catalogo.getReservas().listar()) {
            SesionUso sesion = r.getSesionUso();
            if (sesion != null) {
                int numero = Integer.parseInt(sesion.getId().substring(2)); // "S-089" -> 89
                if (numero > mayor) {
                    mayor = numero;
                }
            }
        }
        String id = String.format("S-%03d", mayor + 1);
        return reserva.abrirSesion(id, horaInicioReal);
    }

    public boolean tieneCertificacionVigente(Usuario usuario, TipoEquipo tipo, LocalDate fecha) {
        for (Certificacion c : catalogo.getCertificaciones().listar()) {
            if (c.getUsuario() == usuario && c.getTipoEquipoAutorizado() == tipo && c.esVigente(fecha)) {
                return true;
            }
        }
        return false;
    }

    public boolean existeCruce(Equipo equipo, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        for (Reserva r : catalogo.getReservas().listar()) {
            if (r.seCruzaCon(equipo, fecha, horaInicio, horaFin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cancela la reserva. Como la franja queda libre, la lista de espera
     * elige al siguiente con la estrategia activa y se le avisa.
     * Devuelve ese candidato, o null si nadie esperaba.
     */
    public SolicitudEspera cancelarReserva(Reserva reserva) throws MakerLabException {
        reserva.cancelar();
        SolicitudEspera siguiente = catalogo.getListaEspera().obtenerSiguiente(reserva.getEquipo(), reserva.getFecha(),
                reserva.getHoraInicio(), reserva.getHoraFin(), catalogo.getEstrategiaEspera());
        if (siguiente != null) {
            siguiente.getUsuario().agregarNotificacion("Se libero " + reserva.getEquipo().getCodigo() + " el "
                    + reserva.getFecha() + " de " + reserva.getHoraInicio() + " a " + reserva.getHoraFin()
                    + ". Ya puedes reservar.");
        }
        return siguiente;
    }

    public Reserva buscarReserva(String id) throws MakerLabException {
        Reserva reserva = catalogo.getReservas().buscar(id);
        if (reserva == null) {
            throw new MakerLabException("No existe la reserva " + id + ".");
        }
        return reserva;
    }
}
