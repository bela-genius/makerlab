package co.edu.eia.makerlab.modelo;

public class Estudiante extends Usuario {

    private String carrera;
    private int semestre;

    public Estudiante(String id, String nombre, String correo, String carrera, int semestre) {
        super(id, nombre, correo);
        this.carrera = carrera;
        this.semestre = semestre;
    }

    public String getCarrera() {
        return carrera;
    }

    public int getSemestre() {
        return semestre;
    }

    @Override
    public void mostrarDatos() {
        System.out.println("Estudiante " + getId() + ": " + getNombre() + " (" + carrera + ", semestre " + semestre + ")");
    }
}
