package br.com.fiap.solovivo.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.fiap.solovivo.service.Formatador;

@DisplayName("Unidade | Textos de data e hora exibidos no app")
class FormatadorTest {

    // 20/11/2026 17:00 UTC = 14:00 em Sao Paulo (UTC-3)
    private static final Instant AGORA = Instant.parse("2026-11-20T17:00:00Z");
    private final Formatador formatador =
            new Formatador(ZoneId.of("America/Sao_Paulo"), new Fixtures.RelogioAjustavel(AGORA));

    private static OffsetDateTime utc(String iso) {
        return OffsetDateTime.parse(iso).withOffsetSameInstant(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Hora no fuso do produtor: 17:32 UTC -> 14h32")
    void hora() {
        assertThat(formatador.hora(utc("2026-11-20T17:32:00Z"))).isEqualTo("14h32");
    }

    @Test
    @DisplayName("Hoje / Ontem / dd/MM")
    void dataRelativa() {
        assertThat(formatador.dataHoraRelativa(utc("2026-11-20T16:10:00Z"))).isEqualTo("Hoje, 13h10");
        assertThat(formatador.dataHoraRelativa(utc("2026-11-19T21:40:00Z"))).isEqualTo("Ontem, 18h40");
        assertThat(formatador.dataHoraRelativa(utc("2026-11-16T17:05:00Z"))).isEqualTo("16/11, 14h05");
    }

    @Test
    @DisplayName("Horário do alerta omite 'Hoje'")
    void horarioAlerta() {
        assertThat(formatador.horarioAlerta(utc("2026-11-20T17:32:00Z"))).isEqualTo("14h32");
        assertThat(formatador.horarioAlerta(utc("2026-11-19T19:20:00Z"))).isEqualTo("Ontem, 16h20");
    }

    @Test
    @DisplayName("CT-05 | 'Última leitura há 6h'")
    void tempoDesde() {
        assertThat(formatador.tempoDesde(utc("2026-11-20T11:00:00Z"))).isEqualTo("há 6h");
        assertThat(formatador.tempoDesde(utc("2026-11-20T16:35:00Z"))).isEqualTo("há 25 min");
        assertThat(formatador.tempoDesde(utc("2026-11-18T17:00:00Z"))).isEqualTo("há 2 dias");
    }

    @Test
    @DisplayName("Duração dos cards do histórico")
    void duracao() {
        assertThat(Formatador.duracao(6300)).isEqualTo("1 h 45 min");
        assertThat(Formatador.duracao(1500)).isEqualTo("25 min");
        assertThat(Formatador.duracao(7200)).isEqualTo("2 h");
        assertThat(Formatador.duracao(0)).isEqualTo("0 min");
    }

    @Test
    @DisplayName("Início do dia em São Paulo")
    void inicioDoDia() {
        assertThat(formatador.inicioDoDia().toInstant()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
    }
}
