package br.com.fiap.solovivo.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.fiap.solovivo.iot.mqtt.MqttMensagemParser;
import br.com.fiap.solovivo.iot.mqtt.MqttMensagemParser.TipoTopico;
import br.com.fiap.solovivo.iot.mqtt.MqttTopicos;

/** Mensagens copiadas do formato publicado pelo firmware (solo-vivo-arduino/sketch.ino). */
@DisplayName("Unidade | MQTT: mensagens do firmware do ESP32")
class MqttMensagemParserTest {

    private static final String BASE = "fiap/iot/3esoa/tech-group/solovivo";

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Tópicos do firmware: dispositivo e tipo")
    void topicos() {
        var t = MqttMensagemParser.topico(BASE, BASE + "/esp32-horta-01/telemetria");
        assertThat(t.dispositivo()).isEqualTo("esp32-horta-01");
        assertThat(t.tipo()).isEqualTo(TipoTopico.TELEMETRIA);
        assertThat(MqttMensagemParser.topico(BASE, BASE + "/esp32-horta-01/irrigacao").tipo()).isEqualTo(TipoTopico.IRRIGACAO);
        assertThat(MqttMensagemParser.topico(BASE, BASE + "/esp32-horta-01/status").tipo()).isEqualTo(TipoTopico.STATUS);
        assertThat(MqttMensagemParser.topico(BASE, BASE + "/esp32-horta-01/config/ack").tipo()).isEqualTo(TipoTopico.CONFIG_ACK);

        assertThatThrownBy(() -> MqttMensagemParser.topico(BASE, "outro/topico/x/telemetria"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MqttMensagemParser.topico(BASE, BASE + "/esp32-horta-01/comando"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Tópicos que a API assina e publica")
    void topicosDaApi() {
        MqttTopicos topicos = new MqttTopicos(BASE);
        assertThat(topicos.assinaturas()).contains(BASE + "/+/telemetria", BASE + "/+/irrigacao");
        assertThat(topicos.config("esp32-horta-01")).isEqualTo(BASE + "/esp32-horta-01/config");
        assertThat(topicos.comando("esp32-horta-01")).isEqualTo(BASE + "/esp32-horta-01/comando");
    }

    @Test
    @DisplayName("Telemetria completa; DHT22 com falha (null/NaN) vira null")
    void telemetria() {
        var t = MqttMensagemParser.telemetria(b("""
                {"dispositivo":"esp32-horta-01","umidade_solo":24.0,"temperatura":31.0,"umidade_ar":62.0,
                 "luminosidade":80.5,"bomba":false,"modo":"automatico","automatica_ativa":true,
                 "pausa":"Aguardando intervalo mínimo entre ciclos","rssi":-60}"""));
        assertThat(t.umidadeSolo()).isEqualTo(24.0);
        assertThat(t.temperatura()).isEqualTo(31.0);
        assertThat(t.luminosidade()).isEqualTo(80.5);
        assertThat(t.bomba()).isFalse();
        assertThat(t.modo()).isEqualTo("automatico");
        assertThat(t.pausa()).isEqualTo("Aguardando intervalo mínimo entre ciclos");

        var falha = MqttMensagemParser.telemetria(b(
                "{\"umidade_solo\":40,\"temperatura\":null,\"umidade_ar\":NaN,\"pausa\":null,\"bomba\":true}"));
        assertThat(falha.temperatura()).isNull();
        assertThat(falha.umidadeAr()).isNull();
        assertThat(falha.pausa()).isNull();
    }

    @Test
    @DisplayName("Eventos de irrigação: início automático e fim por umidade máxima")
    void eventos() {
        var inicio = MqttMensagemParser.eventoIrrigacao(b("""
                {"dispositivo":"esp32-horta-01","evento":"inicio","origem":"automatica",
                 "motivo":"Regra: solo abaixo de 30%","umidade":24.0,"duracao_prevista_min":15,
                 "vazao_l_min":1.2,"usuario_id":null}"""));
        assertThat(inicio.evento()).isEqualTo("inicio");
        assertThat(inicio.origem()).isEqualTo("automatica");
        assertThat(inicio.duracaoPrevistaMin()).isEqualTo(15);
        assertThat(inicio.usuarioId()).isNull();

        var fim = MqttMensagemParser.eventoIrrigacao(b("""
                {"dispositivo":"esp32-horta-01","evento":"fim","origem":"manual","motivo_fim":"comando",
                 "umidade":45.0,"duracao_s":480,"volume_l":9.6,"usuario_id":1}"""));
        assertThat(fim.motivoFim()).isEqualTo("comando");
        assertThat(fim.duracaoS()).isEqualTo(480L);
        assertThat(fim.volumeL()).isEqualTo(9.6);
        assertThat(fim.usuarioId()).isEqualTo(1L);

        assertThatThrownBy(() -> MqttMensagemParser.eventoIrrigacao(b("{\"evento\":\"outro\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MqttMensagemParser.eventoIrrigacao(b("nao-e-json")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Status online/offline e confirmação de configuração")
    void statusEAck() {
        assertThat(MqttMensagemParser.status(b("online"))).isEqualTo("online");
        assertThat(MqttMensagemParser.status(b("offline"))).isEqualTo("offline");
        assertThatThrownBy(() -> MqttMensagemParser.status(b("talvez"))).isInstanceOf(IllegalArgumentException.class);

        var ack = MqttMensagemParser.configAck(b("{\"dispositivo\":\"esp32-horta-01\",\"ok\":false,\"erro\":\"faixa de umidade invalida\"}"));
        assertThat(ack.ok()).isFalse();
        assertThat(ack.erro()).isEqualTo("faixa de umidade invalida");
    }
}
