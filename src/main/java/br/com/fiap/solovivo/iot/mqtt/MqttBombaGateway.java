package br.com.fiap.solovivo.iot.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.solovivo.config.SoloVivoProperties;
import br.com.fiap.solovivo.iot.BombaGateway;
import br.com.fiap.solovivo.iot.BombaIndisponivelException;
import br.com.fiap.solovivo.iot.ComandoBomba;
import br.com.fiap.solovivo.iot.ConfirmacoesComando;
import br.com.fiap.solovivo.iot.EstadoDispositivos;
import br.com.fiap.solovivo.iot.ResultadoComando;

/**
 * Bomba do ESP32 via MQTT. Publica em {base}/{dispositivo}/comando exatamente o formato que o firmware
 * espera e AGUARDA o evento em /irrigacao ("inicio" para ligar, "fim" para desligar). Sem esse evento
 * dentro do tempo limite, o comando e considerado nao confirmado (CT-08) e a API responde 503.
 */
@Component
@ConditionalOnProperty(name = "solovivo.mqtt.habilitado", havingValue = "true")
public class MqttBombaGateway implements BombaGateway {

    private static final Logger log = LoggerFactory.getLogger(MqttBombaGateway.class);
    private static final Duration ESTADO_RECENTE = Duration.ofSeconds(20);

    private final MqttClient client;
    private final MqttTopicos topicos;
    private final ConfirmacoesComando confirmacoes;
    private final EstadoDispositivos estados;
    private final ObjectMapper objectMapper;
    private final long timeoutSegundos;

    public MqttBombaGateway(MqttClient client, MqttTopicos topicos, ConfirmacoesComando confirmacoes,
                            EstadoDispositivos estados, ObjectMapper objectMapper, SoloVivoProperties properties) {
        this.client = client;
        this.topicos = topicos;
        this.confirmacoes = confirmacoes;
        this.estados = estados;
        this.objectMapper = objectMapper;
        this.timeoutSegundos = properties.mqtt().timeoutConfirmacaoS();
    }

    @Override
    public ResultadoComando ligar(ComandoBomba comando) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("acao", "ligar");
        corpo.put("duracao_min", comando.duracaoMaxMin());
        corpo.put("usuario_id", comando.usuarioId());
        enviarEAguardar(comando, corpo, "inicio");
        return ResultadoComando.peloDispositivo();
    }

    @Override
    public ResultadoComando desligar(ComandoBomba comando) {
        try {
            enviarEAguardar(comando, Map.of("acao", "desligar"), "fim");
            return ResultadoComando.peloDispositivo();
        } catch (BombaIndisponivelException e) {
            // O firmware ignora "desligar" se a bomba ja estiver desligada (nao publica "fim").
            // Se a telemetria recente diz que a bomba esta desligada, a API fecha o registro sozinha.
            boolean jaDesligada = estados.recente(comando.dispositivo(), ESTADO_RECENTE)
                    .map(est -> Boolean.FALSE.equals(est.bomba())).orElse(false);
            if (jaDesligada) {
                log.info("[MQTT] {} ja estava com a bomba desligada (telemetria)", comando.dispositivo());
                return ResultadoComando.local();
            }
            throw e;
        }
    }

    private void enviarEAguardar(ComandoBomba comando, Map<String, Object> corpo, String eventoEsperado) {
        if (comando.dispositivo() == null) {
            throw new BombaIndisponivelException("Cultivo sem dispositivo de bomba vinculado.");
        }
        if (!client.isConnected()) {
            throw new BombaIndisponivelException("Broker MQTT desconectado.");
        }
        CompletableFuture<Void> confirmacao = confirmacoes.aguardar(comando.dispositivo(), eventoEsperado);
        try {
            MqttMessage msg = new MqttMessage(objectMapper.writeValueAsString(corpo).getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            String topico = topicos.comando(comando.dispositivo());
            client.publish(topico, msg);
            log.info("[MQTT] Comando {} publicado em {}; aguardando evento '{}'", corpo, topico, eventoEsperado);
            confirmacao.get(timeoutSegundos, TimeUnit.SECONDS);
            log.info("[MQTT] {} confirmou o comando (evento '{}')", comando.dispositivo(), eventoEsperado);
        } catch (TimeoutException e) {
            throw new BombaIndisponivelException("O ESP32 " + comando.dispositivo() + " não confirmou o comando em "
                    + timeoutSegundos + " s.");
        } catch (MqttException | JsonProcessingException | ExecutionException e) {
            throw new BombaIndisponivelException("Falha ao enviar comando MQTT: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BombaIndisponivelException("Envio do comando interrompido.", e);
        } finally {
            confirmacoes.cancelar(comando.dispositivo(), eventoEsperado, confirmacao);
        }
    }
}
