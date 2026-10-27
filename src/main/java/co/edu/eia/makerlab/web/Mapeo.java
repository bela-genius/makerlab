package co.edu.eia.makerlab.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.CortadoraLaser;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstacionElectronica;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.Estudiante;
import co.edu.eia.makerlab.modelo.Impresora3D;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.Profesor;
import co.edu.eia.makerlab.modelo.Reserva;
import co.edu.eia.makerlab.modelo.SesionUso;
import co.edu.eia.makerlab.modelo.SolicitudEspera;
import co.edu.eia.makerlab.modelo.Tecnico;
import co.edu.eia.makerlab.modelo.TicketMantenimiento;
import co.edu.eia.makerlab.modelo.TipoEquipo;
import co.edu.eia.makerlab.modelo.Usuario;

/**
 * Convierte los objetos del modelo en mapas listos para JSON. Solo lee: no
 * contiene reglas. Los textos de etiqueta son los mismos de las vistas JavaFX.
 */
final class Mapeo {

    private Mapeo() {
    }

    static String nombreTipo(TipoEquipo tipo) {
        switch (tipo) {
        case IMPRESORA_3D:
            return "Impresora 3D";
        case CORTADORA_LASER:
            return "Cortadora láser";
        default:
            return "Estación electrónica";
        }
    }

    static String codigoTipo(Usuario usuario) {
        if (usuario instanceof Estudiante) {
            return "ESTUDIANTE";
        }
        return usuario instanceof Profesor ? "PROFESOR" : "TECNICO";
    }

    static String nombreTipo(Usuario usuario) {
        if (usuario instanceof Estudiante) {
            return "Estudiante";
        }
        return usuario instanceof Profesor ? "Profesor" : "Técnico";
    }

    static String nombreEstado(EstadoEquipo estado) {
        switch (estado) {
        case OPERATIVO:
            return "Operativo";
        case EN_MANTENIMIENTO:
            return "En mantenimiento";
        case FUERA_DE_SERVICIO:
            return "Fuera de servicio";
        default:
            return "Inactivo";
        }
    }

    static Map<String, Object> certificacion(Certificacion c, LocalDate hoy) {
        return Json.mapa("id", c.getId(), "tipoEquipo", c.getTipoEquipoAutorizado().name(), "tipoEquipoNombre",
                nombreTipo(c.getTipoEquipoAutorizado()), "emision", c.getFechaEmision().toString(), "vencimiento",
                c.getFechaVencimiento().toString(), "vigente", c.esVigente(hoy), "activo", c.isActivo());
    }

    static Map<String, Object> usuario(Usuario u, List<Certificacion> todas, LocalDate hoy) {
        Map<String, Object> m = Json.mapa("id", u.getId(), "nombre", u.getNombre(), "correo", u.getCorreo(), "tipo",
                codigoTipo(u), "tipoNombre", nombreTipo(u));
        if (u instanceof Estudiante) {
            m.put("detalleEtiqueta", "Carrera");
            m.put("detalle", ((Estudiante) u).getCarrera());
            m.put("semestre", ((Estudiante) u).getSemestre());
        } else if (u instanceof Profesor) {
            m.put("detalleEtiqueta", "Departamento");
            m.put("detalle", ((Profesor) u).getDepartamento());
        } else {
            m.put("detalleEtiqueta", "Especialidad");
            m.put("detalle", ((Tecnico) u).getEspecialidad());
        }
        List<Object> certs = new ArrayList<>();
        for (Certificacion c : todas) {
            if (c.getUsuario() == u) {
                certs.add(certificacion(c, hoy));
            }
        }
        m.put("certificaciones", certs);
        m.put("notificaciones", u.getNotificaciones());
        return m;
    }

    static Map<String, Object> equipo(Equipo e) {
        Map<String, Object> m = Json.mapa("codigo", e.getCodigo(), "nombre", e.getNombre(), "tipo",
                e.getTipoEquipo().name(), "tipoNombre", nombreTipo(e.getTipoEquipo()), "laboratorio",
                e.getLaboratorio().getCodigo(), "laboratorioNombre", e.getLaboratorio().getNombre(), "riesgo",
                e.getNivelRiesgo(), "estado", e.getEstado().name(), "estadoNombre", nombreEstado(e.getEstado()));
        if (e instanceof Impresora3D) {
            m.put("caracteristicas", "Tecnología " + ((Impresora3D) e).getTecnologia() + " · "
                    + ((Impresora3D) e).getMaterialSoportado());
        } else if (e instanceof CortadoraLaser) {
            m.put("caracteristicas", ((CortadoraLaser) e).getPotenciaWatts() + " W · "
                    + ((CortadoraLaser) e).getAreaTrabajo());
        } else if (e instanceof EstacionElectronica) {
            m.put("caracteristicas", "Hasta " + ((EstacionElectronica) e).getVoltajeMaximo() + " V · "
                    + ((EstacionElectronica) e).getHerramientas());
        }
        return m;
    }

    static Map<String, Object> laboratorio(Laboratorio lab) {
        List<Object> equipos = new ArrayList<>();
        for (Equipo e : lab.getEquipos()) {
            equipos.add(equipo(e));
        }
        return Json.mapa("codigo", lab.getCodigo(), "nombre", lab.getNombre(), "ubicacion", lab.getUbicacion(),
                "apertura", lab.getHoraApertura().toString(), "cierre", lab.getHoraCierre().toString(), "activo",
                lab.isActivo(), "equipos", equipos);
    }

    static Map<String, Object> sesion(SesionUso s) {
        if (s == null) {
            return null;
        }
        return Json.mapa("id", s.getId(), "inicioReal", s.getHoraInicioReal().toString(), "finReal",
                s.getHoraFinReal() == null ? null : s.getHoraFinReal().toString(), "activa", s.isActiva(),
                "duracionMinutos", s.getDuracionMinutos(), "consumibles", s.getConsumiblesUsados(), "incidentes",
                s.getIncidentesReportados(), "observaciones", s.getObservaciones());
    }

    static Map<String, Object> reserva(Reserva r) {
        SesionUso s = r.getSesionUso();
        // Mismo texto de la columna "Uso" de la vista JavaFX.
        String uso = s == null ? "Sin sesión"
                : s.isActiva() ? "En uso desde " + s.getHoraInicioReal() : "Usado " + s.getDuracionMinutos() + " min";
        return Json.mapa("id", r.getId(), "usuarioId", r.getUsuario().getId(), "usuario", r.getUsuario().getNombre(),
                "equipoCodigo", r.getEquipo().getCodigo(), "equipo", r.getEquipo().getNombre(), "fecha",
                r.getFecha().toString(), "inicio", r.getHoraInicio().toString(), "fin", r.getHoraFin().toString(),
                "proposito", r.getProposito(), "estado", r.getEstado().name(), "uso", uso, "sesion", sesion(s));
    }

    static Map<String, Object> solicitud(SolicitudEspera s) {
        return Json.mapa("id", s.getId(), "usuarioId", s.getUsuario().getId(), "usuario", s.getUsuario().getNombre(),
                "tipoUsuario", nombreTipo(s.getUsuario()), "equipoCodigo", s.getEquipo().getCodigo(), "fecha",
                s.getFecha().toString(), "inicio", s.getHoraInicio().toString(), "fin", s.getHoraFin().toString(),
                "fechaSolicitud", s.getFechaSolicitud().toString());
    }

    static Map<String, Object> ticket(TicketMantenimiento t) {
        List<Object> historial = new ArrayList<>();
        for (TicketMantenimiento.Cambio c : t.getHistorial()) {
            historial.add(Json.mapa("numero", c.getNumero(), "descripcion", c.getDescripcion()));
        }
        return Json.mapa("id", t.getId(), "equipoCodigo", t.getEquipo().getCodigo(), "equipo",
                t.getEquipo().getNombre(), "prioridad", t.getPrioridad().name(), "descripcion", t.getDescripcion(),
                "tecnicoId", t.getTecnicoAsignado() == null ? null : t.getTecnicoAsignado().getId(), "tecnico",
                t.getTecnicoAsignado() == null ? null : t.getTecnicoAsignado().getNombre(), "fecha",
                t.getFechaProgramada().toString(), "hora", t.getHoraProgramada().toString(), "cerrado",
                t.isCerrado(), "accion", t.getAccionRealizada(), "historial", historial);
    }
}
