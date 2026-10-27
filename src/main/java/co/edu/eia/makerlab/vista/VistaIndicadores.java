package co.edu.eia.makerlab.vista;

import java.time.LocalTime;
import java.util.Map;

import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.TipoEquipo;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.servicio.ConsultaService;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Indicadores y consultas. Solo lee datos. */
public class VistaIndicadores extends Vista {

    private ConsultaService consultas = new ConsultaService();
    private BarChart<String, Number> grafico = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private Label incidentes = new Label();
    private Label cancelacion = new Label();
    private Label carga = new Label();
    private ListView<String> porVencer = new ListView<>();
    private ListView<String> fueraDeServicio = new ListView<>();
    private ListView<String> disponibles = new ListView<>();
    private ComboBox<String> laboratorio = new ComboBox<>();

    public VistaIndicadores() {
        super("Indicadores", "Uso de los equipos, incidentes, cancelaciones y consultas rápidas.");

        grafico.setLegendVisible(false);
        grafico.setAnimated(false);
        grafico.setPrefHeight(240);
        grafico.setCategoryGap(80);
        grafico.getYAxis().setLabel("Horas de uso");
        incidentes.getStyleClass().add("dato-grande");
        cancelacion.getStyleClass().add("dato-grande");
        carga.getStyleClass().add("nota");
        VBox cifras = new VBox(14, campo("Incidentes reportados", incidentes),
                campo("Reservas canceladas", cancelacion), campo("Tickets abiertos por técnico", carga));
        HBox fila1 = new HBox(16, panel("Horas de uso por tipo de equipo", grafico), panel("Resumen", cifras));
        HBox.setHgrow(fila1.getChildren().get(0), Priority.ALWAYS);
        ((VBox) fila1.getChildren().get(1)).setPrefWidth(260);

        porVencer.setPrefHeight(140);
        fueraDeServicio.setPrefHeight(140);
        disponibles.setPrefHeight(80);
        DatePicker fecha = new DatePicker(CatalogoMakerLab.getInstancia().getFechaActual());
        TextField desde = new TextField("10:00");
        TextField hasta = new TextField("12:00");
        desde.setPrefWidth(70);
        hasta.setPrefWidth(70);
        var consultar = botonSecundario("Consultar");
        consultar.setOnAction(e -> {
            try {
                Laboratorio lab = CatalogoMakerLab.getInstancia().getLaboratorios().buscar(laboratorio.getValue());
                disponibles.getItems().clear();
                for (Equipo eq : consultas.consultarDisponibilidad(lab, fecha.getValue(),
                        LocalTime.parse(desde.getText().trim()), LocalTime.parse(hasta.getText().trim()))) {
                    disponibles.getItems().add(eq.getCodigo() + "  " + eq.getNombre());
                }
                if (disponibles.getItems().isEmpty()) {
                    disponibles.getItems().add("Ningún equipo libre en esa franja.");
                }
            } catch (RuntimeException ex) {
                error("Revise la fecha y las horas (HH:mm).");
            }
        });
        HBox filtros = new HBox(8, laboratorio, fecha, desde, hasta, consultar);
        VBox disponibilidad = panel("Disponibilidad por laboratorio y franja", filtros, disponibles);

        HBox fila2 = new HBox(16, panel("Certificaciones que vencen en 30 días", porVencer),
                panel("Equipos fuera de servicio", fueraDeServicio));
        for (javafx.scene.Node n : fila2.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }
        getChildren().addAll(fila1, fila2, disponibilidad);
        refrescar();
    }

    @Override
    public void refrescar() {
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (Map.Entry<TipoEquipo, Double> dato : consultas.horasDeUsoPorTipo().entrySet()) {
            serie.getData().add(new XYChart.Data<>(nombreTipo(dato.getKey()), dato.getValue()));
        }
        grafico.getData().setAll(serie);

        incidentes.setText(String.valueOf(consultas.cantidadIncidentes()));
        cancelacion.setText(Math.round(consultas.tasaCancelacion() * 1000) / 10.0 + " %");
        String textoCarga = "";
        for (Map.Entry<String, Integer> dato : consultas.cargaMantenimiento().entrySet()) {
            textoCarga += (dato.getKey().equals("SIN ASIGNAR") ? "Sin asignar" : dato.getKey()) + ": " + dato.getValue() + "\n";
        }
        carga.setText(textoCarga.isEmpty() ? "Ninguno abierto" : textoCarga.trim());

        porVencer.getItems().clear();
        for (Certificacion c : consultas.consultarPorVencer(30)) {
            porVencer.getItems().add(c.getId() + "  " + c.getUsuario().getNombre() + ", vence " + c.getFechaVencimiento());
        }
        fueraDeServicio.getItems().clear();
        for (Equipo eq : consultas.consultarFueraDeServicio()) {
            fueraDeServicio.getItems().add(eq.getCodigo() + "  " + eq.getNombre() + " (" + eq.getEstado() + ")");
        }
        laboratorio.getItems().clear();
        for (Laboratorio lab : CatalogoMakerLab.getInstancia().getLaboratorios().listar()) {
            laboratorio.getItems().add(lab.getCodigo());
        }
        if (!laboratorio.getItems().isEmpty()) {
            laboratorio.setValue(laboratorio.getItems().get(0));
        }
    }
}
