package co.edu.eia.makerlab.excepcion;

/** La franja pedida se cruza con otra reserva confirmada del mismo equipo. */
public class CruceHorarioException extends MakerLabException {

    private static final long serialVersionUID = 1L;

    public CruceHorarioException(String mensaje) {
        super(mensaje);
    }
}
