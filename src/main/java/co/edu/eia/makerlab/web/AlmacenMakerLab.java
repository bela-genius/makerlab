package co.edu.eia.makerlab.web;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import co.edu.eia.makerlab.app.DatosDemo;
import co.edu.eia.makerlab.excepcion.MakerLabException;
import co.edu.eia.makerlab.modelo.EstrategiaPrioridad;
import co.edu.eia.makerlab.patron.CatalogoMakerLab;
import co.edu.eia.makerlab.persistencia.PersistenciaPostgres;
import co.edu.eia.makerlab.servicio.ConsultaService;
import co.edu.eia.makerlab.servicio.MantenimientoService;
import co.edu.eia.makerlab.servicio.RegistroService;
import co.edu.eia.makerlab.servicio.ReservaService;

/**
 * Pone un candado alrededor del catálogo y de la base de datos.
 *
 * Por qué hace falta: CatalogoMakerLab es un Singleton con colecciones que no
 * son seguras entre hilos, y PersistenciaPostgres.guardar() reescribe TODA la
 * base (borra las 18 tablas y las vuelve a llenar) después de cada operación.
 * Con varias personas a la vez, dos guardar() simultáneos se pisarían y dos
 * reservas podrían tomar la misma franja.
 *
 * Solución: un solo candado de lectura/escritura (justo, en orden de llegada).
 * - Las lecturas (GET) pueden ir en paralelo entre sí.
 * - Cada escritura es exclusiva: valida, cambia la memoria y guarda en
 *   PostgreSQL antes de que entre la siguiente. Así la memoria y la base
 *   siempre dicen lo mismo y las reglas de cruce de horario se evalúan
 *   una a una.
 * - Si guardar() falla, se descarta la memoria y se recarga desde la base,
 *   para que nunca quede un cambio que no se guardó.
 *
 * Supuesto: hay UNA sola instancia de la aplicación (plan gratuito de Render).
 * Con dos instancias la memoria de cada una se desfasaría; ver docs/DESPLIEGUE.md.
 */
public class AlmacenMakerLab {

    /** Lo que se ejecuta bajo el candado. Puede lanzar las excepciones del sistema. */
    @FunctionalInterface
    public interface Operacion<T> {
        T ejecutar() throws MakerLabException;
    }

    /** Fecha fija del sistema para la versión web (la demostración da siempre el mismo resultado). */
    private static final LocalDate FECHA_SISTEMA = LocalDate.of(2026, 10, 28);

    private final ReentrantReadWriteLock candado =new ReentrantReadWriteLock(true);
    private final CatalogoMakerLab catalogo = CatalogoMakerLab.getInstancia();
    private final RegistroService registro = new RegistroService();
    private final ReservaService reservas = new ReservaService();
    private final MantenimientoService mantenimiento = new MantenimientoService();
    private final ConsultaService consultas = new ConsultaService();

    private PersistenciaPostgres persistencia; // null = solo memoria
    private String mensajeArranque = "";
    private volatile boolean desincronizado = false;

    private final AtomicLong guardados = new AtomicLong();
    private volatile long ultimoGuardadoMs = -1;
    private volatile Instant ultimoGuardado;

    // --------------------------------------------------------------- servicios

    public RegistroService registro() {
        return registro;
    }

    public ReservaService reservas() {
        return reservas;
    }

    public MantenimientoService mantenimiento() {
        return mantenimiento;
    }

    public ConsultaService consultas() {
        return consultas;
    }

    public CatalogoMakerLab catalogo() {
        return catalogo;
    }

    // ----------------------------------------------------------------- arranque

    /**
     * Prepara los datos. Con base de datos configurada: reintenta la conexión
     * (Neon tarda unos segundos en despertar), crea las tablas si la base está
     * vacía, y carga lo guardado o siembra los datos de demostración.
     * Sin configuración: trabaja en memoria (útil para probar en el portátil).
     *
     * Si hay base configurada pero no responde, falla en vez de arrancar "en
     * memoria" sin avisar: en una exposición sería perder datos sin enterarse.
     * Con ALLOW_MEMORY_FALLBACK=true se permite el comportamiento de escritorio.
     */
    public String iniciar() throws MakerLabException {
        candado.writeLock().lock();
        try {
            catalogo.reiniciar();
            ConfiguracionEntorno.Resultado config = ConfiguracionEntorno.resolver(System.getenv(), Path.of(".env"));
            if (config == null) {
                DatosDemo.cargarDatosBase();
                catalogo.setFechaActual(FECHA_SISTEMA);
                mensajeArranque = "Sin base de datos configurada (DATABASE_URL). Los datos están en memoria y se "
                        + "pierden al reiniciar.";
                return mensajeArranque;
            }
            PersistenciaPostgres base = new PersistenciaPostgres(config.getConfiguracion());
            int intentos = Integer.parseInt(System.getenv().getOrDefault("DB_CONNECT_RETRIES", "8"));
            boolean responde = false;
            for (int i = 1; i <= intentos && !responde; i++) {
                responde = base.probarConexion();
                if (!responde && i < intentos) {
                    System.out.println("[arranque] La base no responde (intento " + i + " de " + intentos
                            + "). Reintentando en 3 s...");
                    dormir(3000);
                }
            }
            if (!responde) {
                if ("true".equalsIgnoreCase(System.getenv("ALLOW_MEMORY_FALLBACK"))) {
                    DatosDemo.cargarDatosBase();
                    catalogo.setFechaActual(FECHA_SISTEMA);
                    mensajeArranque = "No se pudo conectar a " + config.getOrigen() + ". Se trabaja en memoria.";
                    return mensajeArranque;
                }
                throw new MakerLabException("No se pudo conectar a la base de datos (" + config.getOrigen()
                        + ") después de " + intentos + " intentos. Revise DATABASE_URL.");
            }
            String esquema = EsquemaBD.asegurar(config.getConfiguracion(),
                    Path.of(System.getenv().getOrDefault("SQL_DIR", "sql")));
            persistencia = base;
            if (base.estaVacia()) {
                DatosDemo.cargarDatosBase();
                catalogo.setFechaActual(FECHA_SISTEMA);
                base.guardar(catalogo);
                mensajeArranque = esquema + " Se guardaron los datos de demostración en PostgreSQL ("
                        + config.getOrigen() + ").";
            } else {
                base.cargar(catalogo);
                catalogo.setFechaActual(FECHA_SISTEMA);
                mensajeArranque = "Datos cargados desde PostgreSQL (" + config.getOrigen() + ").";
            }
            return mensajeArranque;
        } catch (SQLException e) {
            persistencia = null;
            throw new MakerLabException("Error con la base de datos al arrancar: " + e.getMessage());
        } finally {
            candado.writeLock().unlock();
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ acceso

    /** Lectura: varias a la vez, nunca mientras alguien escribe. */
    public <T> T leer(Operacion<T> operacion) throws MakerLabException {
        if (desincronizado) {
            candado.writeLock().lock();
            try {
                sincronizarSiHaceFalta();
            } finally {
                candado.writeLock().unlock();
            }
        }
        candado.readLock().lock();
        try {
            return operacion.ejecutar();
        } finally {
            candado.readLock().unlock();
        }
    }

    /**
     * Escritura exclusiva: ejecuta, y guarda en PostgreSQL si la operación
     * llegó a tocar el modelo (salió bien o fue rechazada por una regla del
     * sistema; el cruce de horario, por ejemplo, agrega a la lista de espera
     * aunque rechace la reserva). Los errores de datos de la capa web
     * (ApiError) ocurren antes de tocar el modelo y no guardan nada.
     */
    public <T> T escribir(Operacion<T> operacion) throws MakerLabException {
        candado.writeLock().lock();
        try {
            sincronizarSiHaceFalta();
            T resultado;
            try {
                resultado = operacion.ejecutar();
            } catch (ApiError e) {
                throw e;
            } catch (MakerLabException e) {
                persistir();
                throw e;
            } catch (IllegalArgumentException e) {
                // Los constructores del modelo validan antes de crear nada: no hubo cambios.
                throw e;
            } catch (RuntimeException e) {
                // Fallo inesperado: la memoria pudo quedar a medias. Se vuelve a lo guardado.
                resincronizar();
                throw e;
            }
            persistir();
            return resultado;
        } finally {
            candado.writeLock().unlock();
        }
    }

    /** Cambios que solo viven en memoria (como el criterio de la lista de espera). */
    public <T> T escribirSoloMemoria(Operacion<T> operacion) throws MakerLabException {
        candado.writeLock().lock();
        try {
            sincronizarSiHaceFalta();
            return operacion.ejecutar();
        } finally {
            candado.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------ persistencia

    private void persistir() {
        if (persistencia == null) {
            return;
        }
        long inicio = System.nanoTime();
        try {
            persistencia.guardar(catalogo);
            ultimoGuardadoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            ultimoGuardado = Instant.now();
            guardados.incrementAndGet();
        } catch (SQLException e) {
            System.err.println("[bd] No se pudo guardar: " + e.getMessage());
            resincronizar();
            throw new ApiError(503, "BaseDeDatos",
                    "No se pudo guardar en la base de datos. El cambio no se aplicó; intente de nuevo.");
        }
    }

    /** Descarta la memoria y vuelve a leer la base: lo guardado manda. */
    private void resincronizar() {
        if (persistencia == null) {
            return;
        }
        EstrategiaPrioridad estrategia = catalogo.getEstrategiaEspera();
        try {
            catalogo.reiniciar();
            persistencia.cargar(catalogo);
            catalogo.setFechaActual(FECHA_SISTEMA);
            catalogo.setEstrategiaEspera(estrategia);
            desincronizado = false;
        } catch (SQLException | MakerLabException | RuntimeException e) {
            System.err.println("[bd] No se pudo recargar desde la base: " + e.getMessage());
            desincronizado = true;
        }
    }

    private void sincronizarSiHaceFalta() {
        if (desincronizado) {
            resincronizar();
            if (desincronizado) {
                throw new ApiError(503, "BaseDeDatos",
                        "La base de datos no responde en este momento. Intente de nuevo en unos segundos.");
            }
        }
    }

    // ------------------------------------------------------------ modo demostración

    /**
     * Borra todo y restaura los datos de DatosDemo. Con escenario=true además
     * ejecuta el recorrido guiado de la demostración (reservas, rechazos,
     * tickets e indicadores ya llenos).
     */
    public String restaurarDemo(boolean escenario) throws MakerLabException {
        candado.writeLock().lock();
        try {
            try {
                catalogo.reiniciar();
                DatosDemo.cargarDatosBase();
                if (escenario) {
                    DatosDemo.ejecutarEscenario();
                }
                catalogo.setFechaActual(FECHA_SISTEMA);
            } catch (MakerLabException | RuntimeException e) {
                resincronizar();
                throw e;
            }
            persistir();
            return escenario ? "Datos de demostración restaurados, con el recorrido guiado ya ejecutado."
                    : "Datos de demostración restaurados (usuarios, laboratorios, equipos y certificaciones).";
        } finally {
            candado.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------------- estado

    public boolean hayBaseDeDatos() {
        return persistencia != null;
    }

    public String getMensajeArranque() {
        return mensajeArranque;
    }

    public long getGuardados() {
        return guardados.get();
    }

    public long getUltimoGuardadoMs() {
        return ultimoGuardadoMs;
    }

    public Instant getUltimoGuardado() {
        return ultimoGuardado;
    }

    public boolean isDesincronizado() {
        return desincronizado;
    }
}
