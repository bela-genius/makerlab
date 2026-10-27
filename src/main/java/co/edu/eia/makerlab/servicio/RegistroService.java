package co.edu.eia.makerlab.servicio;

import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.ObservadorEquipo;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.patron.NotificadorReservas;

/** Registro y búsqueda de usuarios, laboratorios, equipos y certificaciones. */
public class RegistroService {

    private CatalogoMakerLab catalogo;
    private NotificadorReservas notificador;

    public RegistroService() {
        this.catalogo = CatalogoMakerLab.getInstancia();
        this.notificador = new NotificadorReservas();
    }

    public void registrarUsuario(Usuario usuario) throws MakerLabException {
        catalogo.getUsuarios().agregar(usuario);
    }

    public void registrarLaboratorio(Laboratorio laboratorio) throws MakerLabException {
        catalogo.getLaboratorios().agregar(laboratorio);
    }

    /**
     * Guarda el equipo, lo agrega a su laboratorio y le conecta dos
     * observadores: el notificador de reservas y una bitácora en consola.
     */
    public void registrarEquipo(Equipo equipo) throws MakerLabException {
        if (!catalogo.getLaboratorios().existe(equipo.getLaboratorio().getCodigo())) {
            throw new MakerLabException("El laboratorio " + equipo.getLaboratorio().getCodigo() + " no esta registrado.");
        }
        catalogo.getEquipos().agregar(equipo);
        equipo.getLaboratorio().agregarEquipo(equipo);
        equipo.agregarObservador(notificador);

        // Clase anónima: un observador sencillo que solo imprime el cambio.
        // No vale la pena crear una clase con nombre para una sola línea.
        equipo.agregarObservador(new ObservadorEquipo() {
            @Override
            public void estadoCambiado(Equipo e) {
                System.out.println("  [Bitacora] " + e.getCodigo() + " ahora esta " + e.getEstado());
            }
        });
    }

    public void registrarCertificacion(Certificacion certificacion) throws MakerLabException {
        if (!catalogo.getUsuarios().existe(certificacion.getUsuario().getId())) {
            throw new MakerLabException("El usuario " + certificacion.getUsuario().getId() + " no esta registrado.");
        }
        catalogo.getCertificaciones().agregar(certificacion);
    }

    // Ids nuevos: la vista o el menú piden el id aquí y luego crean el objeto.

    public String siguienteIdUsuario() {
        return catalogo.getUsuarios().siguienteId("U-", 3);
    }

    public String siguienteIdLaboratorio() {
        return catalogo.getLaboratorios().siguienteId("LAB-", 2);
    }

    public String siguienteIdEquipo() {
        return catalogo.getEquipos().siguienteId("EQ-", 3);
    }

    public String siguienteIdCertificacion() {
        return catalogo.getCertificaciones().siguienteId("C-", 3);
    }

    public Usuario buscarUsuario(String id) throws MakerLabException {
        Usuario usuario = catalogo.getUsuarios().buscar(id);
        if (usuario == null) {
            throw new MakerLabException("No existe el usuario " + id + ".");
        }
        return usuario;
    }

    public Laboratorio buscarLaboratorio(String codigo) throws MakerLabException {
        Laboratorio laboratorio = catalogo.getLaboratorios().buscar(codigo);
        if (laboratorio == null) {
            throw new MakerLabException("No existe el laboratorio " + codigo + ".");
        }
        return laboratorio;
    }

    public Equipo buscarEquipo(String codigo) throws MakerLabException {
        Equipo equipo = catalogo.getEquipos().buscar(codigo);
        if (equipo == null) {
            throw new MakerLabException("No existe el equipo " + codigo + ".");
        }
        return equipo;
    }
}
