package co.edu.eia.makerlab.vista;

import java.time.LocalTime;

import co.edu.eia.makerlab.modelo.Equipo;
import co.edu.eia.makerlab.modelo.Reserva;
import co.edu.eia.makerlab.modelo.SesionUso;
import co.edu.eia.makerlab.modelo.SolicitudEspera;
import co.edu.eia.makerlab.modelo.Usuario;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.patron.EstrategiaFifo;
import co.edu.eia.makerlab.patron.EstrategiaProfesoresPrimero;
import co.edu.eia.makerlab.servicio.RegistroService;
import co.edu.eia.makerlab.servicio.ReservaService;
import co.edu.eia.makerlab.excepcion.CruceHorarioException;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Reservas, sesiones de uso y lista de espera. */
public class VistaReservas extends Vista {

    private RegistroService registro = new RegistroService();
    private ReservaService reservas = new ReservaService();
    private TableView<Reserva> tabla = new TableView<>();
    private TableView<SolicitudEspera> espera = new TableView<>();
    private ComboBox<String> usuario = new ComboBox<>();
    private ComboBox<String> equipo = new ComboBox<>();
    private ComboBox<String> estrategia = new ComboBox<>(
            FXCollections.observableArrayList("Orden de llegada", "Profesores primero"));

    public VistaReservas() {
        super("Reservas", "Crear y cancelar reservas, registrar el uso del equipo y ver la lista de espera.");

        tabla.getColumns().add(columna("Id", Reserva::getId));
        tabla.getColumns().add(columna("Usuario", r -> r.getUsuario().getNombre()));
        tabla.getColumns().add(columna("Equipo", r -> r.getEquipo().getCodigo()));
        tabla.getColumns().add(columna("Fecha", r -> r.getFecha().toString()));
        tabla.getColumns().add(columna("Horario", r -> r.getHoraInicio() + " a " + r.getHoraFin()));
        tabla.getColumns().add(columna("Estado", r -> r.getEstado() == null ? "" : r.getEstado().name().toLowerCase()));
        tabla.getColumns().add(columna("Uso", VistaReservas::textoSesion));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        var cancelar = botonSecundario("Cancelar reserva");
        cancelar.setOnAction(e -> ejecutar(() -> {
            SolicitudEspera avisada = reservas.cancelarReserva(seleccionada());
            if (avisada != null) {
                aviso("Reserva cancelada. Se avisó a " + avisada.getUsuario().getNombre()
                        + ", que esperaba esa franja.");
            }
        }));
        var abrir = botonSecundario("Abrir sesión");
        abrir.setOnAction(e -> {
            Reserva reserva = tabla.getSelectionModel().getSelectedItem();
            String[] datos = pedirDatos("Abrir sesión de uso", "Hora real de inicio (HH:mm)");
            if (datos != null) {
                ejecutar(() -> {
                    if (reserva == null) {
                        throw new IllegalArgumentException("seleccione una reserva en la tabla.");
                    }
                    SesionUso sesion = reservas.abrirSesion(reserva, LocalTime.parse(datos[0]));
                    aviso("Sesión " + sesion.getId() + " abierta.");
                });
            }
        });
        var cerrar = botonSecundario("Cerrar sesión");
        cerrar.setOnAction(e -> cerrarSesion());

        VBox izquierda = panel("Reservas registradas", tabla, new HBox(8, cancelar, abrir, cerrar));

        espera.getColumns().add(columna("Solicitud", SolicitudEspera::getId));
        espera.getColumns().add(columna("Usuario", s -> s.getUsuario().getNombre()));
        espera.getColumns().add(columna("Equipo", s -> s.getEquipo().getCodigo()));
        espera.getColumns().add(columna("Franja", s -> s.getFecha() + "  " + s.getHoraInicio() + " a " + s.getHoraFin()));
        espera.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        espera.setPrefHeight(150);
        estrategia.setOnAction(e -> CatalogoMakerLab.getInstancia().setEstrategiaEspera(
                estrategia.getValue().equals("Profesores primero") ? new EstrategiaProfesoresPrimero()
                        : new EstrategiaFifo()));
        izquierda.getChildren().add(campo("Lista de espera (al liberarse una franja se avisa según el criterio elegido)",
                new VBox(8, estrategia, espera)));

        getChildren().add(dosColumnas(izquierda, formulario()));
        refrescar();
    }

    private VBox formulario() {
        DatePicker fecha = new DatePicker(CatalogoMakerLab.getInstancia().getFechaActual().plusDays(1));
        TextField inicio = new TextField("10:00");
        TextField fin = new TextField("12:00");
        TextField proposito = new TextField();

        var crear = botonPrincipal("Crear reserva");
        crear.setOnAction(e -> {
            try {
                Usuario u = registro.buscarUsuario(idDe(usuario.getValue()));
                Equipo eq = registro.buscarEquipo(idDe(equipo.getValue()));
                Reserva nueva = reservas.crearReserva(u, eq, fecha.getValue(), LocalTime.parse(inicio.getText().trim()),
                        LocalTime.parse(fin.getText().trim()), proposito.getText().trim());
                proposito.clear();
                aviso("Reserva " + nueva.getId() + " creada.");
            } catch (CruceHorarioException ex) {
                // El cruce no es un error del usuario: la solicitud quedó en espera.
                aviso(ex.getMessage());
            } catch (Exception ex) {
                error(ex.getMessage());
            }
            ejecutar(() -> { }); // guarda (también la lista de espera) y refresca
        });
        return panel("Nueva reserva", campo("Usuario", usuario), campo("Equipo", equipo),
                campo("Fecha", fecha), fila(campo("Desde", inicio), campo("Hasta", fin)),
                campo("Propósito", proposito), crear);
    }

    private void cerrarSesion() {
        Reserva reserva = tabla.getSelectionModel().getSelectedItem();
        String[] datos = pedirDatos("Cerrar sesión de uso", "Hora real de fin (HH:mm)", "Consumible usado (opcional)",
                "Incidente (opcional)", "Observaciones");
        if (datos == null) {
            return;
        }
        ejecutar(() -> {
            if (reserva == null || reserva.getSesionUso() == null) {
                throw new IllegalArgumentException("seleccione una reserva con sesión abierta.");
            }
            SesionUso sesion = reserva.getSesionUso();
            if (!datos[1].isEmpty()) {
                sesion.registrarConsumible(datos[1]);
            }
            if (!datos[2].isEmpty()) {
                sesion.registrarIncidente(datos[2]);
            }
            sesion.cerrar(LocalTime.parse(datos[0]), datos[3]);
        });
    }

    private Reserva seleccionada() {
        Reserva reserva = tabla.getSelectionModel().getSelectedItem();
        if (reserva == null) {
            throw new IllegalArgumentException("seleccione una reserva en la tabla.");
        }
        return reserva;
    }

    private static String textoSesion(Reserva r) {
        SesionUso sesion = r.getSesionUso();
        if (sesion == null) {
            return "Sin sesión";
        }
        return sesion.isActiva() ? "En uso desde " + sesion.getHoraInicioReal()
                : "Usado " + sesion.getDuracionMinutos() + " min";
    }

    /** De "U-010  Juan Pablo Gallego" toma "U-010". */
    private static String idDe(String opcion) {
        if (opcion == null) {
            throw new IllegalArgumentException("elija una opción en cada lista.");
        }
        return opcion.split(" ")[0];
    }

    @Override
    public void refrescar() {
        CatalogoMakerLab catalogo = CatalogoMakerLab.getInstancia();
        tabla.setItems(FXCollections.observableArrayList(catalogo.getReservas().listar()));
        espera.setItems(FXCollections.observableArrayList(catalogo.getListaEspera().listar()));
        String usuarioElegido = usuario.getValue();
        String equipoElegido = equipo.getValue();
        usuario.getItems().clear();
        for (Usuario u : catalogo.getUsuarios().listar()) {
            usuario.getItems().add(u.getId() + "  " + u.getNombre());
        }
        equipo.getItems().clear();
        for (Equipo eq : catalogo.getEquipos().listar()) {
            equipo.getItems().add(eq.getCodigo() + "  " + eq.getNombre());
        }
        usuario.setValue(usuarioElegido != null ? usuarioElegido : usuario.getItems().get(0));
        equipo.setValue(equipoElegido != null ? equipoElegido : equipo.getItems().get(0));
        estrategia.setValue(catalogo.getEstrategiaEspera() instanceof EstrategiaProfesoresPrimero
                ? "Profesores primero" : "Orden de llegada");
        tabla.refresh();
    }
}
