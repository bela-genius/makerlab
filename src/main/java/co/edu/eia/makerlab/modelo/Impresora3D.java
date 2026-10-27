package co.edu.eia.makerlab.modelo;

public class Impresora3D extends Equipo {

    private String tecnologia;
    private String materialSoportado;

    public Impresora3D(String codigo, String nombre, String nivelRiesgo, Laboratorio laboratorio, String tecnologia,
            String materialSoportado) {
        super(codigo, nombre, nivelRiesgo, laboratorio);
        this.tecnologia = tecnologia;
        this.materialSoportado = materialSoportado;
    }

    public String getTecnologia() {
        return tecnologia;
    }

    public String getMaterialSoportado() {
        return materialSoportado;
    }

    @Override
    public TipoEquipo getTipoEquipo() {
        return TipoEquipo.IMPRESORA_3D;
    }

    @Override
    public int getDuracionMantenimientoMinutos() {
        return 60;
    }

    @Override
    public void mostrarDetalles() {
        System.out.println("Impresora3D " + getCodigo() + ": " + getNombre() + ", " + tecnologia + ", "
                + materialSoportado + " [" + getEstado() + "]");
    }
}
