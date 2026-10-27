-- Paso 2. Ejecutar conectado a la base makerlab_poo.
-- Crea las tablas con sus llaves primarias y validaciones (CHECK, UNIQUE, NOT NULL).
-- Las llaves foráneas están en relaciones.sql.
--
-- Los identificadores son texto ("U-010", "EQ-101", "R-230") porque los
-- asigna la aplicación Java, igual que en el modelo de clases.
-- Se puede ejecutar varias veces: primero borra las tablas si ya existen.

DROP TABLE IF EXISTS TICKET_CAMBIO, TICKET_MANTENIMIENTO, SOLICITUD_ESPERA,
    SESION_INCIDENTE, SESION_CONSUMIBLE, SESION_USO, RESERVA, CERTIFICACION,
    NOTIFICACION, ESTUDIANTE, PROFESOR, TECNICO, USUARIO,
    IMPRESORA_3D, CORTADORA_LASER, ESTACION_ELECTRONICA, EQUIPO, LABORATORIO CASCADE;

---- LABORATORIOS Y EQUIPOS
CREATE TABLE LABORATORIO (
    codigo        VARCHAR(20)  PRIMARY KEY,
    nombre        VARCHAR(100) NOT NULL,
    ubicacion     VARCHAR(100) NOT NULL,
    hora_apertura TIME         NOT NULL,
    hora_cierre   TIME         NOT NULL,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    CHECK (hora_cierre > hora_apertura)
);

CREATE TABLE EQUIPO (
    codigo             VARCHAR(20)  PRIMARY KEY,
    nombre             VARCHAR(100) NOT NULL,
    nivel_riesgo       VARCHAR(10)  NOT NULL CHECK (nivel_riesgo IN ('BAJO', 'MEDIO', 'ALTO')),
    estado             VARCHAR(20)  NOT NULL
        CHECK (estado IN ('OPERATIVO', 'EN_MANTENIMIENTO', 'FUERA_DE_SERVICIO', 'INACTIVO')),
    tipo_equipo        VARCHAR(30)  NOT NULL
        CHECK (tipo_equipo IN ('IMPRESORA_3D', 'CORTADORA_LASER', 'ESTACION_ELECTRONICA')),
    laboratorio_codigo VARCHAR(20)  NOT NULL
);

-- Herencia: la llave de cada subtipo es la misma del equipo. La columna
-- tipo_equipo, fija con DEFAULT y CHECK, junto con la llave foránea doble de
-- relaciones.sql, impide que un equipo quede en dos subtipos a la vez.
CREATE TABLE IMPRESORA_3D (
    equipo_codigo      VARCHAR(20)  PRIMARY KEY,
    tipo_equipo        VARCHAR(30)  NOT NULL DEFAULT 'IMPRESORA_3D' CHECK (tipo_equipo = 'IMPRESORA_3D'),
    tecnologia         VARCHAR(100) NOT NULL,
    material_soportado VARCHAR(100) NOT NULL
);

CREATE TABLE CORTADORA_LASER (
    equipo_codigo  VARCHAR(20)  PRIMARY KEY,
    tipo_equipo    VARCHAR(30)  NOT NULL DEFAULT 'CORTADORA_LASER' CHECK (tipo_equipo = 'CORTADORA_LASER'),
    potencia_watts INTEGER      NOT NULL CHECK (potencia_watts > 0),
    area_trabajo   VARCHAR(100) NOT NULL
);

CREATE TABLE ESTACION_ELECTRONICA (
    equipo_codigo  VARCHAR(20)   PRIMARY KEY,
    tipo_equipo    VARCHAR(30)   NOT NULL DEFAULT 'ESTACION_ELECTRONICA' CHECK (tipo_equipo = 'ESTACION_ELECTRONICA'),
    voltaje_maximo NUMERIC(6, 2) NOT NULL CHECK (voltaje_maximo > 0),
    herramientas   VARCHAR(200)  NOT NULL
);

---- USUARIOS
CREATE TABLE USUARIO (
    id_usuario   VARCHAR(20)  PRIMARY KEY,
    nombre       VARCHAR(100) NOT NULL,
    correo       VARCHAR(100) NOT NULL UNIQUE CHECK (correo LIKE '%@%'),
    tipo_usuario VARCHAR(20)  NOT NULL CHECK (tipo_usuario IN ('ESTUDIANTE', 'PROFESOR', 'TECNICO'))
);

CREATE TABLE ESTUDIANTE (
    id_usuario   VARCHAR(20)  PRIMARY KEY,
    tipo_usuario VARCHAR(20)  NOT NULL DEFAULT 'ESTUDIANTE' CHECK (tipo_usuario = 'ESTUDIANTE'),
    carrera      VARCHAR(100) NOT NULL,
    semestre     INTEGER      NOT NULL CHECK (semestre BETWEEN 1 AND 12)
);

CREATE TABLE PROFESOR (
    id_usuario   VARCHAR(20)  PRIMARY KEY,
    tipo_usuario VARCHAR(20)  NOT NULL DEFAULT 'PROFESOR' CHECK (tipo_usuario = 'PROFESOR'),
    departamento VARCHAR(100) NOT NULL
);

CREATE TABLE TECNICO (
    id_usuario   VARCHAR(20)  PRIMARY KEY,
    tipo_usuario VARCHAR(20)  NOT NULL DEFAULT 'TECNICO' CHECK (tipo_usuario = 'TECNICO'),
    especialidad VARCHAR(100) NOT NULL
);

-- Usuario.notificaciones (List<String>): una fila por mensaje, en orden.
CREATE TABLE NOTIFICACION (
    id_usuario VARCHAR(20)  NOT NULL,
    numero     INTEGER      NOT NULL,
    mensaje    VARCHAR(300) NOT NULL,
    PRIMARY KEY (id_usuario, numero)
);

CREATE TABLE CERTIFICACION (
    id_certificacion       VARCHAR(20) PRIMARY KEY,
    id_usuario             VARCHAR(20) NOT NULL,
    tipo_equipo_autorizado VARCHAR(30) NOT NULL
        CHECK (tipo_equipo_autorizado IN ('IMPRESORA_3D', 'CORTADORA_LASER', 'ESTACION_ELECTRONICA')),
    fecha_emision          DATE        NOT NULL,
    fecha_vencimiento      DATE        NOT NULL,
    activo                 BOOLEAN     NOT NULL DEFAULT TRUE,
    CHECK (fecha_vencimiento >= fecha_emision)
);

---- RESERVAS Y SESIONES
CREATE TABLE RESERVA (
    id_reserva    VARCHAR(20)  PRIMARY KEY,
    id_usuario    VARCHAR(20)  NOT NULL,
    equipo_codigo VARCHAR(20)  NOT NULL,
    fecha         DATE         NOT NULL,
    hora_inicio   TIME         NOT NULL,
    hora_fin      TIME         NOT NULL,
    proposito     VARCHAR(200) NOT NULL,
    estado        VARCHAR(20)  NOT NULL CHECK (estado IN ('CONFIRMADA', 'CANCELADA')),
    CHECK (hora_fin > hora_inicio)
);

CREATE TABLE SESION_USO (
    id_sesion        VARCHAR(20)  PRIMARY KEY,
    id_reserva       VARCHAR(20)  NOT NULL UNIQUE,  -- 1:1, una sesión por reserva
    hora_inicio_real TIME         NOT NULL,
    hora_fin_real    TIME,                           -- NULL mientras la sesión está activa
    observaciones    VARCHAR(200),
    activa           BOOLEAN      NOT NULL,
    CHECK (hora_fin_real IS NULL OR hora_fin_real > hora_inicio_real),
    CHECK (activa = (hora_fin_real IS NULL))
);

-- SesionUso.consumiblesUsados e incidentesReportados son listas: una fila por elemento.
CREATE TABLE SESION_CONSUMIBLE (
    id_sesion   VARCHAR(20)  NOT NULL,
    numero      INTEGER      NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    PRIMARY KEY (id_sesion, numero)
);

CREATE TABLE SESION_INCIDENTE (
    id_sesion   VARCHAR(20)  NOT NULL,
    numero      INTEGER      NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    PRIMARY KEY (id_sesion, numero)
);

CREATE TABLE SOLICITUD_ESPERA (
    id_solicitud    VARCHAR(20) PRIMARY KEY,
    id_usuario      VARCHAR(20) NOT NULL,
    equipo_codigo   VARCHAR(20) NOT NULL,
    fecha           DATE        NOT NULL,
    hora_inicio     TIME        NOT NULL,
    hora_fin        TIME        NOT NULL,
    fecha_solicitud DATE        NOT NULL,
    CHECK (hora_fin > hora_inicio),
    -- Regla 9: la misma persona no espera dos veces por el mismo equipo y franja.
    UNIQUE (id_usuario, equipo_codigo, fecha, hora_inicio, hora_fin)
);

---- MANTENIMIENTO
CREATE TABLE TICKET_MANTENIMIENTO (
    id_ticket        VARCHAR(20)  PRIMARY KEY,
    equipo_codigo    VARCHAR(20)  NOT NULL,
    id_tecnico       VARCHAR(20),                    -- NULL hasta que se asigna (0..1)
    prioridad        VARCHAR(10)  NOT NULL CHECK (prioridad IN ('BAJA', 'MEDIA', 'ALTA')),
    descripcion      VARCHAR(200) NOT NULL,
    accion_realizada VARCHAR(500),
    cerrado          BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_programada DATE         NOT NULL,
    hora_programada  TIME         NOT NULL,
    -- Regla 7: un ticket cerrado conserva el técnico y la acción realizada.
    CHECK (cerrado = FALSE OR (id_tecnico IS NOT NULL AND accion_realizada IS NOT NULL))
);

-- Historial del ticket (clase interna TicketMantenimiento.Cambio).
CREATE TABLE TICKET_CAMBIO (
    id_ticket   VARCHAR(20)  NOT NULL,
    numero      INTEGER      NOT NULL,
    descripcion VARCHAR(300) NOT NULL,
    PRIMARY KEY (id_ticket, numero)
);
