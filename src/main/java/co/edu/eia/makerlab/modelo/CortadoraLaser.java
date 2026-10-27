package co.edu.eia.makerlab.modelo;

public class CortadoraLaser extends Equipo {

    private int potenciaWatts;
    private String areaTrabajo;

    public CortadoraLaser(String codigo, String nombre, String nivelRiesgo, Laboratorio laboratorio,
            int potenciaWatts, String areaTrabajo) {
        super(codigo, nombre, nivelRiesgo, laboratorio);
        this.potenciaWatts = potenciaWatts;
        this.areaTrabajo = areaTrabajo;
    }

    public int getPotenciaWatts() {
        return potenciaWatts;
    }

    public String getAreaTrabajo() {
        return areaTrabajo;
    }

    @Override
    public TipoEquipo getTipoEquipo() {
        return TipoEquipo.CORTADORA_LASER;
    }

    @Override
    public int getDuracionMantenimientoMinutos() {
        return 120;
    }

    @Override
    public void mostrarDetalles() {
        System.out.println("CortadoraLaser " + getCodigo() + ": " + getNombre() + ", " + potenciaWatts + "W, "
                + areaTrabajo + " [" + getEstado() + "]");
    }
}
