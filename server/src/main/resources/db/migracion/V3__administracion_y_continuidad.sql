-- Vice City App · Módulo de administración y continuidad musical
-- Horarios, programación automática, mantenimiento, pausa de solicitudes, lista por defecto y bloqueos.

-- Operación del establecimiento
ALTER TABLE establecimiento ADD COLUMN programacion_automatica  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE establecimiento ADD COLUMN mantenimiento            BOOLEAN NOT NULL DEFAULT FALSE;
-- Posición de la lista por defecto que sonó por última vez: la rotación sigue de una noche a la siguiente
ALTER TABLE establecimiento ADD COLUMN posicion_lista_defecto   INT;

-- Sesión
ALTER TABLE sesion ADD COLUMN automatica            BOOLEAN NOT NULL DEFAULT FALSE;  -- la abrió la programación automática
ALTER TABLE sesion ADD COLUMN solicitudes_pausadas  BOOLEAN NOT NULL DEFAULT FALSE;  -- pausa manual de solicitudes y votos
-- Canción de la lista por defecto que suena y desde cuándo (las solicitudes guardan su propio inicio)
ALTER TABLE sesion ADD COLUMN video_actual          VARCHAR(11) REFERENCES cancion (video_id);
ALTER TABLE sesion ADD COLUMN inicio_actual         TIMESTAMPTZ;

-- Horario semanal en hora de Colombia: una franja por día.
-- Si cierre es anterior a apertura, la franja termina al día siguiente (por ejemplo, 20:00 a 03:00).
CREATE TABLE horario (
                         establecimiento_id  UUID     NOT NULL REFERENCES establecimiento (id),
                         dia                 SMALLINT NOT NULL CHECK (dia BETWEEN 1 AND 7),   -- 1 = lunes … 7 = domingo (ISO 8601)
                         apertura            TIME     NOT NULL,
                         cierre              TIME     NOT NULL CHECK (cierre <> apertura),
                         PRIMARY KEY (establecimiento_id, dia)
);

-- Canciones bloqueadas por contenido inapropiado: no se pueden pedir ni aparecen en las búsquedas hasta "hasta".
CREATE TABLE bloqueo_video (
                               establecimiento_id  UUID        NOT NULL REFERENCES establecimiento (id),
                               video_id            VARCHAR(11) NOT NULL REFERENCES cancion (video_id),
                               motivo              VARCHAR(15) NOT NULL CHECK (motivo IN ('NO_MUSICAL', 'INAPROPIADO', 'OTRO')),
                               personal_id         UUID        NOT NULL REFERENCES personal (id),
                               creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
                               hasta               TIMESTAMPTZ NOT NULL,
                               PRIMARY KEY (establecimiento_id, video_id)
);
CREATE INDEX ix_bloqueo_video_hasta ON bloqueo_video (establecimiento_id, hasta);