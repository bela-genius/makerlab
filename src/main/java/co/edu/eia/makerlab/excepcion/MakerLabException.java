package co.edu.eia.makerlab.excepcion;

/**
 * Excepción general del sistema. Se usa cuando se incumple una regla de
 * negocio que no tiene una excepción propia: id repetido, horario fuera
 * del laboratorio, cerrar una sesión dos veces, etc.
 *
 * Extiende Exception (checked), así que el compilador obliga a atraparla
 * o declararla con throws.
 */
public class MakerLabException extends Exception {

    private static final long serialVersionUID = 1L;

    public MakerLabException(String mensaje) {
        super(mensaje);
    }
}
