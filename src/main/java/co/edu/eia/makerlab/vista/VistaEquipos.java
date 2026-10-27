package co.edu.eia.makerlab.vista;

import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.Laboratorio;
import co.edu.eia.makerlab.modelo.TipoEquipo;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.patron.EquipoFactory;
import co.edu.eia.makerlab.servicio.RegistroService;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Equipos de todos los laboratorios. */
public class VistaEquipos extends Vista {

    private RegistroService registro = new RegistroService();
    private TableView<Equipo> tabla = new TableView<>();
    private ComboBox<String> laboratorio = new ComboBox<>();

    public VistaEquipos() {
        super("Equipos", "Impresoras 3D, cortadoras láser y estaciones de electrónica.");

        tabla.getColumns().add(columna("Código", Equipo::getCodigo));
        tabla.getColumns().add(columna("Nombre", Equipo::getNombre));
        tabla.getColumns().add(columna("Tipo", e -> nombreTipo(e.getTipoEquipo())));
        tabla.getColumns().add(columna("Lab.", e -> e.getLaboratorio().getCodigo()));
        tabla.getColumns().add(columna("Riesgo", e -> e.getNivelRiesgo().toLowerCase()));
        for (var columna : tabla.getColumns()) {
            columna.setMinWidth(70);
        }
        tabla.getColumns().add(columnaEstado("Estado", Equipo::getEstado));
        tabla.getColumns().get(tabla.getColumns().size() - 1).setMinWidth(140);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        var fueraDeServicio = botonSecundario("Marcar fuera de servicio");
        fueraDeServicio.setOnAction(e -> ejecutar(() -> {
            Equipo equipo = tabla.getSelectionModel().getSelectedItem();
            if (equipo == null) {
                throw new IllegalArgumentException("seleccione un equipo en la tabla.");
            }
            equipo.setEstado(EstadoEquipo.FUERA_DE_SERVICIO);
        }));
        VBox izquierda = panel("Inventario", tabla, new HBox(fueraDeServicio));

        getChildren().add(dosColumnas(izquierda, formulario()));
        refrescar();
    }

    private VBox formulario() {
        ComboBox<TipoEquipo> tipo = new ComboBox<>(FXCollections.observableArrayList(TipoEquipo.values()));
        tipo.setValue(TipoEquipo.IMPRESORA_3D);
        TextField nombre = new TextField();
        ComboBox<String> riesgo = new ComboBox<>(FXCollections.observableArrayList("BAJO", "MEDIO", "ALTO"));
        riesgo.setValue("MEDIO");
        TextField dato1 = new TextField();
        TextField dato2 = new TextField();
        actualizarAyuda(tipo.getValue(), dato1, dato2);
        tipo.setOnAction(e -> actualizarAyuda(tipo.getValue(), dato1, dato2));

        var registrar = botonPrincipal("Registrar equipo");
        registrar.setOnAction(e -> ejecutar(() -> {
            Laboratorio lab = registro.buscarLaboratorio(laboratorio.getValue());
            // Patrón Factory: la vista no necesita saber qué subclase se crea.
            String codigo = registro.siguienteIdEquipo(); // el código lo genera el sistema
            Equipo equipo = EquipoFactory.crear(tipo.getValue(), codigo, nombre.getText().trim(),
                    riesgo.getValue(), lab, dato1.getText().trim(), dato2.getText().trim());
            registro.registrarEquipo(equipo);
            aviso("Equipo " + codigo + " registrado.");
            nombre.clear();
            dato1.clear();
            dato2.clear();
        }));
        return panel("Nuevo equipo", campo("Tipo", tipo), campo("Nombre", nombre),
                fila(campo("Riesgo", riesgo), campo("Laboratorio", laboratorio)), campo("Características", dato1),
                dato2, registrar);
    }

    private void actualizarAyuda(TipoEquipo tipo, TextField dato1, TextField dato2) {
        if (tipo == TipoEquipo.IMPRESORA_3D) {
            dato1.setPromptText("Tecnología (FDM, SLA)");
            dato2.setPromptText("Material soportado");
        } else if (tipo == TipoEquipo.CORTADORA_LASER) {
            dato1.setPromptText("Potencia en watts (número entero)");
            dato2.setPromptText("Área de trabajo");
        } else {
            dato1.setPromptText("Voltaje máximo (por ejemplo 24.0)");
            dato2.setPromptText("Herramientas");
        }
    }

    @Override
    public void refrescar() {
        tabla.setItems(FXCollections.observableArrayList(CatalogoMakerLab.getInstancia().getEquipos().listar()));
        laboratorio.getItems().clear();
        for (Laboratorio lab : CatalogoMakerLab.getInstancia().getLaboratorios().listar()) {
            laboratorio.getItems().add(lab.getCodigo());
        }
        if (!laboratorio.getItems().isEmpty()) {
            laboratorio.setValue(laboratorio.getItems().get(0));
        }
        tabla.refresh();
    }
}
