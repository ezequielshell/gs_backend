package br.com.fiap.solovivo.iot.mqtt;

import java.util.List;

/** Topicos do firmware: {base}/{dispositivo}/... (ver sketch.ino). */
public record MqttTopicos(String base) {

    /** Topicos que a API assina (o ESP32 publica). */
    public List<String> assinaturas() {
        return List.of(base + "/+/telemetria", base + "/+/irrigacao", base + "/+/status", base + "/+/config/ack");
    }

    /** O ESP32 assina: limites e modo (publicar com retain). */
    public String config(String dispositivo) {
        return base + "/" + dispositivo + "/config";
    }

    /** O ESP32 assina: {"acao":"ligar","duracao_min":15,"usuario_id":1} | {"acao":"desligar"}. */
    public String comando(String dispositivo) {
        return base + "/" + dispositivo + "/comando";
    }
}
