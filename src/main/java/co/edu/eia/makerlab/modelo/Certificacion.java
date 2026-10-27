package co.edu.eia.makerlab.modelo;

import java.time.LocalDate;

public class Certificacion implements Identificable {

    private String id;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private boolean activo;
    private TipoEquipo tipoEquipoAutorizado;
    private Usuario usuario;

    public Certificacion(String id, LocalDate fechaEmision, LocalDate fechaVencimiento, Usuario usuario,
            TipoEquipo tipoEquipoAutorizado) {
        if (fechaVencimiento.isBefore(fechaEmision)) {
            throw new IllegalArgumentException("El vencimiento no puede ser anterior a la emision.");
        }
        this.id = id;
        this.fechaEmision = fechaEmision;
        this.fechaVencimiento = fechaVencimiento;
        this.usuario = usuario;
        this.tipoEquipoAutorizado = tipoEquipoAutorizado;
        this.activo = true;
    }

    /** Vigente entre la emisión y el vencimiento, ambos días incluidos. */
    public boolean esVigente(LocalDate fecha) {
        return activo && !fecha.isBefore(fechaEmision) && !fecha.isAfter(fechaVencimiento);
    }

    public void revocar() {
        activo = false;
    }

    @Override
    public String getId() {
        return id;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public boolean isActivo() {
        return activo;
    }

    public TipoEquipo getTipoEquipoAutorizado() {
        return tipoEquipoAutorizado;
    }

    public Usuario getUsuario() {
        return usuario;
    }
}
