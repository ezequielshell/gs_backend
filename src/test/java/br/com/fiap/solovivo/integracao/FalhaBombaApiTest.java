package br.com.fiap.solovivo.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.com.fiap.solovivo.iot.BombaIndisponivelException;
import br.com.fiap.solovivo.iot.BombaSimuladaGateway;
import br.com.fiap.solovivo.iot.ResultadoComando;

/** CT-08: a bomba (controlador) nao confirma o comando. */
@DisplayName("Plano de testes | CT-08 Falha no acionamento da irrigação")
class FalhaBombaApiTest extends IntegracaoBase {

    /** Bomba dos canteiros simulados (com MQTT desligado, todos os canteiros usam esta). */
    @MockitoBean
    private BombaSimuladaGateway bomba;

    @BeforeEach
    void bombaFora() {
        doThrow(new BombaIndisponivelException("timeout de 5 s")).when(bomba).ligar(any());
        when(bomba.desligar(any())).thenReturn(ResultadoComando.local());
    }

    @Test
    @DisplayName("CT-08 | Comando manual sem confirmação -> 503, nada registrado, sem confirmação falsa")
    void ct08ManualSemConfirmacao() throws Exception {
        long id = idCultivo("Canteiro 2");
        long antes = contar("SELECT COUNT(*) FROM irrigacoes");

        mvc.perform(post("/api/cultivos/" + id + "/irrigacao/acionar").header("Authorization", bearerMaria())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modo\":\"manual\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("Não foi possível confirmar o comando")));

        assertThat(contar("SELECT COUNT(*) FROM irrigacoes")).isEqualTo(antes);
        mvc.perform(get("/api/cultivos/" + id).header("Authorization", bearerMaria()))
                .andExpect(jsonPath("$.irrigacaoAtiva").value(false))
                .andExpect(jsonPath("$.modoIrrigacao").value("automatico")); // transacao desfeita
    }

    @Test
    @DisplayName("CT-08 | Automação com bomba fora do ar: leitura e alerta gravados, irrigação não")
    void ct08AutomacaoSemConfirmacao() throws Exception {
        jdbc.update("UPDATE alertas SET resolvido_em = NOW()");
        long antes = contar("SELECT COUNT(*) FROM irrigacoes");

        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor de solo 01\",\"valor\":24}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisoes[0].acao").value("FALHA_ACIONAMENTO"))
                .andExpect(jsonPath("$.decisoes[0].alertasAbertos", hasItem("solo_abaixo_limite")));

        assertThat(contar("SELECT COUNT(*) FROM irrigacoes")).isEqualTo(antes);
        assertThat(contar("SELECT COUNT(*) FROM alertas WHERE resolvido_em IS NULL AND tipo = 'solo_abaixo_limite'"))
                .isEqualTo(1);
    }
}
