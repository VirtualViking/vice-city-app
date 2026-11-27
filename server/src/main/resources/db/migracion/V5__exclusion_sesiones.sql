-- Sesiones que el administrador excluye del tablero analítico, por ejemplo las de prueba.
-- No se borra nada: los eventos siguen siendo inmutables (RNF-15) y la capa analítica conserva la sesión.
-- Quitar la fila vuelve a incluir la sesión. Si la sesión analítica se borra, la exclusión se borra con ella.
CREATE TABLE analitica.sesion_excluida (
                                           sesion_id     UUID        PRIMARY KEY REFERENCES analitica.sesion (sesion_id) ON DELETE CASCADE,
                                           excluida_en   TIMESTAMPTZ NOT NULL,
                                           excluida_por  UUID        NOT NULL REFERENCES personal (id)
);