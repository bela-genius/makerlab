package co.edu.eia.makerlab.vista;

import java.util.LinkedHashMap;
import java.util.Map;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import atlantafx.base.theme.PrimerLight;
import co.edu.eia.makerlab.app.Arranque;
import co.edu.eia.makerlab.excepcion.MakerLabException;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Ventana principal: barra lateral de íconos con las secciones y, a la
 * derecha, la sección elegida. Usa los mismos servicios que el menú de consola.
 * El tema (AtlantaFX) y la barra lateral siguen el estilo de la plantilla KickstartFX.
 *
 * Para ejecutarla desde Eclipse se usa Lanzador (ver su comentario).
 */
public class AppFX extends Application {

    private static final Map<String, Ikon> ICONOS = Map.of(
            "Inicio", MaterialDesignH.HOME_OUTLINE,
            "Usuarios", MaterialDesignA.ACCOUNT_GROUP_OUTLINE,
            "Equipos", MaterialDesignP.PRINTER_3D,
            "Reservas", MaterialDesignC.CALENDAR_MONTH_OUTLINE,
            "Mantenimiento", MaterialDesignW.WRENCH_OUTLINE,
            "Indicadores", MaterialDesignC.CHART_BAR);

    private BorderPane raiz = new BorderPane();
    private ScrollPane contenido = new ScrollPane();
    private Map<String, Vista> vistas = new LinkedHashMap<>();
    private Map<String, Button> botones = new LinkedHashMap<>();
    private String mensajeArranque;

    @Override
    public void start(Stage ventana) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        // Tipografía Inter, la misma de la plantilla KickstartFX.
        Font.loadFont(getClass().getResourceAsStream("Inter-Regular.ttf"), 13);
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
        ventana.setTitle("MakerLab EIA · Laboratorios de fabricación");
        ventana.setMinWidth(1100);
        ventana.setMinHeight(680);
        ventana.setScene(escena);
        ventana.show();
    }

    private VBox barraLateral() {
        VBox lateral = new VBox();
        lateral.getStyleClass().add("lateral");

        FontIcon logo = new FontIcon(MaterialDesignF.FACTORY);
        logo.setIconSize(28);
        logo.getStyleClass().add("lateral-logo");
        Tooltip.install(logo, new Tooltip("MakerLab EIA\nLaboratorios de fabricación"));
        VBox.setMargin(logo, new Insets(6, 0, 12, 0));
        lateral.getChildren().add(logo);

        for (String nombre : vistas.keySet()) {
            FontIcon icono = new FontIcon(ICONOS.get(nombre));
            icono.setIconSize(24);
            Button boton = new Button(nombre, icono);
            boton.setContentDisplay(ContentDisplay.TOP);
            boton.setGraphicTextGap(5);
            boton.getStyleClass().add("nav");
            boton.setMaxWidth(Double.MAX_VALUE);
            boton.setOnAction(e -> mostrar(nombre));
            botones.put(nombre, boton);
            lateral.getChildren().add(boton);
        }

        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);
        boolean hayBase = Arranque.hayBaseDeDatos();
        FontIcon estado = new FontIcon(hayBase ? MaterialDesignD.DATABASE_OUTLINE : MaterialDesignD.DATABASE_OFF_OUTLINE);
        estado.setIconSize(22);
        estado.getStyleClass().add(hayBase ? "estado-ok" : "estado-sin-base");
        String modo = hayBase ? "Datos en PostgreSQL" : "Datos en memoria (sin base de datos)";
        Tooltip.install(estado, new Tooltip(modo + "\n" + mensajeArranque)); // el detalle aparece al pasar el mouse
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
