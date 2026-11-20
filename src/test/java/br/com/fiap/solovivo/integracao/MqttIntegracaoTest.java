package br.com.fiap.solovivo.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;

/**
 * Integracao MQTT de ponta a ponta: API (cliente Paho real) <-> broker MQTT embarcado <-> "ESP32 falso"
 * que responde exatamente como o firmware sketch.ino (topicos, JSON e eventos de confirmacao).
 */
@DisplayName("IoT | Integração MQTT com o firmware do ESP32 (broker embarcado)")
@TestPropertySource(properties = {
        "solovivo.mqtt.habilitado=true",
        "solovivo.mqtt.topico-base=" + MqttIntegracaoTest.BASE,
        "solovivo.mqtt.timeout-confirmacao-s=3"})
class MqttIntegracaoTest extends IntegracaoBase {

    static final String BASE = "teste/solovivo";
    static final String ESP = "esp32-horta-01";
    private static final Duration ESPERA = Duration.ofSeconds(10);

    private static Server broker;
    private static String urlBroker;
    private static EspFalso esp;

    @Autowired
    private MqttClient clienteDaApi;

    @DynamicPropertySource
    static void brokerEmbarcado(DynamicPropertyRegistry registry) {
        iniciarBroker();
        registry.add("solovivo.mqtt.broker-url", () -> urlBroker);
    }

    private static synchronized void iniciarBroker() {
        if (broker != null) {
            return;
        }
        try (ServerSocket livre = new ServerSocket(0)) {
            int porta = livre.getLocalPort();
            livre.close();
            Properties p = new Properties();
            p.setProperty("host", "127.0.0.1");
            p.setProperty("port", String.valueOf(porta));
            p.setProperty("allow_anonymous", "true");
            p.setProperty("persistence_enabled", "false");
            p.setProperty("websocket_port", "disabled");
            p.setProperty("telemetry_enabled", "false"); // nao enviar estatisticas para fora
            broker = new Server();
            broker.startServer(new MemoryConfig(p));
            urlBroker = "tcp://127.0.0.1:" + porta;
            Runtime.getRuntime().addShutdownHook(new Thread(broker::stopServer));
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel iniciar o broker MQTT de teste", e);
        }
    }

    @BeforeEach
    void prepararEsp32() throws Exception {
        if (esp == null) {
            esp = new EspFalso(urlBroker);
        }
        esp.responder = true;
        await().atMost(ESPERA).until(clienteDaApi::isConnected);
        // Garante que a API ja assinou os topicos: o status publicado pelo ESP32 chega ao banco.
        await().atMost(ESPERA).pollInterval(Duration.ofMillis(300)).untilAsserted(() -> {
            esp.publicar("status", "offline");
            assertThat(statusDoDispositivo()).isEqualTo("offline");
        });
        esp.publicar("status", "online");
        await().atMost(ESPERA).until(() -> "online".equals(statusDoDispositivo()));
        esp.configs.clear();
        esp.comandos.clear();
    }

    private String statusDoDispositivo() {
        return jdbc.queryForObject("SELECT status::text FROM dispositivos WHERE identificador = ?", String.class, ESP);
    }

    private long irrigacoesAbertas(long cultivoId) {
        return contar("SELECT COUNT(*) FROM irrigacoes WHERE cultivo_id = ? AND finalizado_em IS NULL", cultivoId);
    }

    @Test
    @DisplayName("Telemetria do ESP32 vira leituras (solo, temperatura, ar, luminosidade) e o app mostra")
    void telemetriaGravaLeituras() throws Exception {
        long id = idCultivo("Canteiro 2");
        long irrigacoesAntes = contar("SELECT COUNT(*) FROM irrigacoes");

        esp.publicar("telemetria", """
                {"dispositivo":"esp32-horta-01","umidade_solo":27.5,"temperatura":30.0,"umidade_ar":58.0,
                 "luminosidade":80.0,"bomba":false,"modo":"automatico","automatica_ativa":true,"pausa":null,"rssi":-60}""");

        await().atMost(ESPERA).until(() -> contar("""
                SELECT COUNT(*) FROM leituras l JOIN sensores s ON s.id = l.sensor_id
                 WHERE s.tipo = 'luminosidade'""") == 1);
        mvc.perform(get("/api/cultivos/" + id).header("Authorization", bearerMaria()))
                .andExpect(jsonPath("$.umidadeSolo").value(27.5))
                .andExpect(jsonPath("$.temperatura").value(30.0))
                .andExpect(jsonPath("$.umidadeAr").value(58.0))
                .andExpect(jsonPath("$.status").value("atencao"));
        // Quem decide ligar a bomba e o ESP32 (controle local): a API nao cria irrigacao sozinha.
        assertThat(contar("SELECT COUNT(*) FROM irrigacoes")).isEqualTo(irrigacoesAntes);
    }

    @Test
    @DisplayName("Irrigação automática decidida pelo ESP32: eventos início/fim viram histórico (10 min, 12 L)")
    void eventosAutomaticosDoEsp32() throws Exception {
        long id = idCultivo("Canteiro 2");
        esp.publicar("irrigacao", """
                {"dispositivo":"esp32-horta-01","evento":"inicio","origem":"automatica",
                 "motivo":"Regra: solo abaixo de 30%","umidade":24.0,"duracao_prevista_min":15,
                 "vazao_l_min":1.2,"usuario_id":null}""");
        await().atMost(ESPERA).until(() -> irrigacoesAbertas(id) == 1);
        assertThat(jdbc.queryForMap("""
                SELECT origem::text AS origem, motivo, usuario_id FROM irrigacoes
                 WHERE cultivo_id = ? AND finalizado_em IS NULL""", id))
                .containsEntry("origem", "automatica")
                .containsEntry("motivo", "Regra: solo abaixo de 30%")
                .containsEntry("usuario_id", null);
        mvc.perform(get("/api/cultivos/" + id).header("Authorization", bearerMaria()))
                .andExpect(jsonPath("$.irrigacaoAtiva").value(true));

        jdbc.update("UPDATE irrigacoes SET iniciado_em = NOW() - INTERVAL '10 minutes' WHERE finalizado_em IS NULL");
        esp.publicar("irrigacao", """
                {"dispositivo":"esp32-horta-01","evento":"fim","origem":"automatica","motivo_fim":"umidade_max",
                 "umidade":61.0,"duracao_s":600,"volume_l":12.0,"usuario_id":null}""");
        await().atMost(ESPERA).until(() -> irrigacoesAbertas(id) == 0);

        assertThat(jdbc.queryForMap("""
                SELECT duracao_segundos, volume_litros::float8 AS volume, umidade_final::float8 AS umidade_final
                  FROM irrigacoes WHERE cultivo_id = ? ORDER BY iniciado_em DESC LIMIT 1""", id))
                .containsEntry("duracao_segundos", 600)
                .containsEntry("volume", 12.0)
                .containsEntry("umidade_final", 61.0);
        mvc.perform(get("/api/cultivos/" + id + "/historico").param("periodo", "hoje").header("Authorization", bearerMaria()))
                .andExpect(jsonPath("$.acionamentos[0].origem").value("automatico"))
                .andExpect(jsonPath("$.acionamentos[0].duracaoMin").value(10));
    }

    @Test
    @DisplayName("CT-07 via MQTT: app liga/desliga, ESP32 confirma pelo evento e a irrigação é gravada uma vez")
    void comandoManualConfirmadoPeloEsp32() throws Exception {
        String auth = bearerMaria();
        long id = idCultivo("Canteiro 2");
        long maria = contar("SELECT id FROM usuarios WHERE email = 'maria@email.com'");
        long totalAntes = contar("SELECT COUNT(*) FROM irrigacoes WHERE cultivo_id = ?", id);

        mvc.perform(post("/api/cultivos/" + id + "/irrigacao/acionar").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modo\":\"manual\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.irrigacaoAtiva").value(true))
                .andExpect(jsonPath("$.modoIrrigacao").value("manual"));

        JsonNode ligar = esp.comandos.poll(5, TimeUnit.SECONDS);
        assertThat(ligar).isNotNull();
        assertThat(ligar.path("acao").asText()).isEqualTo("ligar");
        assertThat(ligar.path("duracao_min").asInt()).isEqualTo(15);
        assertThat(ligar.path("usuario_id").asLong()).isEqualTo(maria);
        assertThat(contar("SELECT COUNT(*) FROM irrigacoes WHERE cultivo_id = ?", id)).isEqualTo(totalAntes + 1);
        assertThat(jdbc.queryForMap("""
                SELECT origem::text AS origem, usuario_id FROM irrigacoes
                 WHERE cultivo_id = ? AND finalizado_em IS NULL""", id))
                .containsEntry("origem", "manual")
                .containsEntry("usuario_id", maria);

        // O modo mudou (automatico -> manual): o ESP32 recebe a nova configuracao
        JsonNode cfg = esp.configs.poll(5, TimeUnit.SECONDS);
        assertThat(cfg).isNotNull();
        assertThat(cfg.path("modo").asText()).isEqualTo("manual");

        mvc.perform(post("/api/cultivos/" + id + "/irrigacao/acionar").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modo\":\"manual\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.irrigacaoAtiva").value(false));
        assertThat(esp.comandos.poll(5, TimeUnit.SECONDS).path("acao").asText()).isEqualTo("desligar");
        assertThat(irrigacoesAbertas(id)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM irrigacoes WHERE cultivo_id = ?", id)).isEqualTo(totalAntes + 1);
    }

    @Test
    @DisplayName("CT-08 via MQTT: ESP32 não confirma -> 503, nada gravado e modo inalterado")
    void comandoSemConfirmacaoDoEsp32() throws Exception {
        esp.responder = false;
        long id = idCultivo("Canteiro 2");
        long antes = contar("SELECT COUNT(*) FROM irrigacoes");

        mvc.perform(post("/api/cultivos/" + id + "/irrigacao/acionar").header("Authorization", bearerMaria())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modo\":\"manual\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("Não foi possível confirmar o comando")));

        assertThat(esp.comandos.poll(5, TimeUnit.SECONDS).path("acao").asText()).isEqualTo("ligar");
        assertThat(contar("SELECT COUNT(*) FROM irrigacoes")).isEqualTo(antes);
        mvc.perform(get("/api/cultivos/" + id).header("Authorization", bearerMaria()))
                .andExpect(jsonPath("$.irrigacaoAtiva").value(false))
                .andExpect(jsonPath("$.modoIrrigacao").value("automatico"));
    }

    @Test
    @DisplayName("Limites do app são publicados para o ESP32 em /config (retain), só para o canteiro dele")
    void limitesPublicadosParaEsp32() throws Exception {
        String auth = bearerMaria();
        mvc.perform(put("/api/cultivos/" + idCultivo("Canteiro 2") + "/limites").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("umidadeMin", 25, "umidadeMax", 65, "tempoMaximoMin", 10,
                                "vazaoLMin", 1.5, "irrigacaoAutomaticaAtivada", false))))
                .andExpect(status().isOk());
        JsonNode cfg = esp.configs.poll(5, TimeUnit.SECONDS);
        assertThat(cfg).isNotNull();
        assertThat(cfg.path("umidade_min").asDouble()).isEqualTo(25.0);
        assertThat(cfg.path("umidade_max").asDouble()).isEqualTo(65.0);
        assertThat(cfg.path("tempo_max_min").asInt()).isEqualTo(10);
        assertThat(cfg.path("vazao_l_min").asDouble()).isEqualTo(1.5);
        assertThat(cfg.path("modo").asText()).isEqualTo("automatico");
        assertThat(cfg.path("automatica_ativa").asBoolean()).isFalse();

        // Canteiro 1 nao esta no rele do ESP32: nao gera /config
        mvc.perform(put("/api/cultivos/" + idCultivo("Canteiro 1") + "/limites").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("umidadeMin", 35, "umidadeMax", 70, "tempoMaximoMin", 12,
                                "vazaoLMin", 1.0, "irrigacaoAutomaticaAtivada", true))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/cultivos/" + idCultivo("Canteiro 2") + "/limites").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("umidadeMin", 26, "umidadeMax", 60, "tempoMaximoMin", 15,
                                "vazaoLMin", 1.2, "irrigacaoAutomaticaAtivada", true))))
                .andExpect(status().isOk());
        assertThat(esp.configs.poll(5, TimeUnit.SECONDS).path("umidade_min").asDouble()).isEqualTo(26.0);

        // Retain: um ESP32 que (re)conecta recebe a ultima configuracao na hora, sem nova publicacao.
        // (So o conteudo e verificado: o Moquette nao marca a flag RETAIN nessa entrega.)
        BlockingQueue<MqttMessage> retidas = new LinkedBlockingQueue<>();
        MqttClient novo = new MqttClient(urlBroker, "esp32-reconectando", new MemoryPersistence());
        novo.connect();
        novo.subscribe(BASE + "/" + ESP + "/config", 1, (t, m) -> retidas.add(m));
        MqttMessage retida = retidas.poll(5, TimeUnit.SECONDS);
        novo.disconnect();
        novo.close();
        assertThat(retida).isNotNull();
        assertThat(new ObjectMapper().readTree(retida.getPayload()).path("umidade_min").asDouble()).isEqualTo(26.0);
    }

    @Test
    @DisplayName("Pausa informada pelo ESP32 aparece no app com alerta; some quando a pausa termina")
    void pausaDoEsp32() throws Exception {
        jdbc.update("UPDATE alertas SET resolvido_em = NOW()");
        long id = idCultivo("Canteiro 2");
        esp.publicar("telemetria", """
                {"umidade_solo":24.0,"temperatura":30.0,"umidade_ar":60.0,"luminosidade":70.0,"bomba":false,
                 "modo":"automatico","automatica_ativa":true,"pausa":"Aguardando intervalo mínimo entre ciclos"}""");
        await().atMost(ESPERA).until(() -> "Aguardando intervalo mínimo entre ciclos".equals(jdbc.queryForObject(
                "SELECT irrigacao_pausada_motivo FROM cultivos WHERE id = ?", String.class, id)));

        String auth = bearerMaria();
        mvc.perform(get("/api/cultivos/" + id).header("Authorization", auth))
                .andExpect(jsonPath("$.irrigacaoPausadaMotivo").value("Aguardando intervalo mínimo entre ciclos"));
        mvc.perform(get("/api/alertas").header("Authorization", auth))
                .andExpect(jsonPath("$[*].tipo", hasItem("irrigacao_pausada")));

        esp.publicar("telemetria", """
                {"umidade_solo":35.0,"bomba":false,"modo":"automatico","automatica_ativa":true,"pausa":null}""");
        await().atMost(ESPERA).until(() -> jdbc.queryForObject(
                "SELECT irrigacao_pausada_motivo FROM cultivos WHERE id = ?", String.class, id) == null);
        assertThat(contar("SELECT COUNT(*) FROM alertas WHERE resolvido_em IS NULL AND tipo = 'irrigacao_pausada'"))
                .isZero();
    }

    @Test
    @DisplayName("Sincronização: telemetria 'bomba desligada' fecha irrigação esquecida; usuário desconhecido vira o dono")
    void sincronizacaoComTelemetria() throws Exception {
        long id = idCultivo("Canteiro 2");
        long maria = contar("SELECT id FROM usuarios WHERE email = 'maria@email.com'");
        // Botao do Node-RED manda usuario_id fixo; se nao existir, a irrigacao fica no nome do dono
        esp.publicar("irrigacao", """
                {"evento":"inicio","origem":"manual","motivo":"Ação confirmada pelo usuário","umidade":28.0,
                 "duracao_prevista_min":15,"vazao_l_min":1.2,"usuario_id":999}""");
        await().atMost(ESPERA).until(() -> irrigacoesAbertas(id) == 1);
        assertThat(contar("SELECT usuario_id FROM irrigacoes WHERE cultivo_id = ? AND finalizado_em IS NULL", id))
                .isEqualTo(maria);

        // O evento "fim" se perdeu; a telemetria diz que a bomba esta desligada
        jdbc.update("UPDATE irrigacoes SET iniciado_em = NOW() - INTERVAL '2 minutes' WHERE finalizado_em IS NULL");
        esp.publicar("telemetria", "{\"umidade_solo\":42.0,\"bomba\":false,\"modo\":\"manual\",\"pausa\":null}");
        await().atMost(ESPERA).until(() -> irrigacoesAbertas(id) == 0);
    }

    @Test
    @DisplayName("Canteiros fora do ESP32 seguem simulados; leitura HTTP do canteiro do ESP32 não aciona a API")
    void outrosCanteirosSeguemSimulados() throws Exception {
        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor de solo 03\",\"valor\":20}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisoes[0].cultivo").value("Canteiro 3"))
                .andExpect(jsonPath("$.decisoes[0].acao").value("LIGAR_BOMBA"));
        assertThat(esp.comandos.poll(1, TimeUnit.SECONDS)).isNull(); // bomba simulada, nada no MQTT

        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor de solo 01\",\"valor\":24}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisoes[0].cultivo").value("Canteiro 2"))
                .andExpect(jsonPath("$.decisoes[0].acao").value("CONTROLE_LOCAL"));
        assertThat(irrigacoesAbertas(idCultivo("Canteiro 2"))).isZero();
    }

    /** Simula o firmware: assina /config e /comando e responde aos comandos com eventos em /irrigacao. */
    static final class EspFalso implements MqttCallback {

        final BlockingQueue<JsonNode> configs = new LinkedBlockingQueue<>();
        final BlockingQueue<JsonNode> comandos = new LinkedBlockingQueue<>();
        volatile boolean responder = true;
        private final MqttClient cliente;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final ObjectMapper om = new ObjectMapper();

        EspFalso(String url) throws MqttException {
            cliente = new MqttClient(url, "esp32-falso-teste", new MemoryPersistence());
            cliente.setCallback(this);
            MqttConnectOptions opcoes = new MqttConnectOptions();
            opcoes.setCleanSession(true);
            cliente.connect(opcoes);
            cliente.subscribe(BASE + "/" + ESP + "/config", 1);
            cliente.subscribe(BASE + "/" + ESP + "/comando", 1);
        }

        void publicar(String sufixo, String payload) throws MqttException {
            MqttMessage m = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            m.setQos(1);
            cliente.publish(BASE + "/" + ESP + "/" + sufixo, m);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            JsonNode json = om.readTree(message.getPayload());
            if (topic.endsWith("/config")) {
                configs.add(json);
                return;
            }
            comandos.add(json);
            if (!responder) {
                return;
            }
            executor.submit(() -> { // fora da thread de callback, como no firmware (loop)
                try {
                    if ("ligar".equals(json.path("acao").asText())) {
                        publicar("irrigacao", String.format("""
                                {"dispositivo":"esp32-horta-01","evento":"inicio","origem":"manual",
                                 "motivo":"Ação confirmada pelo usuário","umidade":24.0,
                                 "duracao_prevista_min":%d,"vazao_l_min":1.2,"usuario_id":%s}""",
                                json.path("duracao_min").asInt(15), json.path("usuario_id").asText("null")));
                    } else if ("desligar".equals(json.path("acao").asText())) {
                        publicar("irrigacao", """
                                {"dispositivo":"esp32-horta-01","evento":"fim","origem":"manual",
                                 "motivo_fim":"comando","umidade":40.0,"duracao_s":5,"volume_l":0.1}""");
                    }
                } catch (MqttException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        @Override
        public void connectionLost(Throwable cause) {
            // teste
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // teste
        }
    }
}
