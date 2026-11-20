package br.com.fiap.solovivo.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import br.com.fiap.solovivo.service.AlertaService;
import br.com.fiap.solovivo.service.IrrigacaoService;

/**
 * Casos do "Plano de Testes - Solo Vivo" executados automaticamente contra a API real
 * (Spring Boot + PostgreSQL com o dump do projeto).
 */
@DisplayName("Plano de testes | CT-01 a CT-09 (API)")
class PlanoDeTestesApiTest extends IntegracaoBase {

    @Autowired
    private AlertaService alertaService;

    @Autowired
    private IrrigacaoService irrigacaoService;

    // ---------------------------------------------------------------- CT-01

    @Test
    @DisplayName("CT-01 | Login com credenciais válidas retorna token e usuário")
    void ct01LoginValido() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "maria@email.com", "senha", "solovivo123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(org.hamcrest.Matchers.emptyString())))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.user.nome").value("Maria Souza"))
                .andExpect(jsonPath("$.user.email").value("maria@email.com"))
                .andExpect(jsonPath("$.user.perfil").value("produtor"));
    }

    @Test
    @DisplayName("CT-01 (erro) | Senha incorreta retorna 401 sem informar qual campo errou")
    void ct01SenhaErrada() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "maria@email.com", "senha", "senha-errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos."));
    }

    // ---------------------------------------------------------------- CT-02

    @Test
    @DisplayName("CT-02 | Acesso sem token retorna 401 e não expõe dados")
    void ct02SemToken() throws Exception {
        mvc.perform(get("/api/cultivos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Autenticação necessária")))
                .andExpect(jsonPath("$[0]").doesNotExist());
        mvc.perform(get("/api/alertas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/perfil")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CT-02 | Token inválido retorna 401")
    void ct02TokenInvalido() throws Exception {
        mvc.perform(get("/api/cultivos").header("Authorization", "Bearer token.falso.qualquer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Token inválido ou expirado")));
    }

    // ---------------------------------------------------------------- CT-03

    @Test
    @DisplayName("CT-03 | Cadastro do Canteiro 4 (Alface, 30%-60%, 1,2 L/min) é salvo e aparece na lista")
    void ct03CadastroCultivo() throws Exception {
        String auth = bearerMaria();
        var corpo = Map.of("nome", "Canteiro 4", "cultura", "Alface", "sensor", "Sensor do solo 01",
                "umidadeMin", 30, "umidadeMax", 60, "vazaoLMin", 1.2);

        mvc.perform(post("/api/cultivos").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(corpo)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/cultivos/")))
                .andExpect(jsonPath("$.nome").value("Canteiro 4"))
                .andExpect(jsonPath("$.cultura").value("Alface"))
                .andExpect(jsonPath("$.sensor").value("Sensor de solo 01"))
                .andExpect(jsonPath("$.umidadeMin").value(30.0))
                .andExpect(jsonPath("$.umidadeMax").value(60.0))
                .andExpect(jsonPath("$.vazaoLMin").value(1.2))
                .andExpect(jsonPath("$.modoIrrigacao").value("automatico"));

        assertThat(contar("SELECT COUNT(*) FROM cultivos WHERE nome = 'Canteiro 4' AND ativo")).isEqualTo(1);
        mvc.perform(get("/api/cultivos").header("Authorization", auth))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].nome", hasItem("Canteiro 4")));
    }

    @Test
    @DisplayName("CT-03 (erro) | Mínima >= máxima, vazão zero e nome duplicado são recusados")
    void ct03CadastroInvalido() throws Exception {
        String auth = bearerMaria();
        mvc.perform(post("/api/cultivos").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Canteiro 5", "cultura", "Alface",
                                "umidadeMin", 60, "umidadeMax", 30, "vazaoLMin", 1.2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("mínima deve ser menor")));

        mvc.perform(post("/api/cultivos").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Canteiro 5", "cultura", "Alface",
                                "umidadeMin", 30, "umidadeMax", 60, "vazaoLMin", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.vazaoLMin", notNullValue()));

        mvc.perform(post("/api/cultivos").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "canteiro 2", "cultura", "Alface",
                                "umidadeMin", 30, "umidadeMax", 60, "vazaoLMin", 1.2))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/cultivos").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Canteiro 5", "cultura", "Alface", "sensor", "Sensor inexistente",
                                "umidadeMin", 30, "umidadeMax", 60, "vazaoLMin", 1.2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Sensor de umidade do solo não encontrado")));
    }

    // ---------------------------------------------------------------- CT-04

    @Test
    @DisplayName("CT-04 | Umidade 24% (mínimo 30%) -> status Atenção e alerta registrado")
    void ct04AlertaBaixaUmidade() throws Exception {
        jdbc.update("UPDATE alertas SET resolvido_em = NOW()");
        jdbc.update("UPDATE cultivos SET modo_irrigacao = 'manual' WHERE nome = 'Canteiro 2'");
        String auth = bearerMaria();
        long id = idCultivo("Canteiro 2");

        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor de solo 01\",\"valor\":24}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisoes[0].cultivo").value("Canteiro 2"))
                .andExpect(jsonPath("$.decisoes[0].alertasAbertos", hasItem("solo_abaixo_limite")));

        mvc.perform(get("/api/cultivos/" + id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("atencao"))
                .andExpect(jsonPath("$.umidadeSolo").value(24.0))
                .andExpect(jsonPath("$.mensagemStatus", startsWith("Solo abaixo do limite")));

        mvc.perform(get("/api/alertas").param("status", "ativos").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("solo_abaixo_limite"))
                .andExpect(jsonPath("$[0].cultivoNome").value("Canteiro 2"))
                .andExpect(jsonPath("$[0].severidade").value("atencao"))
                .andExpect(jsonPath("$[0].mensagem").value("Solo abaixo do limite configurado. Confira o cultivo."));
    }

    // ---------------------------------------------------------------- CT-05

    @Test
    @DisplayName("CT-05 | Canteiro 3 sem atualização há 6h -> 'Sem leitura', sem dados antigos como atuais")
    void ct05SemLeitura() throws Exception {
        long id = idCultivo("Canteiro 3");
        mvc.perform(get("/api/cultivos/" + id).header("Authorization", bearerMaria()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sem_leitura"))
                .andExpect(jsonPath("$.umidadeSolo").value(nullValue()))
                .andExpect(jsonPath("$.temperatura").value(nullValue()))
                .andExpect(jsonPath("$.umidadeAr").value(nullValue()))
                .andExpect(jsonPath("$.mensagemStatus").value("Última leitura há 6h"))
                .andExpect(jsonPath("$.ultimaLeituraEm", notNullValue()));
    }

    @Test
    @DisplayName("CT-05 | Rotina de monitoramento abre alerta crítico 'sem leitura do sensor'")
    void ct05AlertaSemLeitura() throws Exception {
        jdbc.update("UPDATE alertas SET resolvido_em = NOW()");
        assertThat(alertaService.verificarSensoresSemLeitura()).isEqualTo(1);
        assertThat(alertaService.verificarSensoresSemLeitura()).isZero(); // nao duplica

        mvc.perform(get("/api/alertas").header("Authorization", bearerMaria()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("sem_leitura_sensor"))
                .andExpect(jsonPath("$[0].cultivoNome").value("Canteiro 3"))
                .andExpect(jsonPath("$[0].severidade").value("critico"));
    }

    // ---------------------------------------------------------------- CT-06

    @Test
    @DisplayName("CT-06 | API recebe solo 24%, temperatura 31 °C e umidade do ar 62% e o app recebe os valores")
    void ct06IntegracaoSensores() throws Exception {
        long antes = contar("SELECT COUNT(*) FROM leituras");
        enviarLeituras("""
                {"leituras":[{"sensor":"Sensor de solo 01","valor":24},
                             {"tipo":"temperatura_ar","valor":31},
                             {"tipo":"umidade_ar","valor":62}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dispositivo").value("ESP32 Horta"))
                .andExpect(jsonPath("$.recebidas").value(3));
        assertThat(contar("SELECT COUNT(*) FROM leituras")).isEqualTo(antes + 3);

        // Valores diferentes dos do dump, para provar que o app enxerga o que a API recebeu
        enviarLeituras("""
                {"leituras":[{"sensor":"Sensor de solo 01","valor":45.5},
                             {"tipo":"temperatura_ar","valor":28},
                             {"tipo":"umidade_ar","valor":55}]}""")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/cultivos/" + idCultivo("Canteiro 2")).header("Authorization", bearerMaria()))
                .andExpect(jsonPath("$.umidadeSolo").value(45.5))
                .andExpect(jsonPath("$.temperatura").value(28.0))
                .andExpect(jsonPath("$.umidadeAr").value(55.0))
                .andExpect(jsonPath("$.status").value("adequado"))
                .andExpect(jsonPath("$.historico24h", not(hasSize(0))));
    }

    @Test
    @DisplayName("CT-06 (erro) | Token de dispositivo inválido/ausente e leituras fora da faixa são recusados")
    void ct06LeiturasInvalidas() throws Exception {
        long antes = contar("SELECT COUNT(*) FROM leituras");
        String corpo = "{\"leituras\":[{\"sensor\":\"Sensor de solo 01\",\"valor\":40}]}";

        mvc.perform(post("/api/iot/leituras").header("X-Device-Token", "token-falso")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token do dispositivo inválido."));
        mvc.perform(post("/api/iot/leituras").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());

        // Pacote com uma leitura valida e outra impossivel (150%): nada e gravado
        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor de solo 01\",\"valor\":40},"
                + "{\"sensor\":\"Sensor de solo 01\",\"valor\":150}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("fora da faixa")));
        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor que nao existe\",\"valor\":40}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("sensor não encontrado")));
        enviarLeituras("{\"leituras\":[]}").andExpect(status().isBadRequest());

        assertThat(contar("SELECT COUNT(*) FROM leituras")).isEqualTo(antes);
    }

    // ---------------------------------------------------------------- CT-07

    @Test
    @DisplayName("CT-07 | Irrigação manual de 10 min a 1,2 L/min: consumo 12 L, comando confirmado e registrado")
    void ct07IrrigacaoManual() throws Exception {
        String auth = bearerMaria();
        long id = idCultivo("Canteiro 2");
        long maria = contar("SELECT id FROM usuarios WHERE email = 'maria@email.com'");

        mvc.perform(put("/api/cultivos/" + id + "/limites").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("umidadeMin", 30, "umidadeMax", 60, "tempoMaximoMin", 10,
                                "vazaoLMin", 1.2, "irrigacaoAutomaticaAtivada", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tempoMaximoMin").value(10))
                .andExpect(jsonPath("$.consumoEstimadoL").value(12.0));

        mvc.perform(post("/api/cultivos/" + id + "/irrigacao/acionar").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modo\":\"manual\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.irrigacaoAtiva").value(true))
                .andExpect(jsonPath("$.modoIrrigacao").value("manual"));

        assertThat(jdbc.queryForMap("""
                SELECT origem::text AS origem, usuario_id, duracao_prevista_min, vazao_litros_min
                  FROM irrigacoes WHERE cultivo_id = ? AND finalizado_em IS NULL""", id))
                .containsEntry("origem", "manual")
                .containsEntry("usuario_id", maria)
                .containsEntry("duracao_prevista_min", 10);

        mvc.perform(get("/api/cultivos/" + id + "/historico").param("periodo", "hoje").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acionamentos[0].origem").value("manual"))
                .andExpect(jsonPath("$.acionamentos[0].emAndamento").value(true))
                .andExpect(jsonPath("$.acionamentos[0].descricaoRegra").value("Ação confirmada pelo usuário"));

        // Segundo acionamento desliga a bomba e fecha o registro
        mvc.perform(post("/api/cultivos/" + id + "/irrigacao/acionar").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modo\":\"manual\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.irrigacaoAtiva").value(false));
        assertThat(contar("SELECT COUNT(*) FROM irrigacoes WHERE cultivo_id = ? AND finalizado_em IS NULL", id)).isZero();
    }

    @Test
    @DisplayName("Troca de modo (apenasAlterarModo) não liga a bomba")
    void trocaDeModo() throws Exception {
        long id = idCultivo("Canteiro 2");
        mvc.perform(post("/api/cultivos/" + id + "/irrigacao/acionar").header("Authorization", bearerMaria())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modo\":\"manual\",\"apenasAlterarModo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoIrrigacao").value("manual"))
                .andExpect(jsonPath("$.irrigacaoAtiva").value(false));
    }

    // ---------------------------------------------------------------- CT-09

    @Test
    @DisplayName("CT-09 | Solo 24% com automação ativa -> liga automaticamente e registra a decisão; 61% -> desliga")
    void ct09IrrigacaoAutomatica() throws Exception {
        long id = idCultivo("Canteiro 2");

        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor de solo 01\",\"valor\":24}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisoes[0].acao").value("LIGAR_BOMBA"))
                .andExpect(jsonPath("$.decisoes[0].descricao").value("Regra: solo abaixo de 30%"));

        assertThat(jdbc.queryForMap("""
                SELECT origem::text AS origem, motivo, umidade_inicial::float8 AS umidade_inicial
                  FROM irrigacoes WHERE cultivo_id = ? AND finalizado_em IS NULL""", id))
                .containsEntry("origem", "automatica")
                .containsEntry("motivo", "Regra: solo abaixo de 30%")
                .containsEntry("umidade_inicial", 24.0);
        mvc.perform(get("/api/cultivos/" + id).header("Authorization", bearerMaria()))
                .andExpect(jsonPath("$.irrigacaoAtiva").value(true));

        // Segurança: ao passar do máximo (60%) a bomba é desligada
        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor de solo 01\",\"valor\":61}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisoes[0].acao").value("DESLIGAR_BOMBA"))
                .andExpect(jsonPath("$.decisoes[0].alertasAbertos", hasItem("solo_acima_limite")));
        assertThat(contar("SELECT COUNT(*) FROM irrigacoes WHERE cultivo_id = ? AND finalizado_em IS NULL", id)).isZero();

        // Segurança: novo ciclo só após o intervalo mínimo
        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor de solo 01\",\"valor\":24}]}")
                .andExpect(jsonPath("$.decisoes[0].acao").value("NENHUMA"))
                .andExpect(jsonPath("$.decisoes[0].descricao", containsString("intervalo mínimo")));
    }

    @Test
    @DisplayName("CT-09 | Irrigação automática pausada não liga a bomba e gera alerta")
    void ct09Pausada() throws Exception {
        jdbc.update("UPDATE alertas SET resolvido_em = NOW()");
        long id = idCultivo("Canteiro 1");
        enviarLeituras("{\"leituras\":[{\"sensor\":\"Sensor de solo 02\",\"valor\":20}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisoes[0].cultivo").value("Canteiro 1"))
                .andExpect(jsonPath("$.decisoes[0].acao").value("NENHUMA"))
                .andExpect(jsonPath("$.decisoes[0].descricao", containsString("pausada")))
                .andExpect(jsonPath("$.decisoes[0].alertasAbertos", hasItem("irrigacao_pausada")));
        assertThat(contar("SELECT COUNT(*) FROM irrigacoes WHERE cultivo_id = ?", id)).isZero();
    }

    @Test
    @DisplayName("CT-09 | Segurança: irrigação é encerrada ao passar do tempo máximo de acionamento")
    void ct09TempoMaximo() throws Exception {
        long id = idCultivo("Canteiro 2");
        mvc.perform(post("/api/cultivos/" + id + "/irrigacao/acionar").header("Authorization", bearerMaria())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.irrigacaoAtiva").value(true));
        jdbc.update("UPDATE irrigacoes SET iniciado_em = NOW() - INTERVAL '20 minutes' WHERE finalizado_em IS NULL");

        assertThat(irrigacaoService.encerrarIrrigacoesExcedidas()).isEqualTo(1);
        mvc.perform(get("/api/cultivos/" + id).header("Authorization", bearerMaria()))
                .andExpect(jsonPath("$.irrigacaoAtiva").value(false));
        assertThat(contar("SELECT COUNT(*) FROM irrigacoes WHERE cultivo_id = ? AND volume_litros > 0", id))
                .isGreaterThan(0);
    }
}
