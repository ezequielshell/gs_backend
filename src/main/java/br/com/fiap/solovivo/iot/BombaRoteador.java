package br.com.fiap.solovivo.iot;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import br.com.fiap.solovivo.iot.mqtt.MqttBombaGateway;

/**
 * Escolhe a bomba de cada comando: o cultivo ligado ao rele do ESP32 (com MQTT habilitado) recebe o
 * comando por MQTT; os demais canteiros usam a bomba simulada.
 */
@Primary
@Component
public class BombaRoteador implements BombaGateway {

    private final BombaSimuladaGateway simulada;
    private final ObjectProvider<MqttBombaGateway> mqtt;

    public BombaRoteador(BombaSimuladaGateway simulada, ObjectProvider<MqttBombaGateway> mqtt) {
        this.simulada = simulada;
        this.mqtt = mqtt;
    }

    @Override
    public ResultadoComando ligar(ComandoBomba comando) {
        return escolher(comando).ligar(comando);
    }

    @Override
    public ResultadoComando desligar(ComandoBomba comando) {
        return escolher(comando).desligar(comando);
    }

    private BombaGateway escolher(ComandoBomba comando) {
        MqttBombaGateway viaMqtt = mqtt.getIfAvailable();
        return comando.controladoPorDispositivo() && viaMqtt != null ? viaMqtt : simulada;
    }
}
