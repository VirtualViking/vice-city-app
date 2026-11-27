-- Videos que YouTube no deja reproducir dentro de la app (RF-25): el dueño de los derechos no permite verlos incrustados
-- (códigos 101 y 150 del reproductor) o el video ya no existe o es privado (código 100).
-- Se registran la primera vez que fallan en la vista pública. Desde entonces no salen en las búsquedas, no se pueden pedir
-- y la lista por defecto los salta. Vale para toda la app: el bloqueo depende del sitio donde se incrusta el video,
-- que es el mismo para todos los establecimientos.
CREATE TABLE video_no_reproducible (
                                       video_id      VARCHAR(11) PRIMARY KEY REFERENCES cancion (video_id),
                                       codigo_error  SMALLINT    NOT NULL,
                                       detectado_en  TIMESTAMPTZ NOT NULL
);