package br.com.fiap.solovivo.iot;

import java.util.Optional;

import org.springframework.stereotype.Component;

import br.com.fiap.solovivo.config.SoloVivoProperties;
import br.com.fiap.solovivo.model.Cultivo;
import br.com.fiap.solovivo.model.Sensor;
import br.com.fiap.solovivo.model.TipoSensor;
import br.com.fiap.solovivo.repository.CultivoRepository;
import br.com.fiap.solovivo.repository.SensorRepository;

/**
 * Qual canteiro e controlado fisicamente pelo ESP32.
 *
 * <p>O circuito (diagram.json) tem UM sensor de solo (GPIO 34) e UM rele (GPIO 26). O valor
 * "umidade_solo" da telemetria vai para o primeiro sensor de umidade do solo do dispositivo
 * (menor id - no dump, "Sensor de solo 01"), e o rele irriga o cultivo ligado a esse sensor
 * (no dump, "Canteiro 2"). Os outros canteiros continuam simulados (simulador HTTP + bomba simulada).
 */
@Component
public class TopologiaIot {

    private final boolean mqttHabilitado;
    private final SensorRepository sensorRepository;
    private final CultivoRepository cultivoRepository;

    public TopologiaIot(SoloVivoProperties properties, SensorRepository sensorRepository,
                        CultivoRepository cultivoRepository) {
        this.mqttHabilitado = properties.mqtt() != null && properties.mqtt().habilitado();
        this.sensorRepository = sensorRepository;
        this.cultivoRepository = cultivoRepository;
    }

    public Optional<Sensor> sensorSoloPrincipal(Long dispositivoId) {
        return sensorRepository.findFirstByDispositivoIdAndTipoAndAtivoTrueOrderByIdAsc(dispositivoId,
                TipoSensor.umidade_solo);
    }

    public Optional<Cultivo> cultivoControlado(Long dispositivoId) {
        return sensorSoloPrincipal(dispositivoId)
                .flatMap(s -> cultivoRepository.findBySensorUmidadeIdAndAtivoTrue(s.getId()).stream()
                        .filter(c -> dispositivoId.equals(c.getDispositivoBombaId()))
                        .findFirst());
    }

    /** true = com MQTT habilitado, quem decide e aciona a irrigacao deste cultivo e o ESP32. */
    public boolean controladoPorDispositivo(Cultivo cultivo) {
        if (!mqttHabilitado || cultivo.getDispositivoBombaId() == null) {
            return false;
        }
        return cultivoControlado(cultivo.getDispositivoBombaId())
                .map(c -> c.getId().equals(cultivo.getId()))
                .orElse(false);
    }
}
