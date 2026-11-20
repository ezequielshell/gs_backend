package br.com.fiap.solovivo.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("API | Exclusão, alertas, perfil, histórico, culturas e documentação")
class RecursosApiTest extends IntegracaoBase {

    @Test
    @DisplayName("DELETE /cultivos/{id} -> 204; depois 404 e some da lista")
    void excluirCultivo() throws Exception {
        String auth = bearerMaria();
        long id = idCultivo("Canteiro 3");
        mvc.perform(delete("/api/cultivos/" + id).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/cultivos/" + id).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cultivo não encontrado."));
        mvc.perform(get("/api/cultivos").header("Authorization", auth))
                .andExpect(jsonPath("$", hasSize(2)));
        // historico e preservado (exclusao logica) e os alertas do cultivo sao resolvidos
        assertThat(contar("SELECT COUNT(*) FROM cultivos WHERE id = ?", id)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM alertas WHERE cultivo_id = ? AND resolvido_em IS NULL", id)).isZero();
    }

    @Test
    @DisplayName("DELETE com bomba ligada desliga a bomba antes (segurança)")
    void excluirComBombaLigada() throws Exception {
        String auth = bearerMaria();
        long id = idCultivo("Canteiro 2");
        mvc.perform(post("/api/cultivos/" + id + "/irrigacao/acionar").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.irrigacaoAtiva").value(true));
        mvc.perform(delete("/api/cultivos/" + id).header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(contar("SELECT COUNT(*) FROM irrigacoes WHERE cultivo_id = ? AND finalizado_em IS NULL", id)).isZero();
    }

    @Test
    @DisplayName("Alertas: lista ativos do dump, resolve um e ele passa para 'resolvidos'")
    void alertas() throws Exception {
        String auth = bearerMaria();
        mvc.perform(get("/api/alertas").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].cultivoNome", hasItems("Canteiro 1", "Canteiro 2", "Canteiro 3")))
                .andExpect(jsonPath("$[0].status").value("ativos"));

        long id = contar("SELECT id FROM alertas WHERE tipo = 'sem_leitura_sensor'");
        mvc.perform(put("/api/alertas/" + id + "/resolver").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolvidos"));

        mvc.perform(get("/api/alertas").param("status", "resolvidos").header("Authorization", auth))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("sem_leitura_sensor"));
        mvc.perform(get("/api/alertas").param("status", "qualquer").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/alertas/999999/resolver").header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Perfil: GET no formato do app e PUT salva notificações e propriedade")
    void perfil() throws Exception {
        String auth = bearerMaria();
        mvc.perform(get("/api/perfil").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Souza"))
                .andExpect(jsonPath("$.email").value("maria@email.com"))
                .andExpect(jsonPath("$.propriedade").value("Sítio Boa Esperança"))
                .andExpect(jsonPath("$.notificacoes.soloAbaixoLimite").value(true))
                .andExpect(jsonPath("$.notificacoes.semLeituraSensor").value(true));

        var novo = Map.of("nome", "Maria S. Souza", "email", "maria@email.com", "propriedade", "Sítio Nova Esperança",
                "notificacoes", Map.of("soloAbaixoLimite", false, "semLeituraSensor", true), "tema", "escuro");
        mvc.perform(put("/api/perfil").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(novo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria S. Souza"))
                .andExpect(jsonPath("$.propriedade").value("Sítio Nova Esperança"))
                .andExpect(jsonPath("$.notificacoes.soloAbaixoLimite").value(false))
                .andExpect(jsonPath("$.tema").value("escuro"));

        var emailDoAdmin = Map.of("nome", "Maria", "email", "admin@solovivo.com", "propriedade", "Sítio",
                "notificacoes", Map.of("soloAbaixoLimite", true, "semLeituraSensor", true));
        mvc.perform(put("/api/perfil").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(emailDoAdmin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Histórico 7d do Canteiro 2: 2 irrigações do dump, 21,6 L, origem e duração")
    void historico() throws Exception {
        String auth = bearerMaria();
        long id = idCultivo("Canteiro 2");
        mvc.perform(get("/api/cultivos/" + id + "/historico").param("periodo", "7d").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value("7d"))
                .andExpect(jsonPath("$.irrigacoes").value(2))
                .andExpect(jsonPath("$.consumoEstimadoL").value(21.6)) // 10 min + 8 min a 1,2 L/min
                .andExpect(jsonPath("$.acionamentos", hasSize(2)))
                .andExpect(jsonPath("$.acionamentos[0].origem").value("automatico"))
                .andExpect(jsonPath("$.acionamentos[0].descricaoRegra").value("Regra: solo abaixo de 30%"))
                .andExpect(jsonPath("$.acionamentos[0].duracaoMin").value(10))
                .andExpect(jsonPath("$.acionamentos[1].origem").value("manual"));
        mvc.perform(get("/api/cultivos/" + id + "/historico").param("periodo", "1ano").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Culturas: lista as culturas padrão do dump")
    void culturas() throws Exception {
        mvc.perform(get("/api/culturas").header("Authorization", bearerMaria()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItems("Alface", "Tomate", "Cenoura", "Milho", "Feijão", "Soja")));
    }

    @Test
    @DisplayName("Cultura nova digitada no cadastro do cultivo vira cultura própria do produtor")
    void culturaPropria() throws Exception {
        String auth = bearerMaria();
        mvc.perform(post("/api/cultivos").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Canteiro 9", "cultura", "Rúcula",
                                "umidadeMin", 35, "umidadeMax", 65, "vazaoLMin", 1.0, "tempoMaximoMin", 12))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cultura").value("Rúcula"))
                .andExpect(jsonPath("$.status").value("sem_leitura"))
                .andExpect(jsonPath("$.mensagemStatus").value("Nenhum sensor vinculado"))
                .andExpect(jsonPath("$.consumoEstimadoL").value(12.0));
        mvc.perform(get("/api/culturas").header("Authorization", auth))
                .andExpect(jsonPath("$[*].nome", hasItem("Rúcula")));
    }

    @Test
    @DisplayName("Documentação: Swagger/OpenAPI disponível e exportado para docs/openapi.json")
    void documentacao() throws Exception {
        MvcResult r = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Solo Vivo API"))
                .andExpect(jsonPath("$.paths['/api/cultivos']").exists())
                .andExpect(jsonPath("$.paths['/api/iot/leituras']").exists())
                .andReturn();
        Path destino = Path.of("docs", "openapi.json");
        Files.createDirectories(destino.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(destino.toFile(), corpo(r));
        assertThat(destino).exists();

        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
