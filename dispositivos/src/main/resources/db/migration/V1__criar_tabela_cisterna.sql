-- Cisternas monitoradas pelo SertãoSense.
-- O id (UUID) é gerado pela aplicação (Hibernate), por isso não há DEFAULT aqui.
CREATE TABLE cisterna (
    id                UUID          PRIMARY KEY,
    nome              VARCHAR(120)  NOT NULL,
    comunidade        VARCHAR(120)  NOT NULL,
    capacidade_litros INTEGER       NOT NULL,
    -- NUMERIC(9,6): até 3 dígitos antes da vírgula e 6 depois (~10 cm de precisão)
    latitude          NUMERIC(9, 6) NOT NULL,
    longitude         NUMERIC(9, 6) NOT NULL
);
-- As regras de validação (CHECK/UNIQUE) são adicionadas na V3.
