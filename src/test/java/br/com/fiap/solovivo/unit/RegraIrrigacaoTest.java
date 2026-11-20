package br.com.fiap.solovivo.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.fiap.solovivo.model.ModoIrrigacao;
import br.com.fiap.solovivo.model.OrigemIrrigacao;
import br.com.fiap.solovivo.model.TipoAlerta;
import br.com.fiap.solovivo.service.RegraIrrigacao;
import br.com.fiap.solovivo.service.RegraIrrigacao.Acao;
import br.com.fiap.solovivo.service.RegraIrrigacao.AlertaPlanejado;
import br.com.fiap.solovivo.service.RegraIrrigacao.Contexto;
import br.com.fiap.solovivo.service.RegraIrrigacao.Decisao;

@DisplayName("Unidade | Motor de regras da irrigação")
class RegraIrrigacaoTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 20, 14, 0, 0, 0, ZoneOffset.UTC);

    /** Cenario padrao do plano de testes: faixa 30%-60%, modo automatico, automacao ativa. */
    private static Contexto ctx(double umidade) {
        return new Contexto(umidade, 30, 60, ModoIrrigacao.automatico, true, null, false, null, null, AGORA, 30);
    }

    @Test
    @DisplayName("CT-09 | 24% com mínimo 30% e automação ativa -> liga a bomba")
    void abaixoDoMinimoLigaBomba() {
        Decisao d = RegraIrrigacao.avaliar(ctx(24));
        assertThat(d.acao()).isEqualTo(Acao.LIGAR_BOMBA);
        assertThat(d.descricao()).isEqualTo("Regra: solo abaixo de 30%");
        assertThat(d.abrir()).extracting(AlertaPlanejado::tipo).containsExactly(TipoAlerta.solo_abaixo_limite);
    }

    @Test
    @DisplayName("CT-04 | 24% no modo manual -> só alerta, sem ligar a bomba")
    void modoManualApenasAlerta() {
        Contexto c = new Contexto(24, 30, 60, ModoIrrigacao.manual, true, null, false, null, null, AGORA, 30);
        Decisao d = RegraIrrigacao.avaliar(c);
        assertThat(d.acao()).isEqualTo(Acao.NENHUMA);
        assertThat(d.abrir()).extracting(AlertaPlanejado::tipo).containsExactly(TipoAlerta.solo_abaixo_limite);
    }

    @Test
    @DisplayName("Segurança | automação desativada nos limites -> não liga")
    void automacaoDesativada() {
        Contexto c = new Contexto(24, 30, 60, ModoIrrigacao.automatico, false, null, false, null, null, AGORA, 30);
        assertThat(RegraIrrigacao.avaliar(c).acao()).isEqualTo(Acao.NENHUMA);
    }

    @Test
    @DisplayName("Segurança | irrigação pausada -> não liga e abre alerta irrigacao_pausada")
    void pausadaAbreAlerta() {
        Contexto c = new Contexto(24, 30, 60, ModoIrrigacao.automatico, true,
                "Aguardando intervalo mínimo entre ciclos", false, null, null, AGORA, 30);
        Decisao d = RegraIrrigacao.avaliar(c);
        assertThat(d.acao()).isEqualTo(Acao.NENHUMA);
        assertThat(d.abrir()).extracting(AlertaPlanejado::tipo)
                .containsExactly(TipoAlerta.solo_abaixo_limite, TipoAlerta.irrigacao_pausada);
        assertThat(d.resolver()).doesNotContain(TipoAlerta.irrigacao_pausada);
    }

    @Test
    @DisplayName("Segurança | bomba já ligada -> não liga de novo")
    void naoLigaDuasVezes() {
        Contexto c = new Contexto(24, 30, 60, ModoIrrigacao.automatico, true, null, true,
                OrigemIrrigacao.automatica, null, AGORA, 30);
        Decisao d = RegraIrrigacao.avaliar(c);
        assertThat(d.acao()).isEqualTo(Acao.NENHUMA);
        assertThat(d.descricao()).contains("já em andamento");
    }

    @Test
    @DisplayName("Segurança | ciclo terminou há 10 min (mínimo 30) -> aguarda intervalo")
    void respeitaIntervaloEntreCiclos() {
        Contexto c = new Contexto(24, 30, 60, ModoIrrigacao.automatico, true, null, false, null,
                AGORA.minusMinutes(10), AGORA, 30);
        Decisao d = RegraIrrigacao.avaliar(c);
        assertThat(d.acao()).isEqualTo(Acao.NENHUMA);
        assertThat(d.descricao()).contains("intervalo mínimo");
    }

    @Test
    @DisplayName("Segurança | ciclo terminou há 45 min (mínimo 30) -> pode ligar")
    void ligaAposIntervalo() {
        Contexto c = new Contexto(24, 30, 60, ModoIrrigacao.automatico, true, null, false, null,
                AGORA.minusMinutes(45), AGORA, 30);
        assertThat(RegraIrrigacao.avaliar(c).acao()).isEqualTo(Acao.LIGAR_BOMBA);
    }

    @Test
    @DisplayName("Segurança | solo atingiu o máximo com bomba ligada -> desliga")
    void desligaNoMaximo() {
        Contexto c = new Contexto(60, 30, 60, ModoIrrigacao.automatico, true, null, true,
                OrigemIrrigacao.automatica, null, AGORA, 30);
        Decisao d = RegraIrrigacao.avaliar(c);
        assertThat(d.acao()).isEqualTo(Acao.DESLIGAR_BOMBA);
        assertThat(d.resolver()).contains(TipoAlerta.solo_abaixo_limite, TipoAlerta.solo_acima_limite);
    }

    @Test
    @DisplayName("Acima do máximo sem bomba -> alerta solo_acima_limite")
    void acimaDoMaximo() {
        Decisao d = RegraIrrigacao.avaliar(ctx(70));
        assertThat(d.acao()).isEqualTo(Acao.NENHUMA);
        assertThat(d.abrir()).extracting(AlertaPlanejado::tipo).containsExactly(TipoAlerta.solo_acima_limite);
        assertThat(d.resolver()).contains(TipoAlerta.solo_abaixo_limite);
    }

    @Test
    @DisplayName("Dentro da faixa -> resolve alertas de solo e de sem leitura")
    void dentroDaFaixa() {
        Decisao d = RegraIrrigacao.avaliar(ctx(41));
        assertThat(d.acao()).isEqualTo(Acao.NENHUMA);
        assertThat(d.abrir()).isEmpty();
        assertThat(d.resolver()).contains(TipoAlerta.solo_abaixo_limite, TipoAlerta.solo_acima_limite,
                TipoAlerta.sem_leitura_sensor);
    }

    @Test
    @DisplayName("Limite exato: 30% com mínimo 30% está dentro da faixa")
    void limiteMinimoInclusivo() {
        assertThat(RegraIrrigacao.avaliar(ctx(30)).acao()).isEqualTo(Acao.NENHUMA);
    }

    @Test
    @DisplayName("CT-07 | consumo estimado: 10 min x 1,2 L/min = 12 L")
    void consumoEstimado() {
        assertThat(RegraIrrigacao.consumoEstimadoLitros(1.2, 10)).isEqualTo(12.0);
        assertThat(RegraIrrigacao.consumoEstimadoLitros(1.2, 15)).isEqualTo(18.0);
        assertThat(RegraIrrigacao.consumoEstimadoLitros(1.5, 7)).isEqualTo(10.5);
    }

    @Test
    @DisplayName("Formatação de percentual sem casas desnecessárias")
    void percentual() {
        assertThat(RegraIrrigacao.pct(30.0)).isEqualTo("30");
        assertThat(RegraIrrigacao.pct(30.5)).isEqualTo("30,5");
    }
}
