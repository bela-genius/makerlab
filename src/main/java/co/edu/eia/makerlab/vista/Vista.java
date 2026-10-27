package co.edu.eia.makerlab.vista;

import java.util.function.Function;

import co.edu.eia.makerlab.app.Arranque;
import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.Estudiante;
import co.edu.eia.makerlab.modelo.Profesor;
import co.edu.eia.makerlab.modelo.TipoEquipo;
import co.edu.eia.makerlab.modelo.Usuario;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Clase base de todas las pantallas. Cada pantalla hereda de aquí los
 * ayudantes para crear columnas, formularios y mensajes, y solo implementa
 * refrescar(), que vuelve a leer los datos del catálogo.
 */
public abstract class Vista extends VBox {

    private Label lblDescripcion = new Label();

    public Vista(String titulo, String descripcion) {
        getStyleClass().add("vista");
        setSpacing(16);
        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("titulo");
        lblDescripcion.setText(descripcion);
        lblDescripcion.getStyleClass().add("descripcion");
        getChildren().addAll(new VBox(2, lblTitulo, lblDescripcion));
    }

    protected void setDescripcion(String texto) {
        lblDescripcion.setText(texto);
    }

    /** Vuelve a cargar las tablas con los datos actuales. */
    public abstract void refrescar();

    /**
     * Ejecuta una acción que modifica datos. Si funciona, guarda en la base y
     * refresca la pantalla; si falla, muestra el motivo en una ventana.
     */
    protected void ejecutar(Accion accion) {
        try {
            accion.hacer();
            Arranque.guardar();
        } catch (MakerLabException e) {
            error(e.getMessage());
        } catch (RuntimeException e) {
            // Datos mal escritos: fecha, hora, número o un campo vacío.
            error("Revise los datos: " + e.getMessage());
        }
        refrescar();
    }

    /** Lo que hace un botón. Puede lanzar las excepciones del sistema. */
    protected interface Accion {
        void hacer() throws MakerLabException;
    }

    // ---------- Ayudantes para construir la pantalla ----------

    /** Crea una columna de texto. La función dice qué mostrar de cada fila. */
    protected <T> TableColumn<T, String> columna(String titulo, Function<T, String> valor) {
        TableColumn<T, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(fila -> new SimpleStringProperty(valor.apply(fila.getValue())));
        return columna;
    }

    /** Columna que muestra el estado de un equipo como una etiqueta de color. */
    protected <T> TableColumn<T, String> columnaEstado(String titulo, Function<T, EstadoEquipo> estado) {
        TableColumn<T, String> columna = columna(titulo, fila -> estado.apply(fila).name());
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String valor, boolean vacia) {
                super.updateItem(valor, vacia);
                setText(null);
                setGraphic(vacia || valor == null ? null : pildora(EstadoEquipo.valueOf(valor)));
            }
        });
        return columna;
    }

    protected static Label pildora(EstadoEquipo estado) {
        String texto = switch (estado) {
            case OPERATIVO -> "Operativo";
            case EN_MANTENIMIENTO -> "En mantenimiento";
            case FUERA_DE_SERVICIO -> "Fuera de servicio";
            case INACTIVO -> "Inactivo";
        };
        Label pildora = new Label(texto);
        pildora.getStyleClass().addAll("pildora", "pildora-" + estado.name().toLowerCase().replace('_', '-'));
        return pildora;
    }

    /** Panel blanco con un subtítulo. */
    protected VBox panel(String subtitulo, javafx.scene.Node... contenido) {
        Label lbl = new Label(subtitulo);
        lbl.getStyleClass().add("subtitulo");
        VBox panel = new VBox(10, lbl);
        panel.getChildren().addAll(contenido);
        panel.getStyleClass().add("panel");
        return panel;
    }

    /** Dos columnas: la tabla crece a la izquierda y el formulario queda a la derecha. */
    protected HBox dosColumnas(VBox izquierda, VBox derecha) {
        HBox.setHgrow(izquierda, Priority.ALWAYS);
        derecha.setMinWidth(300);
        derecha.setPrefWidth(320);
        HBox fila = new HBox(16, izquierda, derecha);
        VBox.setVgrow(fila, Priority.ALWAYS);
        return fila;
    }

    /** Etiqueta pequeña encima de un campo de formulario. */
    protected VBox campo(String etiqueta, javafx.scene.Node control) {
        Label lbl = new Label(etiqueta);
        lbl.getStyleClass().add("etiqueta");
        return new VBox(4, lbl, control);
    }

    /** Pone varios campos lado a lado, repartiendo el ancho entre ellos. */
    protected HBox fila(javafx.scene.Node... campos) {
        HBox fila = new HBox(8, campos);
        for (javafx.scene.Node campo : campos) {
            HBox.setHgrow(campo, Priority.ALWAYS);
        }
        return fila;
    }

    protected static String nombreTipo(TipoEquipo tipo) {
        return switch (tipo) {
            case IMPRESORA_3D -> "Impresora 3D";
            case CORTADORA_LASER -> "Cortadora láser";
            case ESTACION_ELECTRONICA -> "Estación electrónica";
        };
    }

    protected static String nombreTipo(Usuario usuario) {
        if (usuario instanceof Estudiante) {
            return "Estudiante";
        }
        return usuario instanceof Profesor ? "Profesor" : "Técnico";
    }

    protected Button botonPrincipal(String texto) {
        Button boton = new Button(texto);
        boton.getStyleClass().add("boton-principal");
        boton.setMaxWidth(Double.MAX_VALUE);
        return boton;
    }

    protected Button botonSecundario(String texto) {
        Button boton = new Button(texto);
        boton.getStyleClass().add("boton-secundario");
        return boton;
    }

    // ---------- Mensajes ----------

    protected void error(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje);
        alerta.setHeaderText("No se pudo completar la operación");
        alerta.showAndWait();
    }

    protected void aviso(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alerta.setHeaderText(null);
        alerta.showAndWait();
    }

    /**
     * Ventana con varios campos de texto. Devuelve lo escrito, en el mismo
     * orden de las etiquetas, o null si el usuario canceló.
     */
    protected String[] pedirDatos(String titulo, String... etiquetas) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(titulo);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grilla = new GridPane();
        grilla.setHgap(10);
        grilla.setVgap(10);
        grilla.setPadding(new Insets(10));
        TextField[] campos = new TextField[etiquetas.length];
        for (int i = 0; i < etiquetas.length; i++) {
            campos[i] = new TextField();
            grilla.addRow(i, new Label(etiquetas[i]), campos[i]);
        }
        dialogo.getDialogPane().setContent(grilla);
        if (dialogo.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return null;
        }
        String[] valores = new String[campos.length];
        for (int i = 0; i < campos.length; i++) {
            valores[i] = campos[i].getText().trim();
        }
        return valores;
    }
}
