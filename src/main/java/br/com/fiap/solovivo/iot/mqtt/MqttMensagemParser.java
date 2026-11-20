package br.com.fiap.solovivo.iot.mqtt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Converte as mensagens publicadas pelo firmware (sketch.ino) em objetos Java.
 * Topicos: {base}/{dispositivo}/telemetria | irrigacao | status | config/ack
 */
public final class MqttMensagemParser {

    public enum TipoTopico { TELEMETRIA, IRRIGACAO, STATUS, CONFIG_ACK }

    public record Topico(String dispositivo, TipoTopico tipo) {
    }

    /** publicarTelemetria() do firmware. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Telemetria(
            @JsonProperty("umidade_solo") Double umidadeSolo,
            @JsonProperty("temperatura") Double temperatura,
            @JsonProperty("umidade_ar") Double umidadeAr,
            @JsonProperty("luminosidade") Double luminosidade,
            @JsonProperty("bomba") Boolean bomba,
            @JsonProperty("modo") String modo,
            @JsonProperty("automatica_ativa") Boolean automaticaAtiva,
            @JsonProperty("pausa") String pausa) {
    }

    /** publicarEventoInicio() / publicarEventoFim() do firmware. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventoIrrigacao(
            @JsonProperty("evento") String evento,
            @JsonProperty("origem") String origem,
            @JsonProperty("motivo") String motivo,
            @JsonProperty("umidade") Double umidade,
            @JsonProperty("duracao_prevista_min") Integer duracaoPrevistaMin,
            @JsonProperty("vazao_l_min") Double vazaoLMin,
            @JsonProperty("usuario_id") Long usuarioId,
            @JsonProperty("motivo_fim") String motivoFim,
            @JsonProperty("duracao_s") Long duracaoS,
            @JsonProperty("volume_l") Double volumeL) {
    }

    /** publicarAckConfig() do firmware. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfigAck(@JsonProperty("ok") Boolean ok, @JsonProperty("erro") String erro) {
    }

    /** Aceita NaN (sensor com falha) sem quebrar a leitura do JSON. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .build();

    private MqttMensagemParser() {
    }

    public static Topico topico(String base, String topico) {
        String prefixo = base + "/";
        if (topico == null || !topico.startsWith(prefixo)) {
            throw new IllegalArgumentException("Topico fora da base " + base + ": " + topico);
        }
        String resto = topico.substring(prefixo.length());
        int barra = resto.indexOf('/');
        if (barra <= 0) {
            throw new IllegalArgumentException("Topico sem dispositivo: " + topico);
        }
        String dispositivo = resto.substring(0, barra);
        TipoTopico tipo = switch (resto.substring(barra + 1)) {
            case "telemetria" -> TipoTopico.TELEMETRIA;
            case "irrigacao" -> TipoTopico.IRRIGACAO;
            case "status" -> TipoTopico.STATUS;
            case "config/ack" -> TipoTopico.CONFIG_ACK;
            default -> throw new IllegalArgumentException("Topico nao tratado pela API: " + topico);
        };
        return new Topico(dispositivo, tipo);
    }

    public static Telemetria telemetria(byte[] payload) {
        Telemetria t = ler(payload, Telemetria.class);
        return new Telemetria(numero(t.umidadeSolo()), numero(t.temperatura()), numero(t.umidadeAr()),
                numero(t.luminosidade()), t.bomba(), t.modo(), t.automaticaAtiva(),
                t.pausa() == null || t.pausa().isBlank() ? null : t.pausa());
    }

    public static EventoIrrigacao eventoIrrigacao(byte[] payload) {
        EventoIrrigacao e = ler(payload, EventoIrrigacao.class);
        if (!"inicio".equals(e.evento()) && !"fim".equals(e.evento())) {
            throw new IllegalArgumentException("Evento de irrigacao desconhecido: " + e.evento());
        }
        return e;
    }

    public static ConfigAck configAck(byte[] payload) {
        return ler(payload, ConfigAck.class);
    }

    /** /status e texto puro: "online" ou "offline". */
    public static String status(byte[] payload) {
        String s = new String(payload, StandardCharsets.UTF_8).trim().replace("\"", "");
        if (!"online".equals(s) && !"offline".equals(s)) {
            throw new IllegalArgumentException("Status desconhecido: " + s);
        }
        return s;
    }

    private static <T> T ler(byte[] payload, Class<T> tipo) {
        try {
            return MAPPER.readValue(payload, tipo);
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON invalido na mensagem MQTT", e);
        }
    }

    /** null para NaN/infinito (falha de leitura do DHT22, por exemplo). */
    private static Double numero(Double v) {
        return v == null || v.isNaN() || v.isInfinite() ? null : v;
    }
}
