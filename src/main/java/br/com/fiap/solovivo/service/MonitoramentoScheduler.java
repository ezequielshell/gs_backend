package br.com.fiap.solovivo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina periodica (padrao: a cada 60 s):
 * <ul>
 *   <li>encerra irrigacoes que passaram do tempo maximo de acionamento (seguranca);</li>
 *   <li>abre o alerta "Sem leitura do sensor" para cultivos sem dados recentes (CT-05).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "solovivo.monitoramento.habilitado", havingValue = "true", matchIfMissing = true)
public class MonitoramentoScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoramentoScheduler.class);

    private final IrrigacaoService irrigacaoService;
    private final AlertaService alertaService;

    public MonitoramentoScheduler(IrrigacaoService irrigacaoService, AlertaService alertaService) {
        this.irrigacaoService = irrigacaoService;
        this.alertaService = alertaService;
    }

    @Scheduled(initialDelayString = "15000", fixedDelayString = "${solovivo.monitoramento.intervalo-ms:60000}")
    public void executar() {
        try {
            irrigacaoService.encerrarIrrigacoesExcedidas();
        } catch (RuntimeException e) {
            log.error("Falha ao verificar irrigações em andamento", e);
        }
        try {
            int abertos = alertaService.verificarSensoresSemLeitura();
            if (abertos > 0) {
                log.info("[Monitoramento] {} alerta(s) de sensor sem leitura aberto(s)", abertos);
            }
        } catch (RuntimeException e) {
            log.error("Falha ao verificar sensores sem leitura", e);
        }
    }
}
