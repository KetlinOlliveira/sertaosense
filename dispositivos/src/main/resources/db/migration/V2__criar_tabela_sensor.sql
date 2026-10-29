-- Sensores instalados nas cisternas (uma cisterna pode ter vários sensores).
CREATE TABLE sensor (
    id          UUID         PRIMARY KEY,
    -- código físico/lógico do sensor; é por ele que a ingestão vai identificar quem enviou a leitura
    codigo      VARCHAR(50)  NOT NULL,
    -- guarda o nome do enum TipoSensor (Java), não a posição; o CHECK impede valores fora da lista
    tipo        VARCHAR(20)  NOT NULL,
    cisterna_id UUID         NOT NULL,

    CONSTRAINT uk_sensor_codigo UNIQUE (codigo),
    CONSTRAINT ck_sensor_tipo CHECK (tipo IN ('BOIA', 'ULTRASSONICO', 'PRESSAO', 'CAPACITIVO')),
    -- Sem ON DELETE: o banco BLOQUEIA excluir cisterna com sensores. Cisternas não são apagadas,
    -- são desativadas (ver V3), para preservar o histórico.
    CONSTRAINT fk_sensor_cisterna FOREIGN KEY (cisterna_id) REFERENCES cisterna (id)
);

-- O PostgreSQL NÃO cria índice automático para FK. Sem ele, "sensores da cisterna X"
-- vira varredura da tabela inteira.
CREATE INDEX idx_sensor_cisterna_id ON sensor (cisterna_id);
