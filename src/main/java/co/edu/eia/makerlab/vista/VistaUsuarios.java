package co.edu.eia.makerlab.vista;

import co.edu.eia.makerlab.modelo.Certificacion;
import co.edu.eia.makerlab.modelo.Estudiante;
import co.edu.eia.makerlab.modelo.Profesor;
import co.edu.eia.makerlab.modelo.Tecnico;
import co.edu.eia.makerlab.modelo.TipoEquipo;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.servicio.RegistroService;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Usuarios con sus certificaciones y notificaciones. */
public class VistaUsuarios extends Vista {

    private RegistroService registro = new RegistroService();
    private TableView<Usuario> tabla = new TableView<>();
    private ListView<String> certificaciones = new ListView<>();
    private ListView<String> notificaciones = new ListView<>();

    public VistaUsuarios() {
        super("Usuarios", "Estudiantes, profesores y técnicos, con sus certificaciones y avisos.");

        tabla.getColumns().add(columna("Id", Usuario::getId));
        tabla.getColumns().add(columna("Nombre", Usuario::getNombre));
        tabla.getColumns().add(columna("Correo", Usuario::getCorreo));
        tabla.getColumns().add(columna("Tipo", Vista::nombreTipo));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> mostrarDetalle(ahora));
        certificaciones.setPrefHeight(120);
        notificaciones.setPrefHeight(120);
        HBox detalle = new HBox(12, campo("Certificaciones del usuario", certificaciones),
                campo("Notificaciones recibidas", notificaciones));
        for (javafx.scene.Node n : detalle.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
        }
        VBox.setVgrow(tabla, Priority.ALWAYS);
        VBox izquierda = panel("Registrados", tabla, detalle);

        VBox derecha = new VBox(16, formularioUsuario(), formularioCertificacion());
        getChildren().add(dosColumnas(izquierda, derecha));
        refrescar();
    }

    private VBox formularioUsuario() {
        ComboBox<String> tipo = new ComboBox<>(FXCollections.observableArrayList("Estudiante", "Profesor", "Técnico"));
        tipo.setValue("Estudiante");
        TextField nombre = new TextField();
        TextField correo = new TextField();
        correo.setPromptText("nombre@eia.edu.co");
        TextField dato = new TextField();
        dato.setPromptText("Carrera");
        TextField semestre = new TextField();
        semestre.setPromptText("Semestre (solo estudiantes)");
        tipo.setOnAction(e -> {
            String elegido = tipo.getValue();
            dato.setPromptText(elegido.equals("Estudiante") ? "Carrera"
                    : elegido.equals("Profesor") ? "Departamento" : "Especialidad");
            semestre.setDisable(!elegido.equals("Estudiante"));
        });

        var registrar = botonPrincipal("Registrar usuario");
        registrar.setOnAction(e -> ejecutar(() -> {
            String id = registro.siguienteIdUsuario(); // el id lo genera el sistema
            Usuario usuario;
            if (tipo.getValue().equals("Estudiante")) {
                usuario = new Estudiante(id, nombre.getText().trim(), correo.getText().trim(),
                        dato.getText().trim(), Integer.parseInt(semestre.getText().trim()));
            } else if (tipo.getValue().equals("Profesor")) {
                usuario = new Profesor(id, nombre.getText().trim(), correo.getText().trim(),
                        dato.getText().trim());
            } else {
                usuario = new Tecnico(id, nombre.getText().trim(), correo.getText().trim(),
                        dato.getText().trim());
            }
            registro.registrarUsuario(usuario);
            aviso("Usuario " + id + " registrado.");
            nombre.clear();
            correo.clear();
            dato.clear();
            semestre.clear();
        }));
        return panel("Nuevo usuario", campo("Tipo", tipo), campo("Nombre", nombre),
                campo("Correo", correo), campo("Detalle", dato), semestre, registrar);
    }

    private VBox formularioCertificacion() {
        ComboBox<TipoEquipo> tipo = new ComboBox<>(FXCollections.observableArrayList(TipoEquipo.values()));
        tipo.setValue(TipoEquipo.IMPRESORA_3D);
        DatePicker emision = new DatePicker(CatalogoMakerLab.getInstancia().getFechaActual());
        DatePicker vencimiento = new DatePicker(CatalogoMakerLab.getInstancia().getFechaActual().plusYears(1));

        var registrar = botonPrincipal("Certificar al usuario seleccionado");
        registrar.setOnAction(e -> ejecutar(() -> {
            Usuario usuario = tabla.getSelectionModel().getSelectedItem();
            if (usuario == null) {
                throw new IllegalArgumentException("seleccione un usuario en la tabla.");
            }
            String id = registro.siguienteIdCertificacion();
            registro.registrarCertificacion(new Certificacion(id, emision.getValue(), vencimiento.getValue(),
                    usuario, tipo.getValue()));
            aviso("Certificación " + id + " registrada para " + usuario.getNombre() + ".");
        }));
        return panel("Nueva certificación", campo("Tipo de equipo", tipo),
                fila(campo("Emisión", emision), campo("Vencimiento", vencimiento)), registrar);
    }

    private void mostrarDetalle(Usuario usuario) {
        certificaciones.getItems().clear();
        notificaciones.getItems().clear();
        if (usuario == null) {
            return;
        }
        for (Certificacion c : CatalogoMakerLab.getInstancia().getCertificaciones().listar()) {
            if (c.getUsuario() == usuario) {
                String vigente = c.esVigente(CatalogoMakerLab.getInstancia().getFechaActual()) ? "vigente" : "no vigente";
                certificaciones.getItems().add(c.getId() + "  " + c.getTipoEquipoAutorizado() + ", hasta "
                        + c.getFechaVencimiento() + " (" + vigente + ")");
            }
        }
        notificaciones.getItems().addAll(usuario.getNotificaciones());
    }

    @Override
    public void refrescar() {
        Usuario seleccionado = tabla.getSelectionModel().getSelectedItem();
        tabla.setItems(FXCollections.observableArrayList(CatalogoMakerLab.getInstancia().getUsuarios().listar()));
        if (seleccionado != null) {
            tabla.getSelectionModel().select(seleccionado);
        }
        mostrarDetalle(tabla.getSelectionModel().getSelectedItem());
    }
}
