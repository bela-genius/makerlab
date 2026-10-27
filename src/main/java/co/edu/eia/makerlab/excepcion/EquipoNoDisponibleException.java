package co.edu.eia.makerlab.excepcion;

/** El equipo no está OPERATIVO o su laboratorio está inactivo. */
public class EquipoNoDisponibleException extends MakerLabException {

    private static final long serialVersionUID = 1L;

    public EquipoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
