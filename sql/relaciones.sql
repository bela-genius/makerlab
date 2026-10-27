-- Paso 3. Llaves foráneas. Ejecutar después de tablas.sql.
-- ON DELETE CASCADE solo en las tablas que son "parte" de otra
-- (subtipos, listas de una sesión o de un ticket, notificaciones).

---- Herencia (1:1 con la tabla padre)
-- La llave doble (id, tipo) obliga a que el subtipo coincida con el tipo del
-- padre: un usuario PROFESOR no puede tener fila en ESTUDIANTE.
ALTER TABLE USUARIO ADD CONSTRAINT uq_usuario_tipo UNIQUE (id_usuario, tipo_usuario);
ALTER TABLE EQUIPO  ADD CONSTRAINT uq_equipo_tipo  UNIQUE (codigo, tipo_equipo);

ALTER TABLE ESTUDIANTE ADD CONSTRAINT fk_estudiante_usuario
    FOREIGN KEY (id_usuario, tipo_usuario) REFERENCES USUARIO (id_usuario, tipo_usuario) ON DELETE CASCADE;
ALTER TABLE PROFESOR ADD CONSTRAINT fk_profesor_usuario
    FOREIGN KEY (id_usuario, tipo_usuario) REFERENCES USUARIO (id_usuario, tipo_usuario) ON DELETE CASCADE;
ALTER TABLE TECNICO ADD CONSTRAINT fk_tecnico_usuario
    FOREIGN KEY (id_usuario, tipo_usuario) REFERENCES USUARIO (id_usuario, tipo_usuario) ON DELETE CASCADE;

ALTER TABLE IMPRESORA_3D ADD CONSTRAINT fk_impresora_equipo
    FOREIGN KEY (equipo_codigo, tipo_equipo) REFERENCES EQUIPO (codigo, tipo_equipo) ON DELETE CASCADE;
ALTER TABLE CORTADORA_LASER ADD CONSTRAINT fk_cortadora_equipo
    FOREIGN KEY (equipo_codigo, tipo_equipo) REFERENCES EQUIPO (codigo, tipo_equipo) ON DELETE CASCADE;
ALTER TABLE ESTACION_ELECTRONICA ADD CONSTRAINT fk_estacion_equipo
    FOREIGN KEY (equipo_codigo, tipo_equipo) REFERENCES EQUIPO (codigo, tipo_equipo) ON DELETE CASCADE;

---- 1:N
ALTER TABLE EQUIPO ADD CONSTRAINT fk_equipo_laboratorio
    FOREIGN KEY (laboratorio_codigo) REFERENCES LABORATORIO (codigo);

ALTER TABLE NOTIFICACION ADD CONSTRAINT fk_notificacion_usuario
    FOREIGN KEY (id_usuario) REFERENCES USUARIO (id_usuario) ON DELETE CASCADE;

ALTER TABLE CERTIFICACION ADD CONSTRAINT fk_certificacion_usuario
    FOREIGN KEY (id_usuario) REFERENCES USUARIO (id_usuario);

ALTER TABLE RESERVA ADD CONSTRAINT fk_reserva_usuario
    FOREIGN KEY (id_usuario) REFERENCES USUARIO (id_usuario);
ALTER TABLE RESERVA ADD CONSTRAINT fk_reserva_equipo
    FOREIGN KEY (equipo_codigo) REFERENCES EQUIPO (codigo);

ALTER TABLE SESION_USO ADD CONSTRAINT fk_sesion_reserva
    FOREIGN KEY (id_reserva) REFERENCES RESERVA (id_reserva) ON DELETE CASCADE;
ALTER TABLE SESION_CONSUMIBLE ADD CONSTRAINT fk_consumible_sesion
    FOREIGN KEY (id_sesion) REFERENCES SESION_USO (id_sesion) ON DELETE CASCADE;
ALTER TABLE SESION_INCIDENTE ADD CONSTRAINT fk_incidente_sesion
    FOREIGN KEY (id_sesion) REFERENCES SESION_USO (id_sesion) ON DELETE CASCADE;

ALTER TABLE SOLICITUD_ESPERA ADD CONSTRAINT fk_solicitud_usuario
    FOREIGN KEY (id_usuario) REFERENCES USUARIO (id_usuario);
ALTER TABLE SOLICITUD_ESPERA ADD CONSTRAINT fk_solicitud_equipo
    FOREIGN KEY (equipo_codigo) REFERENCES EQUIPO (codigo);

ALTER TABLE TICKET_MANTENIMIENTO ADD CONSTRAINT fk_ticket_equipo
    FOREIGN KEY (equipo_codigo) REFERENCES EQUIPO (codigo);
-- El técnico se referencia en TECNICO, así solo se puede asignar a alguien que sea técnico.
ALTER TABLE TICKET_MANTENIMIENTO ADD CONSTRAINT fk_ticket_tecnico
    FOREIGN KEY (id_tecnico) REFERENCES TECNICO (id_usuario);
ALTER TABLE TICKET_CAMBIO ADD CONSTRAINT fk_cambio_ticket
    FOREIGN KEY (id_ticket) REFERENCES TICKET_MANTENIMIENTO (id_ticket) ON DELETE CASCADE;
