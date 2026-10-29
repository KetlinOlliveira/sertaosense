-- Regras de validação da cisterna e desativação (soft delete).
-- O CHECK no banco é a última linha de defesa: a API também vai validar, com mensagens amigáveis.

-- Capacidade é o TAMANHO da cisterna (não o nível de água): zero não faz sentido físico e causaria
-- divisão por zero no cálculo de porcentagem da análise. Teto de 100 mil litros: acima das cisternas
-- de 52 mil (calçadão/enxurrada/escolar), mas barra erros de digitação como 16.000.000.
ALTER TABLE cisterna
    ADD CONSTRAINT ck_cisterna_capacidade CHECK (capacidade_litros > 0 AND capacidade_litros <= 100000);

-- Limites geográficos globais (BETWEEN inclui as pontas).
ALTER TABLE cisterna
    ADD CONSTRAINT ck_cisterna_latitude CHECK (latitude BETWEEN -90 AND 90);
ALTER TABLE cisterna
    ADD CONSTRAINT ck_cisterna_longitude CHECK (longitude BETWEEN -180 AND 180);

-- O mesmo nome pode existir em comunidades diferentes, mas não duas vezes na mesma comunidade:
-- o alerta "Cisterna da Escola, Comunidade A" precisa identificar uma única cisterna.
ALTER TABLE cisterna
    ADD CONSTRAINT uk_cisterna_nome_comunidade UNIQUE (nome, comunidade);

-- Soft delete: cisternas não são apagadas, são desativadas. Os outros serviços guardam o UUID da
-- cisterna nas leituras (sem FK, bancos diferentes), então apagar deixaria histórico órfão.
ALTER TABLE cisterna
    ADD COLUMN ativa BOOLEAN NOT NULL DEFAULT TRUE;
