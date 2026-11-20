package br.com.fiap.solovivo.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.fiap.solovivo.dto.CultivoDtos.CultivoResponse;
import br.com.fiap.solovivo.dto.CultivoDtos.MoisturePoint;
import br.com.fiap.solovivo.model.Leitura;
import br.com.fiap.solovivo.repository.ConsultaRepository.CultivoStatusRow;
import br.com.fiap.solovivo.repository.LeituraRepository;

/** Monta o JSON de Cultivo no formato do app a partir da view v_cultivos_status. */
@Component
public class CultivoMapper {

    private final LeituraRepository leituraRepository;
    private final Formatador formatador;
    private final Clock clock;

    public CultivoMapper(LeituraRepository leituraRepository, Formatador formatador, Clock clock) {
        this.leituraRepository = leituraRepository;
        this.formatador = formatador;
        this.clock = clock;
    }

    public CultivoResponse toResponse(CultivoStatusRow r) {
        boolean semLeitura = "sem_leitura".equals(r.status());
        String atualizadoEm = r.ultimaLeituraEm() == null ? "Sem leitura" : formatador.hora(r.ultimaLeituraEm());

        String mensagem;
        if (r.sensorId() == null) {
            mensagem = "Nenhum sensor vinculado";
        } else if (semLeitura) {
            // CT-05: informa a ultima atualizacao e nao apresenta dado antigo como atual.
            mensagem = r.ultimaLeituraEm() == null
                    ? "Sem leitura do sensor"
                    : "Última leitura " + formatador.tempoDesde(r.ultimaLeituraEm());
        } else {
            mensagem = r.mensagemStatus() + " • " + atualizadoEm;
        }

        return new CultivoResponse(
                String.valueOf(r.cultivoId()),
                r.canteiro(),
                r.cultura(),
                r.sensor(),
                semLeitura ? null : r.umidadeSolo(),
                r.umidadeMin(),
                r.umidadeMax(),
                semLeitura ? null : r.temperaturaAr(),
                semLeitura ? null : r.umidadeAr(),
                r.status(),
                mensagem,
                atualizadoEm,
                r.ultimaLeituraEm() == null ? null : r.ultimaLeituraEm().toString(),
                r.modoIrrigacao(),
                r.bombaLigada(),
                r.irrigacaoAutomaticaAtiva(),
                r.irrigacaoPausadaMotivo(),
                r.tempoMaxAcionamentoMin(),
                r.vazaoBombaLMin(),
                RegraIrrigacao.consumoEstimadoLitros(r.vazaoBombaLMin(), r.tempoMaxAcionamentoMin()),
                historico24h(r.sensorId()));
    }

    /** Media das leituras do sensor de solo em blocos de 2 horas nas ultimas 24h (grafico da Tela 5). */
    private List<MoisturePoint> historico24h(Long sensorId) {
        if (sensorId == null) {
            return List.of();
        }
        OffsetDateTime desde = OffsetDateTime.now(clock).minusHours(24);
        Map<ZonedDateTime, double[]> blocos = new LinkedHashMap<>();
        for (Leitura l : leituraRepository.findBySensorIdAndLidaEmGreaterThanEqualOrderByLidaEmAsc(sensorId, desde)) {
            ZonedDateTime local = l.getLidaEm().atZoneSameInstant(formatador.zona());
            ZonedDateTime inicioBloco = local.truncatedTo(ChronoUnit.HOURS).withHour(local.getHour() - local.getHour() % 2);
            double[] soma = blocos.computeIfAbsent(inicioBloco, k -> new double[2]);
            soma[0] += l.getValor();
            soma[1]++;
        }
        List<MoisturePoint> pontos = new ArrayList<>();
        blocos.forEach((inicio, soma) -> pontos.add(new MoisturePoint(
                String.format("%02dh", inicio.getHour()), Math.round(soma[0] / soma[1] * 10.0) / 10.0)));
        return pontos;
    }
}
