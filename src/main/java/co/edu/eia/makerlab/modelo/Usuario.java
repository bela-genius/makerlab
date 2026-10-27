package co.edu.eia.makerlab.modelo;

import java.util.ArrayList;
import java.util.List;

/** Abstracta: siempre se crea un Estudiante, un Profesor o un Tecnico. */
public abstract class Usuario implements Identificable {

    private String id;
    private String nombre;
    private String correo;
    private List<String> notificaciones;

    public Usuario(String id, String nombre, String correo) {
        if (id == null || id.isBlank() || nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El id y el nombre son obligatorios.");
        }
        if (correo == null || !correo.contains("@")) {
            throw new IllegalArgumentException("Correo invalido: " + correo);
        }
        this.id = id;
        this.nombre = nombre;
        this.correo = correo;
        this.notificaciones = new ArrayList<>();
    }

    @Override
    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void agregarNotificacion(String mensaje) {
        notificaciones.add(mensaje);
    }

    /** Devuelve una copia para que nadie modifique la lista original. */
    public List<String> getNotificaciones() {
        return new ArrayList<>(notificaciones);
    }

    public abstract void mostrarDatos();
}
