package co.edu.eia.makerlab.web;

/**
 * Error que la capa web convierte en una respuesta HTTP. Se usa para lo que
 * no es una regla de negocio del sistema: JSON o campos mal escritos (400),
 * recursos que no existen (404), token incorrecto (403) o base caída (503).
 * Las reglas de negocio siguen saliendo como MakerLabException, con su mensaje.
 */
public class ApiError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int estado;
    private final String tipo;

    public ApiError(int estado, String tipo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.tipo = tipo;
    }

    public int getEstado() {
        return estado;
    }

    public String getTipo() {
        return tipo;
    }

    /** Dato mal escrito por quien usa la app. */
    public static ApiError datos(String mensaje) {
        return new ApiError(400, "DatosInvalidos", mensaje);
    }

    public static ApiError noExiste(String mensaje) {
        return new ApiError(404, "NoExiste", mensaje);
    }
}
