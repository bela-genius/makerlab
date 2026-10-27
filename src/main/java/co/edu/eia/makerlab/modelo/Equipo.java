package co.edu.eia.makerlab.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstracta: siempre se crea un subtipo concreto.
 *
 * Es el sujeto del patrón Observer: cuando cambia de estado con
 * setEstado(...), avisa a todos sus observadores.
 */
public abstract class Equipo implements Identificable {

    private String codigo;
    private String nombre;
    private String nivelRiesgo;
    private EstadoEquipo estado;
    private Laboratorio laboratorio;
    private List<ObservadorEquipo> observadores;

    public Equipo(String codigo, String nombre, String nivelRiesgo, Laboratorio laboratorio) {
        if (codigo == null || codigo.isBlank() || laboratorio == null) {
            throw new IllegalArgumentException("El codigo y el laboratorio son obligatorios.");
        }
        this.codigo = codigo;
        this.nombre = nombre;
        this.nivelRiesgo = nivelRiesgo;
        this.laboratorio = laboratorio;
        this.estado = EstadoEquipo.OPERATIVO;
        this.observadores = new ArrayList<>();
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

    public String getNivelRiesgo() {
        return nivelRiesgo;
    }

    public EstadoEquipo getEstado() {
        return estado;
    }

    public Laboratorio getLaboratorio() {
        return laboratorio;
    }

    public boolean isOperativo() {
        return estado == EstadoEquipo.OPERATIVO;
    }

    /** Cambia el estado y avisa a los observadores (solo si de verdad cambió). */
    public void setEstado(EstadoEquipo nuevoEstado) {
        if (nuevoEstado == estado) {
            return;
        }
        estado = nuevoEstado;
        for (ObservadorEquipo observador : observadores) {
            observador.estadoCambiado(this);
        }
    }

    public void agregarObservador(ObservadorEquipo observador) {
        observadores.add(observador);
    }

    /**
     * Minutos que dura un mantenimiento. 0 = este equipo no define
     * duración, así que no aplica la regla de franjas del técnico.
     */
    public int getDuracionMantenimientoMinutos() {
        return 0;
    }

    public abstract TipoEquipo getTipoEquipo();

    public abstract void mostrarDetalles();
}
