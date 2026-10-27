package co.edu.eia.makerlab.modelo;

public class Tecnico extends Usuario {

    private String especialidad;

    public Tecnico(String id, String nombre, String correo, String especialidad) {
        super(id, nombre, correo);
        this.especialidad = especialidad;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    @Override
    public void mostrarDatos() {
        System.out.println("Tecnico " + getId() + ": " + getNombre() + " (" + especialidad + ")");
    }
}
