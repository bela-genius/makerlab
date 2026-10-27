package co.edu.eia.makerlab.modelo;

public class Profesor extends Usuario {

    private String departamento;

    public Profesor(String id, String nombre, String correo, String departamento) {
        super(id, nombre, correo);
        this.departamento = departamento;
    }

    public String getDepartamento() {
        return departamento;
    }

    @Override
    public void mostrarDatos() {
        System.out.println("Profesor " + getId() + ": " + getNombre() + " (" + departamento + ")");
    }
}
