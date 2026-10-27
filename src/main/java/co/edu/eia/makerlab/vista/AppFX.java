package co.edu.eia.makerlab.vista;

import java.util.LinkedHashMap;
import java.util.Map;

import co.edu.eia.makerlab.app.Arranque;
import co.edu.eia.makerlab.excepcion.MakerLabException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Ventana principal: barra lateral con las secciones y, a la derecha, la
 * sección elegida. Usa los mismos servicios que el menú de consola.
 *
 * Para ejecutarla desde Eclipse se usa Lanzador (ver su comentario).
 */
public class AppFX extends Application {

    private BorderPane raiz = new BorderPane();
    private ScrollPane contenido = new ScrollPane();
    private Map<String, Vista> vistas = new LinkedHashMap<>();
    private Map<String, Button> botones = new LinkedHashMap<>();
    private String mensajeArranque;

    @Override
    public void start(Stage ventana) {
        try {
            mensajeArranque = Arranque.iniciar();
        } catch (MakerLabException e) {
            mensajeArranque = "Error cargando datos: " + e.getMessage();
        }

        vistas.put("Inicio", new VistaInicio());
        vistas.put("Usuarios", new VistaUsuarios());
        vistas.put("Equipos", new VistaEquipos());
        vistas.put("Reservas", new VistaReservas());
        vistas.put("Mantenimiento", new VistaMantenimiento());
        vistas.put("Indicadores", new VistaIndicadores());

        contenido.setFitToWidth(true);
        contenido.setFitToHeight(true);
        contenido.getStyleClass().add("edge-to-edge");
        raiz.setLeft(barraLateral());
        raiz.setCenter(contenido);
        mostrar("Inicio");

        Scene escena = new Scene(raiz, 1280, 780);
        escena.getStylesheets().add(getClass().getResource("estilos.css").toExternalForm());
        ventana.setTitle("MakerLab EIA");
        ventana.setMinWidth(1100);
        ventana.setMinHeight(680);
        ventana.setScene(escena);
        ventana.show();
    }

    private VBox barraLateral() {
        Label marca = new Label("MakerLab EIA");
        marca.getStyleClass().add("marca");
        Label sub = new Label("Laboratorios de fabricación");
        sub.getStyleClass().add("marca-sub");
        VBox lateral = new VBox(marca, sub);
        lateral.getStyleClass().add("lateral");

        for (String nombre : vistas.keySet()) {
            Button boton = new Button(nombre);
            boton.getStyleClass().add("nav");
            boton.setMaxWidth(Double.MAX_VALUE);
            boton.setOnAction(e -> mostrar(nombre));
            botones.put(nombre, boton);
            lateral.getChildren().add(boton);
        }

        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);
        String modo = Arranque.hayBaseDeDatos() ? "Datos en PostgreSQL" : "Datos en memoria (sin base de datos)";
        Label estado = new Label(modo);
        estado.getStyleClass().add("estado-datos");
        estado.setMaxWidth(180);
        estado.setTooltip(new Tooltip(mensajeArranque)); // el detalle aparece al pasar el mouse
        lateral.getChildren().addAll(espacio, estado);
        return lateral;
    }

    /** Muestra una sección y marca su botón como activo. */
    public void mostrar(String nombre) {
        Vista vista = vistas.get(nombre);
        vista.refrescar();
        contenido.setContent(vista);
        for (Map.Entry<String, Button> par : botones.entrySet()) {
            par.getValue().getStyleClass().remove("nav-activa");
            if (par.getKey().equals(nombre)) {
                par.getValue().getStyleClass().add("nav-activa");
            }
        }
    }
}
