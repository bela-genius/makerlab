package co.edu.eia.makerlab.vista;

import java.time.LocalTime;

import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.EstadoEquipo;
import co.edu.eia.makerlab.modelo.Prioridad;
import co.edu.eia.makerlab.modelo.Tecnico;
import co.edu.eia.makerlab.modelo.TicketMantenimiento;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.servicio.MantenimientoService;
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

/** Tickets de mantenimiento: crear, asignar técnico y cerrar. */
public class VistaMantenimiento extends Vista {

    private RegistroService registro = new RegistroService();
    private MantenimientoService mantenimiento = new MantenimientoService();
    private TableView<TicketMantenimiento> tabla = new TableView<>();
    private ListView<String> historial = new ListView<>();
    private ComboBox<String> equipo = new ComboBox<>();
    private ComboBox<String> tecnico = new ComboBox<>();

    public VistaMantenimiento() {
        super("Mantenimiento", "Al abrir un ticket el equipo queda bloqueado y se avisa a quienes lo tenían reservado.");

        tabla.getColumns().add(columna("Ticket", TicketMantenimiento::getId));
        tabla.getColumns().add(columna("Equipo", t -> t.getEquipo().getCodigo()));
        tabla.getColumns().add(columna("Prioridad", t -> t.getPrioridad().name().toLowerCase()));
        tabla.getColumns().add(columna("Técnico", t -> t.getTecnicoAsignado() == null ? "Sin asignar"
                : t.getTecnicoAsignado().getNombre()));
        tabla.getColumns().add(columna("Programado", t -> t.getFechaProgramada() + " " + t.getHoraProgramada()));
        tabla.getColumns().add(columna("Estado", t -> t.isCerrado() ? "Cerrado" : "Abierto"));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> mostrarHistorial(ahora));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        historial.setPrefHeight(130);

        var asignar = botonSecundario("Asignar");
        asignar.setOnAction(e -> ejecutar(() -> {
            Usuario u = registro.buscarUsuario(tecnico.getValue().split(" ")[0]);
            mantenimiento.asignarTecnico(seleccionado(), (Tecnico) u);
        }));
        var cerrar = botonSecundario("Cerrar ticket");
        cerrar.setOnAction(e -> cerrarTicket());
        HBox acciones = new HBox(8, tecnico, asignar, cerrar);
        HBox.setHgrow(tecnico, Priority.ALWAYS);

        VBox izquierda = panel("Tickets", tabla, acciones, campo("Historial del ticket seleccionado", historial));
        getChildren().add(dosColumnas(izquierda, formulario()));
        refrescar();
    }

    private VBox formulario() {
        ComboBox<Prioridad> prioridad = new ComboBox<>(FXCollections.observableArrayList(Prioridad.values()));
        prioridad.setValue(Prioridad.MEDIA);
        TextField descripcion = new TextField();
        DatePicker fecha = new DatePicker(CatalogoMakerLab.getInstancia().getFechaActual());
        TextField hora = new TextField("08:00");

        var crear = botonPrincipal("Abrir ticket");
        crear.setOnAction(e -> ejecutar(() -> {
            Equipo eq = registro.buscarEquipo(equipo.getValue().split(" ")[0]);
            TicketMantenimiento ticket = mantenimiento.crearTicket(prioridad.getValue(), descripcion.getText().trim(),
                    eq, fecha.getValue(), LocalTime.parse(hora.getText().trim()));
            descripcion.clear();
            aviso("Ticket " + ticket.getId() + " creado. " + eq.getCodigo() + " queda en mantenimiento.");
        }));
        return panel("Nuevo ticket", campo("Equipo", equipo), campo("Prioridad", prioridad),
                campo("Descripción de la falla", descripcion), fila(campo("Fecha", fecha), campo("Hora", hora)),
                crear);
    }

    private void cerrarTicket() {
        String[] datos = pedirDatos("Cerrar ticket", "Acción realizada", "¿Quedó reparado? (si / no)");
        if (datos == null) {
            return;
        }
        ejecutar(() -> {
            TicketMantenimiento ticket = seleccionado();
            ticket.registrarAccion(datos[0]);
            boolean reparado = datos[1].equalsIgnoreCase("si") || datos[1].equalsIgnoreCase("sí");
            ticket.cerrar(reparado ? EstadoEquipo.OPERATIVO : EstadoEquipo.FUERA_DE_SERVICIO);
        });
    }

    private TicketMantenimiento seleccionado() {
        TicketMantenimiento ticket = tabla.getSelectionModel().getSelectedItem();
        if (ticket == null) {
            throw new IllegalArgumentException("seleccione un ticket en la tabla.");
        }
        return ticket;
    }

    private void mostrarHistorial(TicketMantenimiento ticket) {
        historial.getItems().clear();
        if (ticket != null) {
            for (TicketMantenimiento.Cambio cambio : ticket.getHistorial()) {
                historial.getItems().add(cambio.getNumero() + ". " + cambio.getDescripcion());
            }
        }
    }

    @Override
    public void refrescar() {
        CatalogoMakerLab catalogo = CatalogoMakerLab.getInstancia();
        TicketMantenimiento elegido = tabla.getSelectionModel().getSelectedItem();
        tabla.setItems(FXCollections.observableArrayList(catalogo.getTickets().listar()));
        if (elegido != null) {
            tabla.getSelectionModel().select(elegido);
        }
        mostrarHistorial(tabla.getSelectionModel().getSelectedItem());

        String equipoElegido = equipo.getValue();
        equipo.getItems().clear();
        for (Equipo eq : catalogo.getEquipos().listar()) {
            equipo.getItems().add(eq.getCodigo() + "  " + eq.getNombre());
        }
        equipo.setValue(equipoElegido != null ? equipoElegido : equipo.getItems().get(0));
        tecnico.getItems().clear();
        for (Usuario u : catalogo.getUsuarios().listar()) {
            if (u instanceof Tecnico) {
                tecnico.getItems().add(u.getId() + "  " + u.getNombre());
            }
        }
        if (!tecnico.getItems().isEmpty()) {
            tecnico.setValue(tecnico.getItems().get(0));
        }
    }
}
