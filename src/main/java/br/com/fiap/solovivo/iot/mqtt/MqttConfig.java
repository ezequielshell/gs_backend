package br.com.fiap.solovivo.iot.mqtt;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.fiap.solovivo.config.SoloVivoProperties;

/**
 * Integracao MQTT com o ESP32 (ligada com MQTT_ENABLED=true).
 * Mesmo broker e topicos do firmware (solo-vivo-arduino/sketch.ino) e do Node-RED (flows_solovivo.json).
 */
@Configuration
@ConditionalOnProperty(name = "solovivo.mqtt.habilitado", havingValue = "true")
public class MqttConfig {

    @Bean
    public MqttClient mqttClient(SoloVivoProperties properties) throws MqttException {
        return new MqttClient(properties.mqtt().brokerUrl(), properties.mqtt().clientId(), new MemoryPersistence());
    }

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        return options;
    }

    @Bean
    public MqttTopicos mqttTopicos(SoloVivoProperties properties) {
        return new MqttTopicos(properties.mqtt().topicoBase());
    }

    /** Conectar/assinar/publicar fora das threads de callback do Paho (evita travamentos). */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService mqttExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mqtt-tarefas");
            t.setDaemon(true);
            return t;
        });
    }
}
