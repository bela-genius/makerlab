# MakerLab EIA, Entrega 2

Aplicación en Java para administrar los laboratorios maker de la Universidad EIA: usuarios, laboratorios, equipos, certificaciones, reservas, sesiones de uso, lista de espera, mantenimiento, notificaciones e indicadores. Tiene interfaz gráfica en JavaFX, menú de consola y guarda los datos en PostgreSQL.

Integrantes: Juan Pablo Gallego, Isabella Navarro, Maria Camila Ricardo, Samuel Pérez.

## Requisitos

- JDK 26.
- Eclipse IDE for Java Developers (incluye soporte para Maven).
- PostgreSQL 14 o superior, con pgAdmin o psql.
- Conexión a internet la primera vez, para que Maven descargue JUnit 5, JavaFX y el driver de PostgreSQL.

## 1. Crear la base de datos

Los scripts están en `sql/` y se ejecutan en este orden:

| Paso | Archivo | Dónde ejecutarlo |
|---|---|---|
| 1 | `crear_base.sql` | Conectado a la base `postgres` |
| 2 | `tablas.sql` | Conectado a `makerlab_poo` |
| 3 | `relaciones.sql` | Conectado a `makerlab_poo` |
| 4 | `inserts_selects_prueba.sql` (opcional) | Conectado a `makerlab_poo` |

En pgAdmin, el paso 1 se ejecuta sobre la base `postgres`. Luego se selecciona `makerlab_poo` en el panel izquierdo y se abre una nueva Query Tool para los pasos 2 a 4. Si se omite el paso 4, la aplicación inserta esos mismos datos la primera vez que encuentra la base vacía.

Al final de `inserts_selects_prueba.sql` hay seis inserciones comentadas que violan reglas del sistema. Si se ejecutan de a una, PostgreSQL debe rechazarlas todas.

## 2. Configurar la conexión

Copiar `.env.example` como `.env`, en la misma carpeta de `pom.xml`, y poner la clave del usuario de PostgreSQL:

```
DB_URL=jdbc:postgresql://localhost:5432/makerlab_poo
DB_USER=postgres
DB_PASSWORD=la_clave
```

El archivo `.env` no se entrega porque contiene la contraseña.

Si no hay `.env`, o la base no responde, el programa igual funciona: trabaja en memoria con los datos de demostración y lo indica en la barra lateral (interfaz gráfica) o en el encabezado del menú (consola).

## 3. Importar en Eclipse

El proyecto trae sus archivos de Eclipse (`.project`, `.classpath` y `.settings`), así que funciona cualquiera de las dos formas:

- **File → Import → Maven → Existing Maven Projects**, elegir la carpeta que contiene `pom.xml` y pulsar **Finish**.
- **File → Import → General → Existing Projects into Workspace**, elegir la misma carpeta y pulsar **Finish**.

Después hay que esperar a que Eclipse descargue las dependencias. Si aparecen errores de librerías: clic derecho sobre el proyecto → **Maven → Update Project**.

## 4. Ejecutar

- **Interfaz gráfica:** clic derecho sobre `src/main/java/co/edu/eia/makerlab/vista/Lanzador.java` → **Run As → Java Application**. También funciona `mvn javafx:run`.
- **Menú de consola:** clic derecho sobre `src/main/java/co/edu/eia/makerlab/app/Main.java` → **Run As → Java Application**. La opción 14 ejecuta la demostración guiada completa.

Cada operación que modifica datos se guarda enseguida en PostgreSQL. La fecha del sistema está fija en **2026-09-08**, para que la demostración dé siempre el mismo resultado.

Los ids de los registros nuevos (usuarios, laboratorios, equipos, certificaciones, reservas, tickets y sesiones) los genera el programa, con el número siguiente al más alto que exista: por ejemplo R-239 después de R-238. No hay que escribirlos ni en la interfaz ni en el menú.

Si en la consola de Eclipse las tildes salen mal: **Run Configurations → Common → Encoding → UTF-8**.

## 5. Pruebas

En Eclipse: clic derecho sobre el proyecto → **Run As → Maven test**. Desde una terminal: `mvn test`. Las pruebas no necesitan la base de datos. Deben pasar 38:

| Clase | Pruebas | Qué cubre |
|---|---|---|
| `servicio.ReservaServiceTest` | 13 | Reserva válida, cruce, solicitud repetida, franjas que solo se tocan, certificación vencida, equipo no operativo, horario del laboratorio, reserva que termina justo al cierre, id repetido, cancelación con y sin lista de espera, id generado para la reserva (R-239) y la sesión (S-090) después de la demostración |
| `servicio.MantenimientoServiceTest` | 6 | Ticket que bloquea el equipo y notifica, cierre sin acción, cierre correcto, técnico con mantenimientos cruzados, mantenimientos contiguos, id generado para el ticket (TK-003) |
| `servicio.ConsultaServiceTest` | 7 | Indicadores de la demostración, tasa sin reservas, certificación en el límite de aviso, disponibilidad, método genérico, `? super`, repositorio genérico con `? extends` |
| `modelo.ModeloTest` | 5 | Sesión con duración e incidentes, doble cierre, sesión de reserva cancelada, vigencia de certificación, lista de espera sin duplicados |
| `persistencia.ConfiguracionBDTest` | 3 | Lectura del `.env`, archivo inexistente, archivo incompleto |
| `repositorio.RepositorioTest` | 4 | Siguiente id (R-230 y R-238 → R-239), repositorio vacío (R-001), dos dígitos (LAB-03), ids con otro prefijo o sin número válido |

## Paquetes

| Paquete | Contenido |
|---|---|
| `modelo` | Clases del dominio, 4 enums y las interfaces `Identificable`, `ObservadorEquipo` y `EstrategiaPrioridad` |
| `repositorio` | `Repositorio<T extends Identificable>` |
| `servicio` | `RegistroService`, `ReservaService`, `MantenimientoService`, `ConsultaService` |
| `patron` | `CatalogoMakerLab` (Singleton), `EquipoFactory` (Factory), `EstrategiaFifo` y `EstrategiaProfesoresPrimero` (Strategy), `NotificadorReservas` (Observer) |
| `persistencia` | `ConfiguracionBD` (lee el `.env`) y `PersistenciaPostgres` (guarda y carga con JDBC) |
| `excepcion` | `MakerLabException` y sus tres subclases |
| `app` | `Main`, `MenuConsola`, `DatosDemo` y `Arranque` (carga los datos al iniciar) |
| `vista` | Interfaz JavaFX: `Lanzador`, `AppFX`, la clase base `Vista` y una pantalla por sección |

## Datos de demostración

Son los mismos de `DatosDemo`, de `sql/inserts_selects_prueba.sql` y de los diagramas de objetos.

| Tipo | Datos |
|---|---|
| Estudiantes | U-010 Juan Pablo Gallego, U-011 Maria Camila Ricardo, U-012 Samuel Pérez, U-013 Isabella Navarro |
| Profesor | U-020 Johan Gabriel Vélez |
| Técnico | U-030 Carlos Ramírez |
| Laboratorios | LAB-01 Laboratorio de Prototipado, 07:00-19:00; LAB-02 Laboratorio de Electrónica, 08:00-18:00 |
| Equipos | EQ-101 Creality K1 (impresora 3D, LAB-01); EQ-201 Trotec Speedy 100 (cortadora láser, LAB-02); EQ-202 Estación Hakko FX-888D (estación electrónica, LAB-02) |
| Certificaciones | C-045 Juan Pablo, impresora, hasta 2026-12-15; C-046 Maria Camila, impresora, hasta 2026-12-15; C-047 Maria Camila, estación, vencida el 2026-08-01; C-048 Samuel, cortadora, hasta 2026-09-30; C-049 Isabella, estación, hasta 2027-03-01; C-050 Johan, impresora, hasta 2027-03-01 |

## Demostración guiada (opción 14 del menú de consola)

1. **Polimorfismo.** Lista usuarios y equipos; cada subclase se muestra con su propio método.
2. **Reservas y sesiones.**
   - R-230 (Juan Pablo, EQ-101, 2026-09-10 14:00-16:00) queda confirmada. Su sesión S-088 va de 14:05 a 15:50, y cerrarla otra vez se rechaza.
   - Se confirman R-231 (Juan Pablo, EQ-101, 2026-09-11 10:00-12:00) y R-238 (Isabella, EQ-202); la sesión de R-238 registra un incidente.
3. **Rechazos.**
   - R-232 (Maria Camila) y R-233 (Johan) chocan con R-231 y quedan en espera como SOL-001 y SOL-002.
   - R-234 repite SOL-001 y no se agrega.
   - R-235 se rechaza por certificación vencida.
   - R-236 queda fuera del horario del laboratorio.
   - R-237 (Samuel) se rechaza porque EQ-201 está fuera de servicio.
4. **Mantenimiento.**
   - TK-001 deja EQ-101 en mantenimiento y Juan Pablo recibe un aviso por R-231.
   - TK-002 no se puede asignar a Carlos porque se cruza con TK-001.
   - TK-001 se cierra una vez registrada la acción.
5. **Lista de espera.** Con "profesores primero", cancelar R-231 avisa a Johan (SOL-002) antes que a Maria Camila (SOL-001).
6. **Consultas e indicadores.**
   - Horas de uso: impresora 1.75, cortadora 0, estación 1.5.
   - Incidentes: 1.
   - Tasa de cancelación: 33.3 %.
   - Tickets abiertos: 1, sin asignar.

Con base de datos, la demostración se ejecuta una sola vez, porque sus reservas quedan guardadas. Para repetirla hay que volver a ejecutar `tablas.sql`, `relaciones.sql` y, si se quiere, `inserts_selects_prueba.sql`.

## Diagramas

En `docs/` están el diagrama de clases y dos diagramas de objetos (escenario normal y cambio de estado). Cada uno tiene su archivo de draw.io (`.drawio`) y sus versiones SVG y PDF; `Diagramas_Objetos_Finales.pdf` reúne los dos escenarios.
