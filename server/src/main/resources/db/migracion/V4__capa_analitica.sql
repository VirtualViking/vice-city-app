-- Capa analítica (Entregable 6): datos limpios y anónimos que el pipeline construye a partir de la tabla evento.
-- Va en un esquema aparte para separar los datos operativos (public) de los analíticos (analitica).
-- Ninguna tabla de este esquema guarda el hash del dispositivo ni el id de la solicitud:
-- solo códigos que cambian en cada sesión y que dejan de poder relacionarse cuando se borra la clave de la sesión.

CREATE SCHEMA IF NOT EXISTS analitica;

-- Dimensión canción, limpia: artista sin sufijos de canal y géneros en español
CREATE TABLE analitica.cancion (
                                   video_id      VARCHAR(11)  PRIMARY KEY,
                                   titulo        VARCHAR(200) NOT NULL,
                                   artista       VARCHAR(200) NOT NULL,
                                   duracion_seg  INT          NOT NULL,
                                   actualizada_en TIMESTAMPTZ NOT NULL
);

CREATE TABLE analitica.cancion_genero (
                                          video_id  VARCHAR(11) NOT NULL REFERENCES analitica.cancion (video_id) ON DELETE CASCADE,
                                          genero    VARCHAR(60) NOT NULL,
                                          PRIMARY KEY (video_id, genero)
);

-- Una fila por sesión procesada. La sesión no es un dato personal.
CREATE TABLE analitica.sesion (
                                  sesion_id           UUID        PRIMARY KEY,
                                  establecimiento_id  UUID        NOT NULL,
                                  inicio              TIMESTAMPTZ NOT NULL,
                                  fin                 TIMESTAMPTZ,
                                  fecha_local         DATE        NOT NULL,          -- fecha de inicio en hora de Colombia
                                  automatica          BOOLEAN     NOT NULL,
                                  duracion_seg        INT,
                                  transiciones        INT         NOT NULL DEFAULT 0, -- cambios entre la cola y la lista por defecto
                                  consolidada         BOOLEAN     NOT NULL DEFAULT FALSE,
                                  procesada_en        TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_an_sesion_establecimiento_fecha ON analitica.sesion (establecimiento_id, fecha_local);

-- Participante: código anónimo válido solo dentro de su sesión
CREATE TABLE analitica.participacion (
                                         sesion_id     UUID     NOT NULL REFERENCES analitica.sesion (sesion_id) ON DELETE CASCADE,
                                         participante  CHAR(16) NOT NULL,
                                         accedio       BOOLEAN  NOT NULL,   -- abrió la app con la sesión abierta
                                         nuevo         BOOLEAN  NOT NULL,   -- era su primera visita al establecimiento
                                         solicitudes   INT      NOT NULL,
                                         votos         INT      NOT NULL,   -- incluye el voto inicial de sus solicitudes
                                         PRIMARY KEY (sesion_id, participante)
);

CREATE TABLE analitica.solicitud (
                                     sesion_id      UUID        NOT NULL REFERENCES analitica.sesion (sesion_id) ON DELETE CASCADE,
                                     solicitud_ref  CHAR(16)    NOT NULL,
                                     participante   CHAR(16)    NOT NULL,
                                     video_id       VARCHAR(11) NOT NULL,
                                     solicitada_en  TIMESTAMPTZ NOT NULL,   -- redondeada al minuto
                                     hora_local     SMALLINT    NOT NULL CHECK (hora_local BETWEEN 0 AND 23),
                                     estado_final   VARCHAR(15) NOT NULL,   -- EN_COLA y REPRODUCIENDO solo mientras la sesión sigue abierta
                                     votos          INT         NOT NULL,
                                     espera_seg     INT,                    -- desde la solicitud hasta que empezó a sonar
                                     en_cola_seg    INT,                    -- si no sonó: tiempo en cola hasta el cierre
                                     PRIMARY KEY (sesion_id, solicitud_ref)
);

CREATE TABLE analitica.voto (
                                sesion_id      UUID        NOT NULL REFERENCES analitica.sesion (sesion_id) ON DELETE CASCADE,
                                solicitud_ref  CHAR(16)    NOT NULL,
                                participante   CHAR(16)    NOT NULL,
                                origen         VARCHAR(20) NOT NULL,   -- INICIAL, DIRECTO o SOLICITUD_REPETIDA
                                ocurrido_en    TIMESTAMPTZ NOT NULL,   -- redondeado al minuto
                                hora_local     SMALLINT    NOT NULL CHECK (hora_local BETWEEN 0 AND 23),
                                PRIMARY KEY (sesion_id, solicitud_ref, participante)
);

-- Historial de lo que sonó
CREATE TABLE analitica.reproduccion (
                                        sesion_id      UUID        NOT NULL REFERENCES analitica.sesion (sesion_id) ON DELETE CASCADE,
                                        orden          INT         NOT NULL,
                                        video_id       VARCHAR(11) NOT NULL,
                                        origen         VARCHAR(15) NOT NULL,   -- COLA o LISTA_DEFECTO
                                        solicitud_ref  CHAR(16),
                                        inicio         TIMESTAMPTZ NOT NULL,
                                        fin            TIMESTAMPTZ,
                                        segundos       INT         NOT NULL,
                                        resultado      VARCHAR(15) NOT NULL,   -- TERMINADA, SALTADA, FALLIDA, ELIMINADA, CIERRE o SONANDO
                                        fin_inferido   BOOLEAN     NOT NULL,
                                        votos          INT,
                                        espera_seg     INT,
                                        PRIMARY KEY (sesion_id, orden)
);

-- Registro de cada ejecución del pipeline (evidencia de la ingesta y la limpieza)
CREATE TABLE analitica.ejecucion (
                                     id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                     origen                 VARCHAR(15) NOT NULL,
                                     inicio                 TIMESTAMPTZ NOT NULL,
                                     fin                    TIMESTAMPTZ,
                                     eventos_leidos         INT         NOT NULL DEFAULT 0,
                                     eventos_cargados       INT         NOT NULL DEFAULT 0,
                                     eventos_rechazados     INT         NOT NULL DEFAULT 0,
                                     sesiones_procesadas    INT         NOT NULL DEFAULT 0,
                                     sesiones_consolidadas  INT         NOT NULL DEFAULT 0,
                                     detalle                JSONB       NOT NULL DEFAULT '{}'::jsonb   -- rechazos por motivo, normalizaciones, retención
);
CREATE INDEX ix_an_ejecucion_inicio ON analitica.ejecucion (inicio DESC);

-- Eventos que no pasaron la limpieza: se apartan con su motivo y no llegan a los indicadores
CREATE TABLE analitica.evento_rechazado (
                                            evento_id     BIGINT      PRIMARY KEY,
                                            ejecucion_id  BIGINT      NOT NULL REFERENCES analitica.ejecucion (id),
                                            tipo          VARCHAR(40) NOT NULL,
                                            motivo        VARCHAR(40) NOT NULL,
                                            detectado_en  TIMESTAMPTZ NOT NULL
);

-- Clave aleatoria de cada sesión aún no consolidada. Se borra al consolidar: desde ahí los códigos no se pueden revertir.
CREATE TABLE analitica.sal_sesion (
                                      sesion_id  UUID        PRIMARY KEY,
                                      sal        BYTEA       NOT NULL,
                                      creada_en  TIMESTAMPTZ NOT NULL
);

-- Retención (sección 6 del diseño): un evento no se modifica ni se borra dentro de su periodo de retención (24 meses).
-- Pasado ese plazo, solo el proceso de retención puede borrarlo, activando la variable vicecity.retencion en su transacción.
CREATE OR REPLACE FUNCTION evento_inmutable() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE'
        AND current_setting('vicecity.retencion', true) = 'activa'
        AND OLD.ocurrido_en < now() - INTERVAL '24 months' THEN
        RETURN OLD;
END IF;
    RAISE EXCEPTION 'La tabla evento es de solo inserción (RNF-15)';
END;
$$ LANGUAGE plpgsql;