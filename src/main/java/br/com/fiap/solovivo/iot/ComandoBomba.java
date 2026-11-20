package br.com.fiap.solovivo.iot;

/**
 * Comando para a bomba de um cultivo.
 *
 * @param dispositivo               identificador do ESP32 (dispositivos.identificador, ex.: esp32-horta-01)
 * @param cultivoId                 cultivo irrigado
 * @param duracaoMaxMin             tempo maximo de acionamento (o firmware desliga sozinho apos esse tempo)
 * @param usuarioId                 quem pediu (manual) ou null (automatico)
 * @param controladoPorDispositivo  true = o rele deste cultivo e o do ESP32 real (comando vai por MQTT)
 */
public record ComandoBomba(String dispositivo, Long cultivoId, Integer duracaoMaxMin, Long usuarioId,
                           boolean controladoPorDispositivo) {
}
