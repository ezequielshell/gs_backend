-- =====================================================================
-- Solo Vivo - Irrigação inteligente e monitoramento ambiental
-- Dump de estrutura + dados iniciais (PostgreSQL 14+)
-- Uso: createdb solo_vivo && psql -d solo_vivo -f dump.sql
-- ATENÇÃO: o script recria o schema public (apaga tudo que existir nele).
-- =====================================================================

BEGIN;

DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

-- ---------------------------------------------------------------------
-- TIPOS (ENUMs)
-- ---------------------------------------------------------------------
CREATE TYPE tipo_sensor AS ENUM (
    'umidade_solo', 'temperatura_ar', 'umidade_ar',
    'luminosidade', 'chuva', 'vento'
);

CREATE TYPE status_dispositivo AS ENUM ('online', 'offline', 'manutencao');

CREATE TYPE modo_irrigacao AS ENUM ('manual', 'automatico');          -- Tela 5: Manual | Automático

CREATE TYPE origem_irrigacao AS ENUM ('automatica', 'manual');        -- Tela 7: badges Automático | Manual

CREATE TYPE tipo_alerta AS ENUM (
    'solo_abaixo_limite',     -- "Solo abaixo do limite"
    'solo_acima_limite',
    'sem_leitura_sensor',     -- "Sem leitura do sensor"
    'irrigacao_pausada',      -- "Irrigação automática pausada"
    'temperatura_alta'
);

CREATE TYPE severidade_alerta AS ENUM ('info', 'atencao', 'critico'); -- Tela/Modal 2

CREATE TYPE plataforma_push AS ENUM ('android', 'ios', 'web');

CREATE TYPE tema_app AS ENUM ('claro', 'escuro', 'sistema');           -- Tela 8: Aparência

-- ---------------------------------------------------------------------
-- FUNÇÃO AUXILIAR
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_atualizado_em()
RETURNS TRIGGER AS $$
BEGIN
    NEW.atualizado_em = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- USUÁRIOS (Tela 1 - Login / Tela 8 - Perfil)
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
    id             BIGSERIAL PRIMARY KEY,
    nome           VARCHAR(120) NOT NULL,
    email          VARCHAR(160) NOT NULL UNIQUE,
    senha_hash     VARCHAR(255) NOT NULL,
    tema           tema_app NOT NULL DEFAULT 'claro',
    ativo          BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_usuarios_atualizado
    BEFORE UPDATE ON usuarios
    FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

-- "Esqueci minha senha"
CREATE TABLE recuperacoes_senha (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    expira_em   TIMESTAMPTZ NOT NULL,
    usado_em    TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recuperacoes_usuario ON recuperacoes_senha(usuario_id);

-- Tela 8: campo "Propriedade" (ex.: Sítio Boa Esperança)
CREATE TABLE propriedades (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    nome           VARCHAR(120) NOT NULL,
    cidade         VARCHAR(100),
    estado         CHAR(2),
    latitude       NUMERIC(9,6),
    longitude      NUMERIC(9,6),
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_propriedades_usuario ON propriedades(usuario_id);

CREATE TRIGGER trg_propriedades_atualizado
    BEFORE UPDATE ON propriedades
    FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

-- Tokens para alertas no celular
CREATE TABLE dispositivos_push (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token       VARCHAR(512) NOT NULL UNIQUE,
    plataforma  plataforma_push NOT NULL,
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_usuario ON dispositivos_push(usuario_id);

-- Tela 8: Notificações (switches "Solo abaixo do limite", "Sem leitura do sensor")
CREATE TABLE preferencias_notificacao (
    usuario_id   BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo_alerta  tipo_alerta NOT NULL,
    ativo        BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (usuario_id, tipo_alerta)
);

-- Ao criar um usuário, todas as notificações nascem ativadas
CREATE OR REPLACE FUNCTION criar_preferencias_padrao()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO preferencias_notificacao (usuario_id, tipo_alerta)
    SELECT NEW.id, t FROM unnest(enum_range(NULL::tipo_alerta)) AS t;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_usuarios_preferencias
    AFTER INSERT ON usuarios
    FOR EACH ROW EXECUTE FUNCTION criar_preferencias_padrao();

-- ---------------------------------------------------------------------
-- CULTURAS (campo "Cultura" da Tela 4) - catálogo com limites sugeridos
-- usuario_id NULL = cultura padrão do sistema
-- ---------------------------------------------------------------------
CREATE TABLE culturas (
    id                   BIGSERIAL PRIMARY KEY,
    usuario_id           BIGINT REFERENCES usuarios(id) ON DELETE CASCADE,
    nome                 VARCHAR(100) NOT NULL,
    umidade_min_padrao   NUMERIC(5,2) NOT NULL DEFAULT 30,
    umidade_max_padrao   NUMERIC(5,2) NOT NULL DEFAULT 60,
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cultura_faixa CHECK (
        umidade_min_padrao >= 0 AND umidade_max_padrao <= 100
        AND umidade_min_padrao < umidade_max_padrao
    )
);

CREATE UNIQUE INDEX uq_culturas_sistema ON culturas(LOWER(nome)) WHERE usuario_id IS NULL;
CREATE UNIQUE INDEX uq_culturas_usuario ON culturas(usuario_id, LOWER(nome)) WHERE usuario_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- HARDWARE: ESP32, sensores
-- ---------------------------------------------------------------------
CREATE TABLE dispositivos (
    id              BIGSERIAL PRIMARY KEY,
    propriedade_id  BIGINT NOT NULL REFERENCES propriedades(id) ON DELETE CASCADE,
    nome            VARCHAR(100) NOT NULL,
    identificador   VARCHAR(64) NOT NULL UNIQUE,    -- MAC / chip id do ESP32
    token_api       VARCHAR(128) NOT NULL UNIQUE,   -- autenticação do ESP32 na API
    firmware        VARCHAR(30),
    status          status_dispositivo NOT NULL DEFAULT 'offline',
    ultimo_contato  TIMESTAMPTZ,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dispositivos_propriedade ON dispositivos(propriedade_id);

CREATE TRIGGER trg_dispositivos_atualizado
    BEFORE UPDATE ON dispositivos
    FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

-- Tela 4: "Sensor vinculado" (ex.: Sensor de solo 01). Também guarda DHT22, LDR, chuva, vento.
CREATE TABLE sensores (
    id              BIGSERIAL PRIMARY KEY,
    dispositivo_id  BIGINT NOT NULL REFERENCES dispositivos(id) ON DELETE CASCADE,
    tipo            tipo_sensor NOT NULL,
    modelo          VARCHAR(50),
    nome            VARCHAR(100) NOT NULL,
    unidade         VARCHAR(15) NOT NULL,           -- %, °C, lux, mm, km/h
    calibracao_min  NUMERIC(10,2),                  -- leitura bruta com solo seco
    calibracao_max  NUMERIC(10,2),                  -- leitura bruta com solo molhado
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (dispositivo_id, nome)
);

CREATE INDEX idx_sensores_dispositivo ON sensores(dispositivo_id);

-- Série temporal de leituras
CREATE TABLE leituras (
    id         BIGSERIAL PRIMARY KEY,
    sensor_id  BIGINT NOT NULL REFERENCES sensores(id) ON DELETE CASCADE,
    valor      NUMERIC(10,2) NOT NULL,
    lida_em    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_leituras_sensor_data ON leituras(sensor_id, lida_em DESC);

-- ---------------------------------------------------------------------
-- CULTIVOS / CANTEIROS (Telas 2, 3, 4, 5, 6)
-- Concentra a configuração de limites e da irrigação de cada canteiro.
-- ---------------------------------------------------------------------
CREATE TABLE cultivos (
    id                          BIGSERIAL PRIMARY KEY,
    propriedade_id              BIGINT NOT NULL REFERENCES propriedades(id) ON DELETE CASCADE,
    cultura_id                  BIGINT REFERENCES culturas(id) ON DELETE SET NULL,
    nome                        VARCHAR(100) NOT NULL,            -- "Canteiro 2"
    sensor_umidade_id           BIGINT REFERENCES sensores(id) ON DELETE SET NULL, -- sensor vinculado
    dispositivo_bomba_id        BIGINT REFERENCES dispositivos(id) ON DELETE SET NULL, -- ESP32 com o relé
    -- Tela 6 - Limites
    umidade_min                 NUMERIC(5,2) NOT NULL DEFAULT 30,
    umidade_max                 NUMERIC(5,2) NOT NULL DEFAULT 60,
    tempo_max_acionamento_min   INTEGER NOT NULL DEFAULT 15,
    vazao_bomba_l_min           NUMERIC(8,2) NOT NULL DEFAULT 1.2,
    irrigacao_automatica_ativa  BOOLEAN NOT NULL DEFAULT TRUE,
    -- Tela 5 - Modo e estado
    modo_irrigacao              modo_irrigacao NOT NULL DEFAULT 'automatico',
    bomba_ligada                BOOLEAN NOT NULL DEFAULT FALSE,   -- mantido por trigger em irrigacoes
    irrigacao_pausada_motivo    VARCHAR(255),                     -- "Irrigação automática pausada. Veja o motivo."
    -- Considera "Sem leitura" se o sensor ficar este tempo sem enviar dados
    limite_sem_leitura_min      INTEGER NOT NULL DEFAULT 60,
    ativo                       BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cultivo_umidade CHECK (
        umidade_min >= 0 AND umidade_max <= 100 AND umidade_min < umidade_max
    ),
    CONSTRAINT ck_cultivo_tempo CHECK (tempo_max_acionamento_min > 0),
    CONSTRAINT ck_cultivo_vazao CHECK (vazao_bomba_l_min > 0)
);

CREATE INDEX idx_cultivos_propriedade ON cultivos(propriedade_id);
CREATE INDEX idx_cultivos_sensor ON cultivos(sensor_umidade_id);
CREATE UNIQUE INDEX uq_cultivos_nome ON cultivos(propriedade_id, LOWER(nome)) WHERE ativo;

CREATE TRIGGER trg_cultivos_atualizado
    BEFORE UPDATE ON cultivos
    FOR EACH ROW EXECUTE FUNCTION set_atualizado_em();

-- O sensor vinculado precisa ser de umidade do solo e da mesma propriedade
CREATE OR REPLACE FUNCTION validar_sensor_cultivo()
RETURNS TRIGGER AS $$
DECLARE
    v_tipo tipo_sensor;
    v_prop BIGINT;
BEGIN
    IF NEW.sensor_umidade_id IS NOT NULL THEN
        SELECT s.tipo, d.propriedade_id INTO v_tipo, v_prop
        FROM sensores s JOIN dispositivos d ON d.id = s.dispositivo_id
        WHERE s.id = NEW.sensor_umidade_id;

        IF v_tipo IS DISTINCT FROM 'umidade_solo' THEN
            RAISE EXCEPTION 'O sensor vinculado deve ser do tipo umidade_solo';
        END IF;
        IF v_prop IS DISTINCT FROM NEW.propriedade_id THEN
            RAISE EXCEPTION 'O sensor vinculado pertence a outra propriedade';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cultivos_validar_sensor
    BEFORE INSERT OR UPDATE OF sensor_umidade_id, propriedade_id ON cultivos
    FOR EACH ROW EXECUTE FUNCTION validar_sensor_cultivo();

-- ---------------------------------------------------------------------
-- IRRIGAÇÕES (Modal 1 - Ligar irrigação / Tela 7 - Histórico)
-- ---------------------------------------------------------------------
CREATE TABLE irrigacoes (
    id                   BIGSERIAL PRIMARY KEY,
    cultivo_id           BIGINT NOT NULL REFERENCES cultivos(id) ON DELETE CASCADE,
    usuario_id           BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,  -- quem confirmou (manual)
    origem               origem_irrigacao NOT NULL,
    motivo               VARCHAR(160),               -- "Regra: solo abaixo de 30%" / "Ação confirmada pelo usuário"
    iniciado_em          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalizado_em        TIMESTAMPTZ,                -- NULL = bomba ligada
    duracao_prevista_min INTEGER,                    -- tempo máximo no momento do acionamento
    duracao_segundos     INTEGER,
    vazao_litros_min     NUMERIC(8,2),               -- foto da vazão no momento (histórico fiel)
    volume_litros        NUMERIC(12,2),              -- consumo estimado
    umidade_inicial      NUMERIC(5,2),
    umidade_final        NUMERIC(5,2),
    CONSTRAINT ck_irrigacao_fim CHECK (finalizado_em IS NULL OR finalizado_em >= iniciado_em),
    CONSTRAINT ck_irrigacao_usuario CHECK (origem <> 'manual' OR usuario_id IS NOT NULL)
);

CREATE INDEX idx_irrigacoes_cultivo_data ON irrigacoes(cultivo_id, iniciado_em DESC);
-- No máximo uma irrigação em andamento por cultivo
CREATE UNIQUE INDEX uq_irrigacao_em_andamento ON irrigacoes(cultivo_id) WHERE finalizado_em IS NULL;

CREATE OR REPLACE FUNCTION preparar_irrigacao()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.vazao_litros_min IS NULL THEN
        SELECT vazao_bomba_l_min INTO NEW.vazao_litros_min FROM cultivos WHERE id = NEW.cultivo_id;
    END IF;
    IF NEW.finalizado_em IS NOT NULL THEN
        NEW.duracao_segundos := EXTRACT(EPOCH FROM (NEW.finalizado_em - NEW.iniciado_em))::INTEGER;
        IF NEW.volume_litros IS NULL THEN
            NEW.volume_litros := ROUND((NEW.duracao_segundos / 60.0) * COALESCE(NEW.vazao_litros_min, 0), 2);
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_irrigacoes_preparar
    BEFORE INSERT OR UPDATE ON irrigacoes
    FOR EACH ROW EXECUTE FUNCTION preparar_irrigacao();

-- Mantém cultivos.bomba_ligada ("Bomba: Desligada • automático") sincronizado
CREATE OR REPLACE FUNCTION sincronizar_bomba()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE cultivos
       SET bomba_ligada = EXISTS (
            SELECT 1 FROM irrigacoes
             WHERE cultivo_id = NEW.cultivo_id AND finalizado_em IS NULL)
     WHERE id = NEW.cultivo_id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_irrigacoes_bomba
    AFTER INSERT OR UPDATE ON irrigacoes
    FOR EACH ROW EXECUTE FUNCTION sincronizar_bomba();

-- ---------------------------------------------------------------------
-- ALERTAS (Modal 2 - abas Ativos | Resolvidos; sino na Home/Cultivos)
-- ---------------------------------------------------------------------
CREATE TABLE alertas (
    id              BIGSERIAL PRIMARY KEY,
    propriedade_id  BIGINT NOT NULL REFERENCES propriedades(id) ON DELETE CASCADE,
    cultivo_id      BIGINT REFERENCES cultivos(id) ON DELETE CASCADE,
    sensor_id       BIGINT REFERENCES sensores(id) ON DELETE SET NULL,
    tipo            tipo_alerta NOT NULL,
    severidade      severidade_alerta NOT NULL DEFAULT 'atencao',
    mensagem        TEXT NOT NULL,                  -- "Solo abaixo do limite configurado. Confira o cultivo."
    acao_sugerida   TEXT,                           -- "Ação: conferir o cultivo e a leitura do sensor."
    valor           NUMERIC(10,2),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lido_em         TIMESTAMPTZ,
    resolvido_em    TIMESTAMPTZ                     -- NULL = ativo
);

CREATE INDEX idx_alertas_propriedade_data ON alertas(propriedade_id, criado_em DESC);
CREATE INDEX idx_alertas_ativos ON alertas(propriedade_id) WHERE resolvido_em IS NULL;
-- Evita alerta ativo duplicado do mesmo tipo para o mesmo cultivo
CREATE UNIQUE INDEX uq_alerta_ativo_cultivo ON alertas(cultivo_id, tipo)
    WHERE resolvido_em IS NULL AND cultivo_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- VIEWS
-- ---------------------------------------------------------------------

-- Telas 2, 3 e 5: situação atual de cada cultivo
-- status: adequado | atencao | sem_leitura
CREATE VIEW v_cultivos_status AS
SELECT
    x.*,
    CASE x.status
        WHEN 'sem_leitura' THEN 'Sem leitura'
        WHEN 'atencao' THEN
            CASE WHEN x.umidade_solo < x.umidade_min THEN 'Solo abaixo do limite'
                 ELSE 'Solo acima do limite' END
        ELSE 'Dentro da faixa'
    END AS mensagem_status
FROM (
    SELECT
        c.id                        AS cultivo_id,
        c.propriedade_id,
        c.nome                      AS canteiro,
        cu.nome                     AS cultura,
        c.umidade_min,
        c.umidade_max,
        c.tempo_max_acionamento_min,
        c.vazao_bomba_l_min,
        c.modo_irrigacao,
        c.irrigacao_automatica_ativa,
        c.bomba_ligada,
        c.irrigacao_pausada_motivo,
        ul.valor                    AS umidade_solo,
        ul.lida_em                  AS ultima_leitura_em,
        t.valor                     AS temperatura_ar,
        h.valor                     AS umidade_ar,
        CASE
            WHEN ul.lida_em IS NULL
              OR ul.lida_em < NOW() - make_interval(mins => c.limite_sem_leitura_min) THEN 'sem_leitura'
            WHEN ul.valor < c.umidade_min OR ul.valor > c.umidade_max THEN 'atencao'
            ELSE 'adequado'
        END                         AS status
    FROM cultivos c
    LEFT JOIN culturas cu ON cu.id = c.cultura_id
    LEFT JOIN sensores s  ON s.id = c.sensor_umidade_id
    LEFT JOIN LATERAL (
        SELECT l.valor, l.lida_em FROM leituras l
        WHERE l.sensor_id = c.sensor_umidade_id
        ORDER BY l.lida_em DESC LIMIT 1
    ) ul ON TRUE
    LEFT JOIN LATERAL (
        SELECT l.valor FROM leituras l JOIN sensores s2 ON s2.id = l.sensor_id
        WHERE s2.dispositivo_id = s.dispositivo_id AND s2.tipo = 'temperatura_ar'
        ORDER BY l.lida_em DESC LIMIT 1
    ) t ON TRUE
    LEFT JOIN LATERAL (
        SELECT l.valor FROM leituras l JOIN sensores s3 ON s3.id = l.sensor_id
        WHERE s3.dispositivo_id = s.dispositivo_id AND s3.tipo = 'umidade_ar'
        ORDER BY l.lida_em DESC LIMIT 1
    ) h ON TRUE
    WHERE c.ativo
) x;

-- Tela 2 (banner): "3 cultivos: 2 adequados, 1 em atenção" + "Atualizado às 14h32"
CREATE VIEW v_resumo_home AS
SELECT
    propriedade_id,
    COUNT(*)                                        AS total_cultivos,
    COUNT(*) FILTER (WHERE status = 'adequado')     AS adequados,
    COUNT(*) FILTER (WHERE status = 'atencao')      AS em_atencao,
    COUNT(*) FILTER (WHERE status = 'sem_leitura')  AS sem_leitura,
    MAX(ultima_leitura_em)                          AS atualizado_em
FROM v_cultivos_status
GROUP BY propriedade_id;

-- Consumo de água por cultivo e dia (histórico de consumo)
CREATE VIEW v_consumo_agua_diario AS
SELECT
    cultivo_id,
    DATE(iniciado_em)                    AS dia,
    COUNT(*)                             AS total_irrigacoes,
    COALESCE(SUM(duracao_segundos), 0)   AS duracao_total_segundos,
    COALESCE(SUM(volume_litros), 0)      AS volume_total_litros
FROM irrigacoes
WHERE finalizado_em IS NOT NULL
GROUP BY cultivo_id, DATE(iniciado_em);

-- Relatórios ambientais: mín/média/máx diários por sensor
CREATE VIEW v_resumo_ambiental_diario AS
SELECT
    s.id                   AS sensor_id,
    s.dispositivo_id,
    s.tipo,
    DATE(l.lida_em)        AS dia,
    ROUND(MIN(l.valor), 2) AS minimo,
    ROUND(AVG(l.valor), 2) AS media,
    ROUND(MAX(l.valor), 2) AS maximo,
    COUNT(*)               AS total_leituras
FROM leituras l
JOIN sensores s ON s.id = l.sensor_id
GROUP BY s.id, s.dispositivo_id, s.tipo, DATE(l.lida_em);

-- Tela 7: cards "Irrigações", "Consumo estimado" e "Tempo fora da faixa"
-- Ex.: SELECT * FROM resumo_historico(2, NOW() - INTERVAL '7 days');
CREATE OR REPLACE FUNCTION resumo_historico(p_cultivo_id BIGINT, p_desde TIMESTAMPTZ)
RETURNS TABLE (
    total_irrigacoes          BIGINT,
    consumo_estimado_litros   NUMERIC,
    tempo_fora_faixa_segundos BIGINT
)
LANGUAGE sql STABLE AS $$
    WITH c AS (
        SELECT sensor_umidade_id, umidade_min, umidade_max, limite_sem_leitura_min
        FROM cultivos WHERE id = p_cultivo_id
    ),
    l AS (
        SELECT le.valor, le.lida_em,
               LEAD(le.lida_em) OVER (ORDER BY le.lida_em) AS proxima
        FROM leituras le
        JOIN c ON le.sensor_id = c.sensor_umidade_id
        WHERE le.lida_em >= p_desde
    ),
    fora AS (
        SELECT COALESCE(SUM(EXTRACT(EPOCH FROM LEAST(
                   COALESCE(l.proxima, NOW()) - l.lida_em,
                   make_interval(mins => c.limite_sem_leitura_min)))), 0)::BIGINT AS seg
        FROM l CROSS JOIN c
        WHERE l.valor < c.umidade_min OR l.valor > c.umidade_max
    ),
    irr AS (
        SELECT COUNT(*) AS n, COALESCE(SUM(volume_litros), 0) AS litros
        FROM irrigacoes
        WHERE cultivo_id = p_cultivo_id AND iniciado_em >= p_desde
    )
    SELECT irr.n, irr.litros, fora.seg FROM irr, fora;
$$;

-- ---------------------------------------------------------------------
-- DADOS INICIAIS: culturas padrão (limites sugeridos, editáveis no app)
-- ---------------------------------------------------------------------
INSERT INTO culturas (nome, umidade_min_padrao, umidade_max_padrao) VALUES
    ('Alface',  30, 60),
    ('Tomate',  30, 60),
    ('Cenoura', 35, 65),
    ('Milho',   25, 55),
    ('Feijão',  25, 55),
    ('Soja',    30, 60);

-- ---------------------------------------------------------------------
-- DADOS DE DEMONSTRAÇÃO (opcional - baseado nas telas do Figma)
-- Apague este bloco em produção. A senha abaixo NÃO é válida:
-- substitua senha_hash por um hash bcrypt real gerado pela API.
-- ---------------------------------------------------------------------
INSERT INTO usuarios (nome, email, senha_hash)
VALUES ('Maria Souza', 'maria@email.com', '$2b$10$SUBSTITUA_POR_UM_HASH_BCRYPT_REAL');

INSERT INTO propriedades (usuario_id, nome, cidade, estado)
SELECT id, 'Sítio Boa Esperança', 'Simões Filho', 'BA' FROM usuarios WHERE email = 'maria@email.com';

INSERT INTO dispositivos (propriedade_id, nome, identificador, token_api, status, ultimo_contato)
SELECT id, 'ESP32 Horta', 'esp32-horta-01', 'demo-token-esp32-01', 'online', NOW()
FROM propriedades WHERE nome = 'Sítio Boa Esperança';

INSERT INTO sensores (dispositivo_id, tipo, modelo, nome, unidade)
SELECT d.id, v.tipo::tipo_sensor, v.modelo, v.nome, v.unidade
FROM dispositivos d
CROSS JOIN (VALUES
    ('umidade_solo',   'Capacitivo', 'Sensor de solo 01', '%'),
    ('umidade_solo',   'Capacitivo', 'Sensor de solo 02', '%'),
    ('umidade_solo',   'Capacitivo', 'Sensor de solo 03', '%'),
    ('temperatura_ar', 'DHT22',      'Temperatura do ar', '°C'),
    ('umidade_ar',     'DHT22',      'Umidade do ar',     '%'),
    ('luminosidade',   'LDR',        'Luminosidade',      '%')
) AS v(tipo, modelo, nome, unidade)
WHERE d.identificador = 'esp32-horta-01';

INSERT INTO cultivos (propriedade_id, cultura_id, nome, sensor_umidade_id, dispositivo_bomba_id,
                      umidade_min, umidade_max, tempo_max_acionamento_min, vazao_bomba_l_min,
                      modo_irrigacao, irrigacao_pausada_motivo)
SELECT p.id, cu.id, v.canteiro, s.id, d.id, 30, 60, 15, 1.2, 'automatico', v.pausa
FROM propriedades p
JOIN dispositivos d ON d.propriedade_id = p.id
CROSS JOIN (VALUES
    ('Canteiro 2', 'Alface',  'Sensor de solo 01', NULL),
    ('Canteiro 1', 'Tomate',  'Sensor de solo 02', 'Aguardando intervalo mínimo entre ciclos'),
    ('Canteiro 3', 'Cenoura', 'Sensor de solo 03', NULL)
) AS v(canteiro, cultura, sensor, pausa)
JOIN culturas cu ON cu.nome = v.cultura AND cu.usuario_id IS NULL
JOIN sensores s ON s.nome = v.sensor AND s.dispositivo_id = d.id
WHERE p.nome = 'Sítio Boa Esperança';

-- Leituras das últimas 24h (a cada 30 min) + leitura atual
INSERT INTO leituras (sensor_id, valor, lida_em)
SELECT s.id,
       ROUND((CASE WHEN s.nome = 'Sensor de solo 01' THEN 35 + 12 * SIN(EXTRACT(EPOCH FROM t) / 7200)
                   WHEN s.nome = 'Sensor de solo 02' THEN 41 + 3 * SIN(EXTRACT(EPOCH FROM t) / 5400)
                   WHEN s.nome = 'Temperatura do ar'  THEN 29 + 3 * SIN(EXTRACT(EPOCH FROM t) / 9000)
                   ELSE 62 + 5 * SIN(EXTRACT(EPOCH FROM t) / 9000) END)::NUMERIC, 2),
       t
FROM sensores s
CROSS JOIN generate_series(NOW() - INTERVAL '24 hours', NOW() - INTERVAL '1 minute', INTERVAL '30 minutes') AS t
WHERE s.nome IN ('Sensor de solo 01', 'Sensor de solo 02', 'Temperatura do ar', 'Umidade do ar');

INSERT INTO leituras (sensor_id, valor, lida_em)
SELECT id, v.valor, NOW()
FROM sensores s
JOIN (VALUES ('Sensor de solo 01', 24.00), ('Sensor de solo 02', 41.00),
             ('Temperatura do ar', 31.00), ('Umidade do ar', 62.00)) AS v(nome, valor) ON v.nome = s.nome;

-- Sensor 03 (Canteiro 3): última leitura há 6 horas => "Sem leitura"
INSERT INTO leituras (sensor_id, valor, lida_em)
SELECT id, 38.00, NOW() - INTERVAL '6 hours' FROM sensores WHERE nome = 'Sensor de solo 03';

-- Histórico de irrigação do Canteiro 2
INSERT INTO irrigacoes (cultivo_id, usuario_id, origem, motivo, iniciado_em, finalizado_em, duracao_prevista_min, umidade_inicial, umidade_final)
SELECT c.id, NULL, 'automatica', 'Regra: solo abaixo de 30%',
       NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours' + INTERVAL '10 minutes', 15, 28, 41
FROM cultivos c WHERE c.nome = 'Canteiro 2';

INSERT INTO irrigacoes (cultivo_id, usuario_id, origem, motivo, iniciado_em, finalizado_em, duracao_prevista_min, umidade_inicial, umidade_final)
SELECT c.id, u.id, 'manual', 'Ação confirmada pelo usuário',
       NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day' + INTERVAL '8 minutes', 15, 31, 44
FROM cultivos c, usuarios u WHERE c.nome = 'Canteiro 2' AND u.email = 'maria@email.com';

-- Alertas ativos
INSERT INTO alertas (propriedade_id, cultivo_id, tipo, severidade, mensagem, acao_sugerida, valor, criado_em)
SELECT c.propriedade_id, c.id, v.tipo::tipo_alerta, v.sev::severidade_alerta, v.msg,
       'Ação: conferir o cultivo e a leitura do sensor.', v.valor, NOW() - v.atras
FROM cultivos c
JOIN (VALUES
    ('Canteiro 2', 'solo_abaixo_limite', 'atencao', 'Solo abaixo do limite configurado. Confira o cultivo.', 24.00, INTERVAL '1 hour'),
    ('Canteiro 3', 'sem_leitura_sensor', 'critico', 'Sem leitura do solo. Verifique a conexão do sensor.',    NULL,  INTERVAL '6 hours'),
    ('Canteiro 1', 'irrigacao_pausada',  'atencao', 'Irrigação automática pausada. Veja o motivo.',          NULL,  INTERVAL '7 hours')
) AS v(canteiro, tipo, sev, msg, valor, atras) ON v.canteiro = c.nome;

COMMIT;
