package co.edu.eia.makerlab.patron;

import java.time.LocalDate;

import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstrategiaPrioridad;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.ListaEspera;
import co.edu.eia.makerlab.modelo.Reserva;
import co.edu.eia.makerlab.modelo.TicketMantenimiento;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.repositorio.Repositorio;

/**
 * Patrón Singleton: catálogo central con todos los datos del sistema y su
 * configuración. Hay una sola instancia, así que todos los servicios ven
 * los mismos usuarios, equipos y reservas, la misma fecha y la misma
 * estrategia de lista de espera.
 */
public class CatalogoMakerLab {

    private static CatalogoMakerLab instancia;

    private Repositorio<Usuario> usuarios;
    private Repositorio<Laboratorio> laboratorios;
    private Repositorio<Equipo> equipos;
    private Repositorio<Certificacion> certificaciones;
    private Repositorio<Reserva> reservas;
    private Repositorio<TicketMantenimiento> tickets;
    private ListaEspera listaEspera;
    private LocalDate fechaActual;
    private EstrategiaPrioridad estrategiaEspera;

    // Constructor privado: nadie fuera de esta clase puede hacer "new".
    private CatalogoMakerLab() {
        reiniciar();
    }

    public static CatalogoMakerLab getInstancia() {
        if (instancia == null) {
            instancia = new CatalogoMakerLab();
        }
        return instancia;
    }

    /** Deja el catálogo vacío. Lo usan las pruebas antes de cada caso. */
    public void reiniciar() {
        usuarios = new Repositorio<>();
        laboratorios = new Repositorio<>();
        equipos = new Repositorio<>();
        certificaciones = new Repositorio<>();
        reservas = new Repositorio<>();
        tickets = new Repositorio<>();
        listaEspera = new ListaEspera();
        fechaActual = LocalDate.now();
        estrategiaEspera = new EstrategiaFifo();
    }

    public Repositorio<Usuario> getUsuarios() {
        return usuarios;
    }

    public Repositorio<Laboratorio> getLaboratorios() {
        return laboratorios;
    }

    public Repositorio<Equipo> getEquipos() {
        return equipos;
    }

    public Repositorio<Certificacion> getCertificaciones() {
        return certificaciones;
    }

    public Repositorio<Reserva> getReservas() {
        return reservas;
    }

    public Repositorio<TicketMantenimiento> getTickets() {
        return tickets;
    }

    public ListaEspera getListaEspera() {
        return listaEspera;
    }

    /** "Hoy" para el sistema. Se puede fijar para que la demo y las pruebas den siempre lo mismo. */
    public LocalDate getFechaActual() {
        return fechaActual;
    }

    public void setFechaActual(LocalDate fechaActual) {
        this.fechaActual = fechaActual;
    }

    public EstrategiaPrioridad getEstrategiaEspera() {
        return estrategiaEspera;
    }

    public void setEstrategiaEspera(EstrategiaPrioridad estrategiaEspera) {
        this.estrategiaEspera = estrategiaEspera;
    }
}
