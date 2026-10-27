package co.edu.eia.makerlab.vista;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstadoReserva;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.Reserva;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Resumen del día: estado de cada equipo por laboratorio y próximas reservas. */
public class VistaInicio extends Vista {

    private VBox laboratorios = new VBox(12);
    private VBox proximas = new VBox(8);

    public VistaInicio() {
        super("Inicio", "");
        VBox izquierda = panel("Equipos por laboratorio", laboratorios);
        VBox derecha = panel("Próximas reservas", proximas);
        getChildren().add(dosColumnas(izquierda, derecha));
        refrescar();
    }

    @Override
    public void refrescar() {
        CatalogoMakerLab catalogo = CatalogoMakerLab.getInstancia();
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"));
        String hoy = catalogo.getFechaActual().format(formato);
        setDescripcion("Hoy es " + hoy + ". Estado de los equipos y reservas que vienen.");

        laboratorios.getChildren().clear();
        for (Laboratorio lab : catalogo.getLaboratorios().listar()) {
            Label nombre = new Label(lab.getNombre() + "  (" + lab.getHoraApertura() + " a " + lab.getHoraCierre() + ")");
            nombre.getStyleClass().add("subtitulo");
            VBox bloque = new VBox(6, nombre);
            for (Equipo equipo : lab.getEquipos()) {
                Label texto = new Label(equipo.getCodigo() + "  " + equipo.getNombre());
                Region espacio = new Region();
                HBox.setHgrow(espacio, Priority.ALWAYS);
                HBox fila = new HBox(10, texto, espacio, pildora(equipo.getEstado()));
                fila.setAlignment(Pos.CENTER_LEFT);
                bloque.getChildren().add(fila);
            }
            laboratorios.getChildren().add(bloque);
        }

        proximas.getChildren().clear();
        List<Reserva> futuras = new ArrayList<>();
        for (Reserva r : catalogo.getReservas().listar()) {
            if (r.getEstado() == EstadoReserva.CONFIRMADA && !r.getFecha().isBefore(catalogo.getFechaActual())) {
                futuras.add(r);
            }
        }
        // Ordenar por fecha y hora con una clase anónima (Comparator).
        futuras.sort(new Comparator<Reserva>() {
            @Override
            public int compare(Reserva a, Reserva b) {
                int porFecha = a.getFecha().compareTo(b.getFecha());
                return porFecha != 0 ? porFecha : a.getHoraInicio().compareTo(b.getHoraInicio());
            }
        });
        if (futuras.isEmpty()) {
            Label vacio = new Label("No hay reservas desde hoy. Créelas en la sección Reservas.");
            vacio.getStyleClass().add("nota");
            proximas.getChildren().add(vacio);
        }
        for (Reserva r : futuras) {
            Label cuando = new Label(r.getFecha() + "  " + r.getHoraInicio() + " a " + r.getHoraFin());
            cuando.getStyleClass().add("etiqueta");
            Label que = new Label(r.getEquipo().getCodigo() + ", " + r.getUsuario().getNombre());
            proximas.getChildren().add(new VBox(1, cuando, que));
        }
    }
}
