package br.com.fiap.solovivo.iot.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.solovivo.iot.TopologiaIot;
import br.com.fiap.solovivo.model.Cultivo;
import br.com.fiap.solovivo.model.Dispositivo;
import br.com.fiap.solovivo.model.ModoIrrigacao;
import br.com.fiap.solovivo.repository.CultivoRepository;
import br.com.fiap.solovivo.repository.DispositivoRepository;
import br.com.fiap.solovivo.service.ConfiguracaoCultivoAlterada;

/**
 * Mantem a configuracao do ESP32 igual a do banco: publica em {base}/{dispositivo}/config (retain),
 * no formato lido por aplicarConfig() do firmware, quando o app altera limites/modo e quando a API conecta.
 */
@Component
@ConditionalOnProperty(name = "solovivo.mqtt.habilitado", havingValue = "true")
public class MqttConfigPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttConfigPublisher.class);

    private final MqttClient client;
    private final MqttTopicos topicos;
    private final TopologiaIot topologia;
    private final CultivoRepository cultivoRepository;
    private final DispositivoRepository dispositivoRepository;
    private final ObjectMapper objectMapper;

    public MqttConfigPublisher(MqttClient client, MqttTopicos topicos, TopologiaIot topologia,
                               CultivoRepository cultivoRepository, DispositivoRepository dispositivoRepository,
                               ObjectMapper objectMapper) {
        this.client = client;
        this.topicos = topicos;
        this.topologia = topologia;
        this.cultivoRepository = cultivoRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.objectMapper = objectMapper;
    }

    /** Disparado pelos services apos o commit (limites ou modo alterados). */
    @TransactionalEventListener
    public void aoAlterar(ConfiguracaoCultivoAlterada evento) {
        cultivoRepository.findByIdAndAtivoTrue(evento.cultivoId())
                .filter(topologia::controladoPorDispositivo)
                .ifPresent(this::publicar);
    }

    /** Ao conectar: envia a configuracao do cultivo controlado de cada dispositivo. */
    public void publicarTodas() {
        for (Dispositivo d : dispositivoRepository.findAll()) {
            topologia.cultivoControlado(d.getId()).ifPresent(this::publicar);
        }
    }

    public void publicar(Cultivo c) {
        Dispositivo dispositivo = dispositivoRepository.findById(c.getDispositivoBombaId()).orElse(null);
        if (dispositivo == null) {
            return;
        }
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("umidade_min", c.getUmidadeMin());
        cfg.put("umidade_max", c.getUmidadeMax());
        cfg.put("tempo_max_min", c.getTempoMaxAcionamentoMin());
        cfg.put("vazao_l_min", c.getVazaoBombaLMin());
        cfg.put("modo", c.getModoIrrigacao() == ModoIrrigacao.automatico ? "automatico" : "manual");
        cfg.put("automatica_ativa", c.isIrrigacaoAutomaticaAtiva());
        try {
            MqttMessage msg = new MqttMessage(objectMapper.writeValueAsString(cfg).getBytes(StandardCharsets.UTF_8));
            msg.setQos(1);
            msg.setRetained(true); // o ESP32 recebe a ultima configuracao mesmo se estiver offline agora
            String topico = topicos.config(dispositivo.getIdentificador());
            client.publish(topico, msg);
            log.info("[MQTT] Configuracao de {} publicada em {}: {}", c.getNome(), topico, cfg);
        } catch (MqttException | JsonProcessingException e) {
            log.warn("[MQTT] Nao foi possivel publicar a configuracao de {}: {}", c.getNome(), e.getMessage());
        }
    }
}
