# Solo Vivo API (back-end)

API REST do **Solo Vivo**: irrigação inteligente e monitoramento ambiental para o pequeno produtor
(Global Solution 2026/2 – CitizenTech). O app mobile (Expo/React Native) consome esta API.

| Item | Tecnologia |
|---|---|
| Linguagem | **Java 21** |
| Framework | **Spring Boot 3.5** (Web, Data JPA, Security, Validation) |
| Banco | **PostgreSQL** (script do time de banco em `database/`) |
| Autenticação | JWT (HS256) + senhas com **BCrypt** + perfis **ADMIN** / **PRODUTOR** |
| Documentação | **Swagger / OpenAPI** (`/swagger-ui.html`, cópia em `docs/openapi.json`) |
| IoT | **ESP32 (Wokwi) via MQTT** – mesmo broker/tópicos do projeto Arduino – + simulador HTTP para os demais canteiros |
| Testes | JUnit 5 + MockMvc + PostgreSQL e broker MQTT embarcados (sem Docker), relatórios Surefire e JaCoCo |

---

## 1. Arquitetura em camadas

```
App mobile ──HTTPS/JSON──> Controller ──> Service ──> Repository ──SQL──> PostgreSQL
                             (HTTP,        (regras de    (Spring Data JPA +
                              validação)    negócio)      views/funções do banco)

ESP32 (Wokwi) ──MQTT──> broker.hivemq.com ──MQTT──> MqttListener ──> DispositivoEventosService ──> banco
   ^  telemetria / irrigacao / status / config/ack                       (leituras, alertas, irrigações)
   └── config (retain) / comando  <── MqttConfigPublisher / MqttBombaGateway <── app (limites, ligar/desligar)

Simulador HTTP ──JSON──> IotController ──> LeituraService ──> AutomacaoService ──> BombaRoteador
                                                               (motor de regras)    (ESP32 via MQTT ou bomba simulada)
```

```
src/main/java/br/com/fiap/solovivo
├── controller/   Endpoints REST (Auth, Cultivos, Alertas, Perfil, Culturas, Usuarios, IoT)
├── service/      Regras de negócio (RegraIrrigacao = motor de regras puro, testado isoladamente)
├── repository/   Spring Data JPA + ConsultaRepository (usa v_cultivos_status e resumo_historico do dump)
├── model/        Entidades JPA (espelham as tabelas do dump)
├── dto/          Formatos de entrada/saída (iguais a src/types/index.ts do app)
├── security/     JWT, filtro de autenticação, proteção contra força bruta
├── iot/          BombaRoteador (ESP32 ou bomba simulada), TopologiaIot + iot/mqtt (integração com o ESP32)
├── config/       Segurança, CORS, Swagger, propriedades
└── exception/    Tratamento global de erros (JSON com "message", sem stack trace)
```

---

## 2. Como executar

Pré-requisito: **Java 21** (o Maven vem junto pelo wrapper `mvnw`).

### Opção A – Modo demonstração (sem Docker, mais rápido)

Sobe um PostgreSQL embarcado, carrega `database/*.sql` e inicia a API. Os dados são recriados a cada execução.

```bash
# Windows
.\mvnw.cmd spring-boot:test-run
# Linux / Mac
./mvnw spring-boot:test-run
```

### Opção B – PostgreSQL no Docker

```bash
docker compose up -d db          # cria o banco solo_vivo com 01-dump-iot.sql e 02-ajustes-api.sql
.\mvnw.cmd spring-boot:run       # Windows   (Linux/Mac: ./mvnw spring-boot:run)
```

Para recriar o banco do zero (os dados de demonstração usam `NOW()`): `docker compose down -v && docker compose up -d db`.

Sem Docker, com PostgreSQL instalado: crie o banco e rode `psql -d solo_vivo -f database/01-dump-iot.sql`
e depois `psql -d solo_vivo -f database/02-ajustes-api.sql`. A conexão é configurada por variáveis de ambiente
(veja `.env.example`: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, ...).

### Acessos

| O quê | Onde |
|---|---|
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs (cópia em `docs/openapi.json`, importável no Postman) |

### Usuários de demonstração

| Perfil | E-mail | Senha |
|---|---|---|
| PRODUTOR | maria@email.com | solovivo123 |
| ADMIN | admin@solovivo.com | admin12345 |
| Dispositivo (ESP32) | header `X-Device-Token` | demo-token-esp32-01 |

---

## 3. Endpoints

Todos sob `/api`. Erros sempre em JSON: `{"status": 400, "message": "...", "campos": {...}}`.

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/auth/login` | público | Login → token JWT |
| POST | `/auth/register` | público | Cadastro de produtor (cria propriedade padrão) |
| GET | `/cultivos` | logado | Lista cultivos com status (`adequado`, `atencao`, `sem_leitura`) |
| GET | `/cultivos/{id}` | dono/admin | Detalhes + gráfico das últimas 24h |
| POST | `/cultivos` | logado | Cadastra cultivo (201) |
| PUT | `/cultivos/{id}/limites` | dono/admin | Limites de umidade, tempo máximo, vazão, automação |
| DELETE | `/cultivos/{id}` | dono/admin | Exclui (lógico) e desliga a bomba se estiver ligada (204) |
| GET | `/cultivos/{id}/historico?periodo=hoje\|7d\|30d` | dono/admin | Irrigações, consumo e tempo fora da faixa |
| POST | `/cultivos/{id}/irrigacao/acionar` | dono/admin | Liga/desliga a bomba ou troca o modo |
| GET | `/alertas?status=ativos\|resolvidos` | logado | Alertas |
| PUT | `/alertas/{id}/resolver` | dono/admin | Resolve um alerta |
| GET / PUT | `/perfil` | logado | Perfil e preferências de notificação |
| GET | `/culturas` | logado | Catálogo de culturas |
| POST / DELETE | `/culturas`, `/culturas/{id}` | **ADMIN** | Gerencia culturas padrão |
| GET | `/usuarios` | **ADMIN** | Lista usuários |
| POST | `/iot/leituras` | token do dispositivo | Recebe leituras, gera alertas e aplica a automação |

As rotas e os formatos JSON são exatamente os que o app já usa em `src/services/*.ts`.

---

## 4. Integração com o app mobile

No `.env` do app (pasta `GS_Irrigacao`):

```
EXPO_PUBLIC_API_BASE_URL=http://SEU_IP_NA_REDE:8080/api
EXPO_PUBLIC_USE_MOCK=false
```

- `localhost` só funciona no Expo Web/simulador iOS. No Android Emulator use `http://10.0.2.2:8080/api`;
  no celular físico use o IP do computador na rede (ex.: `http://192.168.0.10:8080/api`).
- O app ainda não tem tela de exclusão; o `DELETE /cultivos/{id}` pode ser demonstrado pelo Swagger.

---

## 5. Simulação de IoT (Parte 6)

O IoT tem duas partes que funcionam juntas:

1. **ESP32 no Wokwi** (projeto `solo-vivo/solo-vivo/solo-vivo-arduino`): sensor de solo (potenciômetro), DHT22,
   LDR e relé da bomba. Ele decide a irrigação automática **localmente** (funciona até sem internet) e conversa
   com a API por **MQTT** no broker `broker.hivemq.com:1883`. O Node-RED (`flows_solovivo.json`) mostra um painel.
2. **Simulador HTTP** (`simulador-iot/`): alimenta os canteiros que não têm hardware no circuito.

### Sensores

| Sensor | Mede | Como influencia o sistema |
|---|---|---|
| Umidade do solo (potenciômetro no Wokwi = "Sensor de solo 01"; sensores 02/03 pelo simulador HTTP) | Umidade do solo (%) | Abaixo do mínimo → alerta "Solo abaixo do limite" e, no modo automático, **liga a bomba**. Ao atingir o máximo → **desliga**. Acima do máximo → alerta. Sem dados por mais de 60 min → "Sem leitura" (crítico). |
| Temperatura do ar (DHT22) | Temperatura (°C) | Acima de 35 °C → alerta "Alta temperatura ambiente". Exibida no card do cultivo. |
| Umidade do ar (DHT22) | Umidade relativa (%) | Exibida no card do cultivo. |
| Luminosidade (LDR) | Luz (0–100%) | Gravada no histórico de leituras (estação ambiental). |

Leituras impossíveis (ex.: umidade 150%) são rejeitadas: o pacote inteiro é recusado (400) e nada é gravado.

### Integração MQTT com o ESP32

Tópicos (base `fiap/iot/3esoa/tech-group/solovivo/esp32-horta-01`, iguais ao `sketch.ino`):

| Tópico | Quem publica | O que a API faz |
|---|---|---|
| `/telemetria` | ESP32 (a cada 5 s) | Grava solo, temperatura, umidade do ar e luminosidade; gera alertas; mostra a pausa ("Aguardando intervalo mínimo entre ciclos") no app |
| `/irrigacao` | ESP32 | Grava o **início** e o **fim** de cada irrigação (automática ou manual) no histórico, com duração e volume |
| `/status` | ESP32 (Last Will) | Marca o dispositivo `online` / `offline` |
| `/config` | **API** (retain) | Envia limites, tempo máximo, vazão, modo e automação quando o app altera (e ao conectar) |
| `/config/ack` | ESP32 | API registra se o ESP32 aceitou ou recusou a configuração |
| `/comando` | **API** | Botão Ligar/Desligar do app → `{"acao":"ligar","duracao_min":15,"usuario_id":1}` / `{"acao":"desligar"}` |

- **Confirmação real (CT-08):** depois de publicar um comando, a API espera o evento do ESP32 em `/irrigacao`
  (até 8 s). Sem confirmação → **503** e nada é gravado.
- **Um único "cérebro" por bomba:** o circuito tem 1 sensor de solo e 1 relé, ligados ao **Canteiro 2**
  ("Sensor de solo 01" no banco). Para ele, quem decide é o ESP32 (a API só alerta e registra). Os Canteiros 1 e 3
  continuam com a regra da API + bomba simulada, alimentados pelo simulador HTTP.
- O dispositivo é identificado pelo tópico (`esp32-horta-01` = `dispositivos.identificador` no dump atualizado).
- O Node-RED também publica `/config` com retain: vale a **última** configuração enviada (app ou painel).

#### Executar com o ESP32 (Wokwi)

```bash
# 1) API com MQTT ligado
#    Windows:   $env:MQTT_ENABLED="true"; .\mvnw.cmd spring-boot:test-run
#    Linux/Mac: MQTT_ENABLED=true ./mvnw spring-boot:test-run
# 2) Wokwi: rode o projeto solo-vivo-arduino e gire o potenciômetro (esquerda = seco).
# 3) (opcional) Node-RED: importe flows_solovivo.json e abra /ui.
```

No log da API aparecem as mensagens `[MQTT]` e `[ESP32]` (telemetria, início/fim de irrigação, status).
⚠️ O broker HiveMQ é **público**: qualquer pessoa pode publicar nesses tópicos. Em produção, use um broker
privado com usuário/senha e TLS (`MQTT_BROKER_URL`, ou o Mosquitto local: `docker compose --profile mqtt up -d`
com `MQTT_BROKER_URL=tcp://localhost:1883`).

### Regras de segurança da irrigação automática
| Regra | ESP32 (Canteiro 2) | API (canteiros simulados) |
|---|---|---|
| Só liga no modo automático, com automação ativada | ✅ | ✅ (e sem `irrigacao_pausada_motivo`) |
| Nunca liga duas vezes | ✅ | ✅ |
| Intervalo mínimo entre ciclos | 1 min (demo) | 30 min |
| Desliga na umidade máxima | ✅ | ✅ (qualquer irrigação) |
| Desliga no tempo máximo de acionamento | ✅ | ✅ (rotina a cada 60 s) |
| Comando só é registrado após confirmação | evento `/irrigacao` | bomba simulada |

### Executar o simulador HTTP (com a API rodando)

```bash
java simulador-iot/SimuladorSensores.java --cenario seco
java simulador-iot/SimuladorSensores.java --cenario secando --ciclos 6 --intervalo 5
```

| Cenário | O que acontece |
|---|---|
| `seco` | Valores do plano de testes: solo 24%, 31 °C, ar 62% → alerta + irrigação automática do Canteiro 2 |
| `secando` | Solo do Canteiro 2 cai 3% por ciclo e cruza o mínimo ao vivo (bom para o vídeo) |
| `encharcado` | Solo 70% → desliga a bomba e alerta "acima do limite" |
| `calor` | 37,5 °C → alerta de alta temperatura |
| `sem-leitura` / `normal` | Sensor 03 não envia / tudo dentro da faixa |

A resposta da API mostra a decisão para cada cultivo (`LIGAR_BOMBA`, `DESLIGAR_BOMBA`, `NENHUMA`, ou
`CONTROLE_LOCAL` quando o canteiro é do ESP32) e os alertas abertos/resolvidos; o log da API registra as mesmas
decisões (`[Automação] ...`). Também é possível enviar `simulador-iot/exemplos/leituras-ct06.json` pelo Swagger
ou Postman. Com o ESP32 conectado, prefira o Wokwi para o Canteiro 2 e o simulador para os Canteiros 1 e 3.

---

## 6. Segurança (Parte 5)

- **Login seguro:** senhas só como hash **BCrypt**; JWT assinado (HS256) com expiração de 8h; segredo por variável de ambiente.
- **Perfis:** `ADMIN` (usuários, catálogo de culturas, vê todas as propriedades) e `PRODUTOR` (apenas os próprios dados).
  Recurso de outro produtor responde **404** (não revela que existe).
- **Boas práticas:**
  1. Validação de entrada (Bean Validation) com mensagens por campo;
  2. Proteção contra **SQL Injection**: JPA e consultas com parâmetros nomeados (nenhuma concatenação de entrada do usuário);
  3. Proteção contra **XSS**: campos de texto rejeitam `<` e `>`; respostas apenas JSON;
  4. **Força bruta:** 5 senhas erradas seguidas bloqueiam o e-mail por 5 min (HTTP 429);
  5. Mensagem genérica de login e comparação de hash mesmo para e-mail inexistente (evita descobrir e-mails);
  6. Erros sem stack trace; cabeçalhos de segurança do Spring Security; CORS restrito; sessão stateless;
  7. Dispositivos IoT autenticados por token próprio.

---

## 7. Testes (Parte 3)

```bash
.\mvnw.cmd verify        # Windows  (Linux/Mac: ./mvnw verify)
```

Roda **71 testes** (unidade + API com PostgreSQL real embarcado + MQTT com broker embarcado, sem Docker
e sem internet) e gera os relatórios:

| Relatório | Arquivo |
|---|---|
| Resultado dos testes (HTML) | `target/reports/surefire.html` |
| Cobertura de código (JaCoCo) | `target/site/jacoco/index.html` |
| XML por classe | `target/surefire-reports/` |

Mapeamento com o *Plano de Testes - Solo Vivo*:

| Caso | Teste automatizado |
|---|---|
| CT-01 Login válido (+ senha errada) | `PlanoDeTestesApiTest.ct01*` |
| CT-02 Acesso sem autenticação (401) | `PlanoDeTestesApiTest.ct02*` |
| CT-03 Cadastro do Canteiro 4 (+ dados inválidos) | `PlanoDeTestesApiTest.ct03*` |
| CT-04 Alerta de baixa umidade (24% < 30%) | `PlanoDeTestesApiTest.ct04*`, `RegraIrrigacaoTest` |
| CT-05 Sem leitura há 6h | `PlanoDeTestesApiTest.ct05*`, `FormatadorTest` |
| CT-06 Sensores simulados (24%, 31 °C, 62%) | `PlanoDeTestesApiTest.ct06*` |
| CT-07 Irrigação manual 10 min × 1,2 L/min = 12 L | `PlanoDeTestesApiTest.ct07*`, `MqttIntegracaoTest` (via ESP32), `RegraIrrigacaoTest` |
| CT-08 Falha no acionamento (503, sem registro) | `FalhaBombaApiTest`, `MqttIntegracaoTest` (ESP32 não responde) |
| CT-09 Irrigação automática + segurança | `PlanoDeTestesApiTest.ct09*`, `MqttIntegracaoTest` (eventos do ESP32), `RegraIrrigacaoTest` |
| Integração MQTT (telemetria, eventos, /config, /comando, pausa, status) | `MqttIntegracaoTest`, `MqttMensagemParserTest` |
| CT-10 Perda de conexão | Lado do app; a API devolve erros JSON com `message` exibida pelo app |
| Segurança (perfis, dono, força bruta, XSS, SQLi) | `SegurancaApiTest`, `SegurancaUnitTest` |

---

## 8. Ajustes no banco de dados

`database/01-dump-iot.sql` é uma **cópia sem alterações** do dump atualizado do time
(`solo-vivo/solo-vivo/dump-atualizado.sql`: dispositivo `esp32-horta-01` e sensor de luminosidade LDR).
`database/02-ajustes-api.sql` acrescenta o que a API precisa:

1. ENUM `perfil_usuario` e coluna `usuarios.perfil` (admin / produtor) – exigência da Parte 5;
2. hash BCrypt real para `maria@email.com` (o dump tinha o placeholder `SUBSTITUA_POR_UM_HASH_BCRYPT_REAL`);
3. usuário administrador de demonstração.

Sugestão: incorporar esses ajustes ao dump oficial entregue na Parte 1.

---
