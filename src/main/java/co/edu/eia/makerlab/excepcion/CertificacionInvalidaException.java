package co.edu.eia.makerlab.excepcion;

/** El usuario no tiene una certificación vigente para el tipo de equipo. */
public class CertificacionInvalidaException extends MakerLabException {

    private static final long serialVersionUID = 1L;

    public CertificacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
