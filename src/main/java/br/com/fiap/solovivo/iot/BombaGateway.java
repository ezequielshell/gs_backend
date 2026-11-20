package br.com.fiap.solovivo.iot;

/**
 * Envia comandos para o controlador de irrigacao (rele da bomba no ESP32) e aguarda a confirmacao.
 *
 * <p>Implementacoes:
 * <ul>
 *   <li>{@link BombaSimuladaGateway}: bomba simulada (canteiros sem hardware).</li>
 *   <li>{@link br.com.fiap.solovivo.iot.mqtt.MqttBombaGateway}: ESP32 real/Wokwi via MQTT (topico /comando),
 *       confirmado pelo evento publicado pelo firmware em /irrigacao.</li>
 *   <li>{@link BombaRoteador} (@Primary): escolhe uma das duas para cada cultivo.</li>
 * </ul>
 * Se o comando nao for confirmado, lanca {@link BombaIndisponivelException} e a API
 * NAO registra a irrigacao (CT-08: nada de confirmacao falsa).
 */
public interface BombaGateway {

    ResultadoComando ligar(ComandoBomba comando);

    ResultadoComando desligar(ComandoBomba comando);
}
