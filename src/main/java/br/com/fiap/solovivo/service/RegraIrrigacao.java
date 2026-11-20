package br.com.fiap.solovivo.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import br.com.fiap.solovivo.model.ModoIrrigacao;
import br.com.fiap.solovivo.model.OrigemIrrigacao;
import br.com.fiap.solovivo.model.SeveridadeAlerta;
import br.com.fiap.solovivo.model.TipoAlerta;

/**
 * Motor de regras da irrigacao (logica pura, sem banco - testado em RegraIrrigacaoTest).
 *
 * <p>Para cada leitura de umidade do solo decide: ligar a bomba, desligar ou nao fazer nada,
 * e quais alertas abrir/resolver. Condicoes de seguranca da automacao:
 * <ol>
 *   <li>so liga no modo automatico, com a automacao ativada e sem pausa;</li>
 *   <li>nunca liga se ja ha irrigacao em andamento;</li>
 *   <li>respeita um intervalo minimo entre ciclos;</li>
 *   <li>desliga qualquer irrigacao quando o solo atinge o limite maximo;</li>
 *   <li>(no IrrigacaoService) desliga quando passa do tempo maximo de acionamento.</li>
 * </ol>
 */
public final class RegraIrrigacao {

    public static final String ACAO_SUGERIDA = "Ação: conferir o cultivo e a leitura do sensor.";

    public enum Acao { LIGAR_BOMBA, DESLIGAR_BOMBA, NENHUMA }

    public record AlertaPlanejado(TipoAlerta tipo, SeveridadeAlerta severidade, String mensagem) {
    }

    public record Contexto(
            double umidade,
            double umidadeMin,
            double umidadeMax,
            ModoIrrigacao modo,
            boolean automaticaAtiva,
            String pausadaMotivo,
            boolean bombaLigada,
            OrigemIrrigacao origemEmAndamento,
            OffsetDateTime ultimaIrrigacaoFinalizadaEm,
            OffsetDateTime agora,
            int intervaloMinimoMin) {
    }

    public record Decisao(Acao acao, String descricao, List<AlertaPlanejado> abrir, Set<TipoAlerta> resolver) {
    }

    private RegraIrrigacao() {
    }

    public static Decisao avaliar(Contexto c) {
        List<AlertaPlanejado> abrir = new ArrayList<>();
        Set<TipoAlerta> resolver = EnumSet.of(TipoAlerta.sem_leitura_sensor); // chegou leitura valida
        boolean pausada = c.pausadaMotivo() != null && !c.pausadaMotivo().isBlank();
        if (!pausada) {
            resolver.add(TipoAlerta.irrigacao_pausada);
        }

        // Seguranca: solo no limite maximo com bomba ligada -> desliga (manual ou automatica).
        if (c.bombaLigada() && c.umidade() >= c.umidadeMax()) {
            resolver.add(TipoAlerta.solo_abaixo_limite);
            if (c.umidade() > c.umidadeMax()) {
                abrir.add(soloAcima());
            } else {
                resolver.add(TipoAlerta.solo_acima_limite);
            }
            return new Decisao(Acao.DESLIGAR_BOMBA, "Regra de segurança: solo atingiu " + pct(c.umidade())
                    + "% (limite máximo " + pct(c.umidadeMax()) + "%)", abrir, resolver);
        }

        if (c.umidade() > c.umidadeMax()) {
            abrir.add(soloAcima());
            resolver.add(TipoAlerta.solo_abaixo_limite);
            return new Decisao(Acao.NENHUMA, "Solo acima do limite máximo (" + pct(c.umidadeMax()) + "%)",
                    abrir, resolver);
        }

        if (c.umidade() >= c.umidadeMin()) {
            resolver.add(TipoAlerta.solo_abaixo_limite);
            resolver.add(TipoAlerta.solo_acima_limite);
            return new Decisao(Acao.NENHUMA, "Umidade dentro da faixa (" + pct(c.umidadeMin()) + "% a "
                    + pct(c.umidadeMax()) + "%)", abrir, resolver);
        }

        // Abaixo do minimo
        abrir.add(new AlertaPlanejado(TipoAlerta.solo_abaixo_limite, SeveridadeAlerta.atencao,
                "Solo abaixo do limite configurado. Confira o cultivo."));
        resolver.add(TipoAlerta.solo_acima_limite);

        if (c.modo() == ModoIrrigacao.manual) {
            return nenhuma("Modo manual: a irrigação depende de confirmação do produtor", abrir, resolver);
        }
        if (!c.automaticaAtiva()) {
            return nenhuma("Irrigação automática desativada nos limites do cultivo", abrir, resolver);
        }
        if (pausada) {
            abrir.add(new AlertaPlanejado(TipoAlerta.irrigacao_pausada, SeveridadeAlerta.atencao,
                    "Irrigação automática pausada. Veja o motivo."));
            return nenhuma("Irrigação automática pausada: " + c.pausadaMotivo(), abrir, resolver);
        }
        if (c.bombaLigada()) {
            return nenhuma("Irrigação já em andamento", abrir, resolver);
        }
        if (c.ultimaIrrigacaoFinalizadaEm() != null
                && c.ultimaIrrigacaoFinalizadaEm().plusMinutes(c.intervaloMinimoMin()).isAfter(c.agora())) {
            return nenhuma("Aguardando intervalo mínimo entre ciclos (" + c.intervaloMinimoMin() + " min)",
                    abrir, resolver);
        }
        return new Decisao(Acao.LIGAR_BOMBA, "Regra: solo abaixo de " + pct(c.umidadeMin()) + "%", abrir, resolver);
    }

    public static AlertaPlanejado alertaSemLeitura() {
        return new AlertaPlanejado(TipoAlerta.sem_leitura_sensor, SeveridadeAlerta.critico,
                "Sem leitura do solo. Verifique a conexão do sensor.");
    }

    public static AlertaPlanejado alertaTemperaturaAlta(double temperatura) {
        return new AlertaPlanejado(TipoAlerta.temperatura_alta, SeveridadeAlerta.atencao,
                "Alta temperatura ambiente (" + pct(temperatura) + " °C). Acompanhe a umidade do solo.");
    }

    /** Consumo estimado = vazao (L/min) x tempo (min), arredondado em 1 casa. Ex.: 1,2 x 10 = 12 L. */
    public static double consumoEstimadoLitros(double vazaoLMin, int minutos) {
        return Math.round(vazaoLMin * minutos * 10.0) / 10.0;
    }

    /** 30.0 -> "30"; 30.5 -> "30,5". */
    public static String pct(double valor) {
        return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static Decisao nenhuma(String descricao, List<AlertaPlanejado> abrir, Set<TipoAlerta> resolver) {
        return new Decisao(Acao.NENHUMA, descricao, abrir, resolver);
    }

    private static AlertaPlanejado soloAcima() {
        return new AlertaPlanejado(TipoAlerta.solo_acima_limite, SeveridadeAlerta.atencao,
                "Solo acima do limite configurado. Confira o cultivo.");
    }
}
