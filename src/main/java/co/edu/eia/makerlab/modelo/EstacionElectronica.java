package co.edu.eia.makerlab.modelo;

/** No sobrescribe getDuracionMantenimientoMinutos(): no define duración. */
public class EstacionElectronica extends Equipo {

    private double voltajeMaximo;
    private String herramientas;

    public EstacionElectronica(String codigo, String nombre, String nivelRiesgo, Laboratorio laboratorio,
            double voltajeMaximo, String herramientas) {
        super(codigo, nombre, nivelRiesgo, laboratorio);
        this.voltajeMaximo = voltajeMaximo;
        this.herramientas = herramientas;
    }

    public double getVoltajeMaximo() {
        return voltajeMaximo;
    }

    public String getHerramientas() {
        return herramientas;
    }

    @Override
    public TipoEquipo getTipoEquipo() {
        return TipoEquipo.ESTACION_ELECTRONICA;
    }

    @Override
    public void mostrarDetalles() {
        System.out.println("EstacionElectronica " + getCodigo() + ": " + getNombre() + ", " + voltajeMaximo + "V, "
                + herramientas + " [" + getEstado() + "]");
    }
}
