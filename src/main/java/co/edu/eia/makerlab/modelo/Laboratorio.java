package co.edu.eia.makerlab.modelo;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Laboratorio implements Identificable {

    private String codigo;
    private String nombre;
    private String ubicacion;
    private LocalTime horaApertura;
    private LocalTime horaCierre;
    private boolean activo;
    private List<Equipo> equipos;

    public Laboratorio(String codigo, String nombre, String ubicacion, LocalTime horaApertura, LocalTime horaCierre) {
        if (!horaCierre.isAfter(horaApertura)) {
            throw new IllegalArgumentException("La hora de cierre debe ser posterior a la de apertura.");
        }
        this.codigo = codigo;
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.horaApertura = horaApertura;
        this.horaCierre = horaCierre;
        this.activo = true;
        this.equipos = new ArrayList<>();
    }

    @Override
    public String getId() {
        return codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public LocalTime getHoraApertura() {
        return horaApertura;
    }

    public LocalTime getHoraCierre() {
        return horaCierre;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public void agregarEquipo(Equipo equipo) {
        equipos.add(equipo);
    }

    public List<Equipo> getEquipos() {
        return new ArrayList<>(equipos);
    }

    /** true si la franja completa cabe dentro del horario del laboratorio. */
    public boolean cubreFranja(LocalTime inicio, LocalTime fin) {
        return !inicio.isBefore(horaApertura) && !fin.isAfter(horaCierre);
    }

    public void mostrarEquipos() {
        System.out.println(nombre + " (" + codigo + ", " + horaApertura + "-" + horaCierre + "):");
        for (Equipo equipo : equipos) {
            equipo.mostrarDetalles(); // polimorfismo: cada subtipo se muestra a su manera
        }
    }
}
