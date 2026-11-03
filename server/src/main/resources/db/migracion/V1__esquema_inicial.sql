-- Vice City App · Esquema inicial (modelo entidad-relación aprobado)
-- Todas las marcas de tiempo se guardan en UTC (TIMESTAMPTZ).

CREATE TABLE establecimiento (
                                 id                  UUID PRIMARY KEY,
                                 nombre              VARCHAR(120) NOT NULL,
                                 codigo_qr           VARCHAR(16)  NOT NULL UNIQUE,
                                 limite_solicitudes  INT          NOT NULL DEFAULT 3   CHECK (limite_solicitudes > 0),
                                 ventana_limite_min  INT          NOT NULL DEFAULT 15  CHECK (ventana_limite_min > 0),
                                 antirepeticion_min  INT          NOT NULL DEFAULT 60  CHECK (antirepeticion_min >= 0),
                                 duracion_max_seg    INT          NOT NULL DEFAULT 480 CHECK (duracion_max_seg > 0),
                                 creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE personal (
                          id                  UUID PRIMARY KEY,
                          establecimiento_id  UUID         NOT NULL REFERENCES establecimiento (id),
                          nombre              VARCHAR(120) NOT NULL,
                          email               VARCHAR(160) NOT NULL UNIQUE,
                          password_hash       VARCHAR(100) NOT NULL,
                          rol                 VARCHAR(10)  NOT NULL CHECK (rol IN ('ADMIN', 'DJ')),
                          activo              BOOLEAN      NOT NULL DEFAULT TRUE,
                          creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE sesion (
                        id                      UUID PRIMARY KEY,
                        establecimiento_id      UUID        NOT NULL REFERENCES establecimiento (id),
                        estado                  VARCHAR(10) NOT NULL CHECK (estado IN ('ABIERTA', 'CERRADA')),
                        modo_actual             VARCHAR(15) NOT NULL DEFAULT 'COLA' CHECK (modo_actual IN ('COLA', 'LISTA_DEFECTO')),
                        estado_reproductor      VARCHAR(15) NOT NULL DEFAULT 'DETENIDO' CHECK (estado_reproductor IN ('DETENIDO', 'REPRODUCIENDO', 'PAUSADO')),
                        posicion_lista_defecto  INT,
                        inicio                  TIMESTAMPTZ NOT NULL DEFAULT now(),
                        fin                     TIMESTAMPTZ,
                        CHECK ((estado = 'ABIERTA' AND fin IS NULL) OR (estado = 'CERRADA' AND fin IS NOT NULL))
);
-- Una sola sesión abierta por establecimiento
CREATE UNIQUE INDEX ux_sesion_abierta ON sesion (establecimiento_id) WHERE estado = 'ABIERTA';
CREATE INDEX ix_sesion_establecimiento_inicio ON sesion (establecimiento_id, inicio DESC);

CREATE TABLE dispositivo (
                             hash            CHAR(64)    PRIMARY KEY,  -- HMAC-SHA256 del identificador anónimo; el original no se guarda
                             primera_visita  TIMESTAMPTZ NOT NULL DEFAULT now(),
                             ultima_visita   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cancion (
                         video_id        VARCHAR(11)  PRIMARY KEY,
                         titulo          VARCHAR(200) NOT NULL,
                         canal           VARCHAR(200) NOT NULL,
                         duracion_seg    INT          NOT NULL CHECK (duracion_seg > 0),
                         miniatura_url   VARCHAR(300),
                         actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE cancion_categoria (
                                   video_id   VARCHAR(11) NOT NULL REFERENCES cancion (video_id),
                                   categoria  VARCHAR(60) NOT NULL,
                                   PRIMARY KEY (video_id, categoria)
);

CREATE TABLE solicitud (
                           id                   UUID PRIMARY KEY,
                           sesion_id            UUID        NOT NULL REFERENCES sesion (id),
                           video_id             VARCHAR(11) NOT NULL REFERENCES cancion (video_id),
                           dispositivo_hash     CHAR(64)    NOT NULL REFERENCES dispositivo (hash),
                           estado               VARCHAR(15) NOT NULL CHECK (estado IN ('EN_COLA', 'REPRODUCIENDO', 'REPRODUCIDA', 'ELIMINADA', 'FALLIDA', 'NO_REPRODUCIDA')),
                           solicitada_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
                           inicio_reproduccion  TIMESTAMPTZ,
                           fin_reproduccion     TIMESTAMPTZ
);
-- Una canción no puede estar dos veces en la cola de la misma sesión
CREATE UNIQUE INDEX ux_solicitud_activa ON solicitud (sesion_id, video_id) WHERE estado IN ('EN_COLA', 'REPRODUCIENDO');
CREATE INDEX ix_solicitud_sesion_estado ON solicitud (sesion_id, estado);
CREATE INDEX ix_solicitud_dispositivo ON solicitud (dispositivo_hash, solicitada_en);

CREATE TABLE voto (
                      id                UUID PRIMARY KEY,
                      solicitud_id      UUID        NOT NULL REFERENCES solicitud (id),
                      dispositivo_hash  CHAR(64)    NOT NULL REFERENCES dispositivo (hash),
                      creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
                      UNIQUE (solicitud_id, dispositivo_hash)   -- un voto por dispositivo por canción (RF-06)
);

CREATE TABLE lista_defecto_item (
                                    establecimiento_id  UUID        NOT NULL REFERENCES establecimiento (id),
                                    posicion            INT         NOT NULL CHECK (posicion > 0),
                                    video_id            VARCHAR(11) NOT NULL REFERENCES cancion (video_id),
                                    PRIMARY KEY (establecimiento_id, posicion)
);

-- Registro inmutable de eventos para la capa analítica (RNF-15).
-- solicitud_id, video_id y dispositivo_hash no tienen FK a propósito: el histórico no depende de esas filas.
CREATE TABLE evento (
                        id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        id_idempotencia     UUID        NOT NULL UNIQUE,
                        tipo                VARCHAR(40) NOT NULL,
                        establecimiento_id  UUID        NOT NULL,
                        sesion_id           UUID        REFERENCES sesion (id),
                        solicitud_id        UUID,
                        video_id            VARCHAR(11),
                        dispositivo_hash    CHAR(64),
                        ocurrido_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
                        datos               JSONB       NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_evento_sesion ON evento (sesion_id);
CREATE INDEX ix_evento_tipo_fecha ON evento (tipo, ocurrido_en);
CREATE INDEX ix_evento_establecimiento_fecha ON evento (establecimiento_id, ocurrido_en);

-- Solo inserción: impide modificar o borrar eventos desde la aplicación
CREATE FUNCTION evento_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La tabla evento es de solo inserción (RNF-15)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_evento_inmutable
    BEFORE UPDATE OR DELETE ON evento
    FOR EACH ROW EXECUTE FUNCTION evento_inmutable();

-- Caché de búsquedas de YouTube (RNF-14). Tabla técnica, fuera del modelo conceptual.
CREATE TABLE cache_busqueda (
                                termino     VARCHAR(100) PRIMARY KEY,
                                resultados  JSONB        NOT NULL,
                                expira_en   TIMESTAMPTZ  NOT NULL
);