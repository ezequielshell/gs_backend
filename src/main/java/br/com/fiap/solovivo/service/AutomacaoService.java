package br.com.fiap.solovivo.service;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.fiap.solovivo.config.SoloVivoProperties;
import br.com.fiap.solovivo.dto.IotDtos.DecisaoResponse;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.iot.BombaIndisponivelException;
import br.com.fiap.solovivo.iot.TopologiaIot;
import br.com.fiap.solovivo.model.Cultivo;
import br.com.fiap.solovivo.model.Irrigacao;
import br.com.fiap.solovivo.model.OrigemIrrigacao;
import br.com.fiap.solovivo.model.Sensor;
import br.com.fiap.solovivo.model.TipoAlerta;
import br.com.fiap.solovivo.repository.IrrigacaoRepository;
import br.com.fiap.solovivo.service.RegraIrrigacao.Contexto;
import br.com.fiap.solovivo.service.RegraIrrigacao.Decisao;

/**
 * Aplica o motor de regras a cada leitura recebida: abre/resolve alertas e liga/desliga a bomba.
 * Cada decisao e registrada no log e devolvida na resposta do endpoint IoT (evidencia do CT-09).
 */
@Service
public class AutomacaoService {

    private static final Logger log = LoggerFactory.getLogger(AutomacaoService.class);

    private final IrrigacaoRepository irrigacaoRepository;
    private final IrrigacaoService irrigacaoService;
    private final AlertaService alertaService;
    private final TopologiaIot topologia;
    private final SoloVivoProperties properties;

    public AutomacaoService(IrrigacaoRepository irrigacaoRepository, IrrigacaoService irrigacaoService,
                            AlertaService alertaService, TopologiaIot topologia, SoloVivoProperties properties) {
        this.irrigacaoRepository = irrigacaoRepository;
        this.irrigacaoService = irrigacaoService;
        this.alertaService = alertaService;
        this.topologia = topologia;
        this.properties = properties;
    }

    public DecisaoResponse aplicarLeituraSolo(Cultivo cultivo, Sensor sensor, double valor, OffsetDateTime agora) {
        Optional<Irrigacao> emAndamento = irrigacaoRepository.findFirstByCultivoIdAndFinalizadoEmIsNull(cultivo.getId());
        Optional<Irrigacao> ultima = irrigacaoRepository
                .findFirstByCultivoIdAndFinalizadoEmIsNotNullOrderByFinalizadoEmDesc(cultivo.getId());

        Decisao decisao = RegraIrrigacao.avaliar(new Contexto(
                valor,
                cultivo.getUmidadeMin(),
                cultivo.getUmidadeMax(),
                cultivo.getModoIrrigacao(),
                cultivo.isIrrigacaoAutomaticaAtiva(),
                cultivo.getIrrigacaoPausadaMotivo(),
                emAndamento.isPresent(),
                emAndamento.map(Irrigacao::getOrigem).orElse(null),
                ultima.map(Irrigacao::getFinalizadoEm).orElse(null),
                agora,
                properties.irrigacao().intervaloMinimoEntreCiclosMin()));

        List<String> abertos = alertaService.abrir(cultivo, sensor.getId(), decisao.abrir(), valor);
        List<String> resolvidos = alertaService.resolver(cultivo.getId(), decisao.resolver());

        String acao = decisao.acao().name();
        String descricao = decisao.descricao();
        if (topologia.controladoPorDispositivo(cultivo)) {
            // O ESP32 decide localmente (funciona ate sem internet) e publica o evento em /irrigacao.
            // A API so gera os alertas, para nao haver dois "cerebros" disputando a mesma bomba.
            acao = "CONTROLE_LOCAL";
            descricao = "Irrigação decidida pelo ESP32 (regra equivalente: " + decisao.descricao() + ")";
        } else {
            try {
                switch (decisao.acao()) {
                    case LIGAR_BOMBA -> irrigacaoService.iniciar(cultivo, OrigemIrrigacao.automatica, null, descricao, valor);
                    case DESLIGAR_BOMBA -> irrigacaoService.finalizar(cultivo, emAndamento.orElseThrow(), valor);
                    case NENHUMA -> { }
                }
            } catch (BombaIndisponivelException | ApiException e) {
                acao = "FALHA_ACIONAMENTO";
                descricao = descricao + " - comando não confirmado pela bomba: " + e.getMessage();
            }
        }

        // Telemetria chega a cada 5 s: so vai para o log INFO o que mudou algo (bomba ou alertas).
        boolean relevante = !abertos.isEmpty() || !resolvidos.isEmpty()
                || (!"NENHUMA".equals(acao) && !"CONTROLE_LOCAL".equals(acao));
        if (relevante) {
            log.info("[Automação] {} | umidade {}% (faixa {}-{}%) | {} | {}", cultivo.getNome(),
                    RegraIrrigacao.pct(valor), RegraIrrigacao.pct(cultivo.getUmidadeMin()),
                    RegraIrrigacao.pct(cultivo.getUmidadeMax()), acao, descricao);
        } else {
            log.debug("[Automação] {} | umidade {}% | {} | {}", cultivo.getNome(), RegraIrrigacao.pct(valor), acao,
                    descricao);
        }

        return new DecisaoResponse(String.valueOf(cultivo.getId()), cultivo.getNome(), sensor.getNome(), valor,
                acao, descricao, abertos, resolvidos);
    }

    /** Temperatura do ar alta -> alerta "temperatura_alta" para os cultivos do dispositivo. */
    public DecisaoResponse aplicarLeituraTemperatura(Cultivo cultivo, Sensor sensor, double valor) {
        double limite = properties.irrigacao().temperaturaAltaC();
        boolean alta = valor > limite;
        List<String> abertos = alta
                ? alertaService.abrir(cultivo, sensor.getId(), List.of(RegraIrrigacao.alertaTemperaturaAlta(valor)), valor)
                : List.of();
        List<String> resolvidos = alta ? List.of() : alertaService.resolver(cultivo.getId(), EnumSet.of(TipoAlerta.temperatura_alta));
        if (abertos.isEmpty() && resolvidos.isEmpty()) {
            return null;
        }
        String descricao = alta
                ? "Temperatura do ar " + RegraIrrigacao.pct(valor) + " °C acima de " + RegraIrrigacao.pct(limite) + " °C"
                : "Temperatura do ar normalizada (" + RegraIrrigacao.pct(valor) + " °C)";
        log.info("[Automação] {} | {}", cultivo.getNome(), descricao);
        return new DecisaoResponse(String.valueOf(cultivo.getId()), cultivo.getNome(), sensor.getNome(), valor,
                "NENHUMA", descricao, abertos, resolvidos);
    }
}
