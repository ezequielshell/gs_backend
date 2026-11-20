package br.com.fiap.solovivo.iot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.fiap.solovivo.config.SoloVivoProperties;

/**
 * Bomba simulada: o comando e "confirmado" imediatamente e registrado no log. Usada pelos canteiros
 * que nao estao ligados ao ESP32 real (ou por todos, com MQTT desligado).
 * Com solovivo.bomba-simulada.falhar=true ela recusa os comandos, para demonstrar o CT-08.
 */
@Component
public class BombaSimuladaGateway implements BombaGateway {

    private static final Logger log = LoggerFactory.getLogger(BombaSimuladaGateway.class);

    private final boolean falhar;

    public BombaSimuladaGateway(SoloVivoProperties properties) {
        this.falhar = properties.bombaSimulada() != null && properties.bombaSimulada().falhar();
    }

    @Override
    public ResultadoComando ligar(ComandoBomba comando) {
        return enviar("LIGAR", comando);
    }

    @Override
    public ResultadoComando desligar(ComandoBomba comando) {
        return enviar("DESLIGAR", comando);
    }

    private ResultadoComando enviar(String acao, ComandoBomba comando) {
        if (falhar) {
            throw new BombaIndisponivelException("Bomba simulada configurada para falhar (BOMBA_SIMULADA_FALHAR=true).");
        }
        log.info("[Bomba simulada] {} | dispositivo={} cultivo={} duracaoMax={}min",
                acao, comando.dispositivo(), comando.cultivoId(), comando.duracaoMaxMin());
        return ResultadoComando.local();
    }
}
