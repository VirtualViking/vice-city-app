-- Vice City App · Módulo de acceso y búsqueda
-- Datos para mostrar el artista y para el autocompletado con datos propios (sin gastar cupo de YouTube).

-- Artista deducido del canal o del título (YouTube no tiene un campo "artista").
-- Las columnas *_normalizado guardan el texto en minúsculas y sin tildes para comparar al autocompletar.
ALTER TABLE cancion ADD COLUMN artista              VARCHAR(200);
ALTER TABLE cancion ADD COLUMN titulo_normalizado   VARCHAR(200);
ALTER TABLE cancion ADD COLUMN artista_normalizado  VARCHAR(200);

-- Términos buscados en cada establecimiento: fuente principal de las sugerencias, ordenadas por frecuencia.
CREATE TABLE termino_buscado (
                                 establecimiento_id  UUID         NOT NULL REFERENCES establecimiento (id),
                                 termino             VARCHAR(100) NOT NULL,                  -- normalizado: minúsculas, sin tildes
                                 texto               VARCHAR(100) NOT NULL,                  -- como se escribió la última vez (para mostrarlo)
                                 veces               INT          NOT NULL DEFAULT 1 CHECK (veces > 0),
                                 ultima_busqueda     TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 PRIMARY KEY (establecimiento_id, termino)
);
-- Búsqueda por prefijo (LIKE 'texto%') con índice
CREATE INDEX ix_termino_buscado_prefijo ON termino_buscado (establecimiento_id, termino text_pattern_ops);

-- Conteo de búsquedas que consumieron cupo de YouTube (límites por dispositivo y diario)
CREATE INDEX ix_evento_busquedas_youtube ON evento (ocurrido_en)
    WHERE tipo = 'BUSQUEDA_REALIZADA' AND datos ->> 'fuente' = 'YOUTUBE';