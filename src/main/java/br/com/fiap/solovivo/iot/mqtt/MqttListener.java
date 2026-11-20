package br.com.fiap.solovivo.iot.mqtt;

import java.util.concurrent.ExecutorService;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import br.com.fiap.solovivo.iot.ConfirmacoesComando;
import br.com.fiap.solovivo.iot.mqtt.MqttMensagemParser.ConfigAck;
import br.com.fiap.solovivo.iot.mqtt.MqttMensagemParser.EventoIrrigacao;
import br.com.fiap.solovivo.iot.mqtt.MqttMensagemParser.Topico;
import br.com.fiap.solovivo.service.DispositivoEventosService;
import jakarta.annotation.PreDestroy;

/**
 * Recebe o que o ESP32 publica e entrega aos services:
 * <ul>
 *   <li>/telemetria -> leituras (solo, temperatura, umidade do ar, luminosidade), alertas e estado da bomba;</li>
 *   <li>/irrigacao  -> grava inicio/fim das irrigacoes decididas pelo ESP32 e confirma comandos pendentes;</li>
 *   <li>/status     -> dispositivo online/offline;</li>
 *   <li>/config/ack -> confirmacao (ou recusa) dos limites enviados.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "solovivo.mqtt.habilitado", havingValue = "true")
public class MqttListener implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttListener.class);

    private final MqttClient client;
    private final MqttConnectOptions options;
    private final MqttTopicos topicos;
    private final ExecutorService executor;
    private final DispositivoEventosService eventosService;
    private final ConfirmacoesComando confirmacoes;
    private final MqttConfigPublisher configPublisher;

    public MqttListener(MqttClient client, MqttConnectOptions options, MqttTopicos topicos,
                        ExecutorService mqttExecutor, DispositivoEventosService eventosService,
                        ConfirmacoesComando confirmacoes, MqttConfigPublisher configPublisher) {
        this.client = client;
        this.options = options;
        this.topicos = topicos;
        this.executor = mqttExecutor;
        this.eventosService = eventosService;
        this.confirmacoes = confirmacoes;
        this.configPublisher = configPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void conectar() {
        client.setCallback(this);
        // A reconexao automatica do Paho so vale depois da 1a conexao: ate la, tentamos de novo com espera crescente.
        executor.submit(() -> {
            long esperaMs = 5_000;
            while (!Thread.currentThread().isInterrupted() && !client.isConnected()) {
                try {
                    client.connect(options);
                } catch (MqttException e) {
                    log.error("[MQTT] Nao foi possivel conectar ao broker {}: {} (a API segue funcionando via HTTP; "
                            + "nova tentativa em {} s)", client.getServerURI(), e.getMessage(), esperaMs / 1000);
                    try {
                        Thread.sleep(esperaMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    esperaMs = Math.min(esperaMs * 2, 60_000);
                }
            }
        });
    }

    /** Chamado na primeira conexao e a cada reconexao. */
    @Override
    public void connectComplete(boolean reconexao, String serverURI) {
        executor.submit(() -> {
            try {
                for (String filtro : topicos.assinaturas()) {
                    client.subscribe(filtro, 1);
                }
                log.info("[MQTT] {} {} | assinando {}", reconexao ? "Reconectado a" : "Conectado a", serverURI,
                        topicos.assinaturas());
                configPublisher.publicarTodas(); // ESP32 recebe os limites atuais do banco (retain)
            } catch (MqttException e) {
                log.error("[MQTT] Falha ao assinar os topicos: {}", e.getMessage());
            }
        });
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            Topico t = MqttMensagemParser.topico(topicos.base(), topic);
            byte[] payload = message.getPayload();
            switch (t.tipo()) {
                case TELEMETRIA -> eventosService.processarTelemetria(t.dispositivo(),
                        MqttMensagemParser.telemetria(payload));
                case IRRIGACAO -> {
                    EventoIrrigacao evento = MqttMensagemParser.eventoIrrigacao(payload);
                    eventosService.processarEventoIrrigacao(t.dispositivo(), evento);
                    confirmacoes.confirmar(t.dispositivo(), evento.evento()); // apos o commit
                }
                case STATUS -> eventosService.processarStatus(t.dispositivo(), MqttMensagemParser.status(payload));
                case CONFIG_ACK -> {
                    ConfigAck ack = MqttMensagemParser.configAck(payload);
                    if (Boolean.TRUE.equals(ack.ok())) {
                        log.info("[MQTT] {} aplicou a configuracao", t.dispositivo());
                    } else {
                        log.warn("[MQTT] {} RECUSOU a configuracao: {}", t.dispositivo(), ack.erro());
                    }
                }
            }
        } catch (RuntimeException e) {
            // Nunca derrubar o callback do Paho: apenas registrar a mensagem rejeitada.
            log.warn("[MQTT] Mensagem ignorada em {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[MQTT] Conexao perdida ({}); reconectando automaticamente",
                cause == null ? "motivo desconhecido" : cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // sem acao
    }

    @PreDestroy
    public void encerrar() {
        try {
            if (client.isConnected()) {
                client.disconnect(2000);
            }
            client.close();
        } catch (MqttException e) {
            log.debug("[MQTT] Erro ao encerrar o cliente: {}", e.getMessage());
        }
    }
}
