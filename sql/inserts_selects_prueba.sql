-- Paso 4 (opcional). Datos iniciales: son los mismos que carga la clase
-- DatosDemo. Si se omite este paso, la aplicación los inserta sola la
-- primera vez que encuentra la base vacía.
-- Las reservas, sesiones, tickets y solicitudes de espera los crea la
-- aplicación (demostración guiada o interfaz gráfica).

-- LABORATORIOS
INSERT INTO LABORATORIO (codigo, nombre, ubicacion, hora_apertura, hora_cierre, activo) VALUES
    ('LAB-01', 'Laboratorio de Prototipado', 'Bloque 19 - Piso 2', '07:00', '19:00', TRUE),
    ('LAB-02', 'Laboratorio de Electrónica', 'Bloque 20 - Piso 1', '08:00', '18:00', TRUE);

-- EQUIPOS (uno por tipo)
INSERT INTO EQUIPO (codigo, nombre, nivel_riesgo, estado, tipo_equipo, laboratorio_codigo) VALUES
    ('EQ-101', 'Creality K1',            'MEDIO', 'OPERATIVO', 'IMPRESORA_3D',         'LAB-01'),
    ('EQ-201', 'Trotec Speedy 100',      'ALTO',  'OPERATIVO', 'CORTADORA_LASER',      'LAB-02'),
    ('EQ-202', 'Estación Hakko FX-888D', 'BAJO',  'OPERATIVO', 'ESTACION_ELECTRONICA', 'LAB-02');

INSERT INTO IMPRESORA_3D (equipo_codigo, tecnologia, material_soportado) VALUES
    ('EQ-101', 'FDM', 'PLA/PETG');
INSERT INTO CORTADORA_LASER (equipo_codigo, potencia_watts, area_trabajo) VALUES
    ('EQ-201', 60, '610x305 mm');
INSERT INTO ESTACION_ELECTRONICA (equipo_codigo, voltaje_maximo, herramientas) VALUES
    ('EQ-202', 24.00, 'cautín, multímetro, osciloscopio');

-- USUARIOS
INSERT INTO USUARIO (id_usuario, nombre, correo, tipo_usuario) VALUES
    ('U-010', 'Juan Pablo Gallego',  'juan.gallego24@eia.edu.co',   'ESTUDIANTE'),
    ('U-011', 'Maria Camila Ricardo', 'maria.ricardo24@eia.edu.co',  'ESTUDIANTE'),
    ('U-012', 'Samuel Pérez',        'samuel.perez@eia.edu.co',     'ESTUDIANTE'),
    ('U-013', 'Isabella Navarro',    'isabella.navarro@eia.edu.co', 'ESTUDIANTE'),
    ('U-020', 'Johan Gabriel Vélez', 'johan.velez@eia.edu.co',      'PROFESOR'),
    ('U-030', 'Carlos Ramírez',      'carlos.ramirez@eia.edu.co',   'TECNICO');

INSERT INTO ESTUDIANTE (id_usuario, carrera, semestre) VALUES
    ('U-010', 'Ingeniería de Sistemas', 2),
    ('U-011', 'Ingeniería de Sistemas', 2),
    ('U-012', 'Ingeniería de Sistemas', 2),
    ('U-013', 'Ingeniería de Sistemas', 2);
INSERT INTO PROFESOR (id_usuario, departamento) VALUES
    ('U-020', 'Ingeniería de Sistemas y Computación');
INSERT INTO TECNICO (id_usuario, especialidad) VALUES
    ('U-030', 'Mantenimiento de equipos de fabricación digital');

-- CERTIFICACIONES
INSERT INTO CERTIFICACION (id_certificacion, id_usuario, tipo_equipo_autorizado, fecha_emision, fecha_vencimiento, activo) VALUES
    ('C-045', 'U-010', 'IMPRESORA_3D',         '2026-02-10', '2026-12-15', TRUE),
    ('C-046', 'U-011', 'IMPRESORA_3D',         '2026-03-01', '2026-12-15', TRUE),
    ('C-047', 'U-011', 'ESTACION_ELECTRONICA', '2025-08-01', '2026-08-01', TRUE),  -- vencida
    ('C-048', 'U-012', 'CORTADORA_LASER',      '2025-09-30', '2026-09-30', TRUE),  -- vence pronto
    ('C-049', 'U-013', 'ESTACION_ELECTRONICA', '2026-03-01', '2027-03-01', TRUE),
    ('C-050', 'U-020', 'IMPRESORA_3D',         '2026-03-01', '2027-03-01', TRUE);

-- CONSULTAS DE VERIFICACIÓN
SELECT u.id_usuario, u.nombre, u.tipo_usuario,
       COALESCE(e.carrera, p.departamento, t.especialidad) AS detalle
FROM USUARIO u
LEFT JOIN ESTUDIANTE e ON e.id_usuario = u.id_usuario
LEFT JOIN PROFESOR   p ON p.id_usuario = u.id_usuario
LEFT JOIN TECNICO    t ON t.id_usuario = u.id_usuario
ORDER BY u.id_usuario;

SELECT e.codigo, e.nombre, e.tipo_equipo, e.estado, l.nombre AS laboratorio
FROM EQUIPO e JOIN LABORATORIO l ON l.codigo = e.laboratorio_codigo
ORDER BY e.codigo;

-- Certificaciones vigentes al 2026-09-08 (fecha de la demostración)
SELECT c.id_certificacion, u.nombre, c.tipo_equipo_autorizado, c.fecha_vencimiento
FROM CERTIFICACION c JOIN USUARIO u ON u.id_usuario = c.id_usuario
WHERE c.activo AND DATE '2026-09-08' BETWEEN c.fecha_emision AND c.fecha_vencimiento;

-- PRUEBAS DE RESTRICCIONES
-- Cada una debe FALLAR. Ejecútenlas de a una para ver el error.
-- INSERT INTO USUARIO VALUES ('U-099', 'Otro', 'samuel.perez@eia.edu.co', 'ESTUDIANTE');           -- correo repetido
-- INSERT INTO RESERVA VALUES ('R-999', 'U-010', 'EQ-101', '2026-09-10', '11:00', '09:00', 'x', 'CONFIRMADA'); -- hora fin antes de inicio
-- INSERT INTO CORTADORA_LASER (equipo_codigo, potencia_watts, area_trabajo) VALUES ('EQ-101', 40, 'x'); -- EQ-101 es impresora
-- INSERT INTO EQUIPO VALUES ('EQ-999', 'X', 'MEDIO', 'ROTO', 'IMPRESORA_3D', 'LAB-01');            -- estado inválido
-- INSERT INTO ESTUDIANTE (id_usuario, carrera, semestre) VALUES ('U-020', 'x', 1);                  -- U-020 es profesor
-- INSERT INTO EQUIPO VALUES ('EQ-998', 'X', 'BAJO', 'OPERATIVO', 'IMPRESORA_3D', NULL);           -- equipo sin laboratorio
