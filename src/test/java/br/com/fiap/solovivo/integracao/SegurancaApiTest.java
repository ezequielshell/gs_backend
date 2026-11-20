package br.com.fiap.solovivo.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Segurança | perfis, dono do recurso e boas práticas (API)")
class SegurancaApiTest extends IntegracaoBase {

    private String cadastrar(String nome, String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", nome, "email", email, "senha", "senhaForte123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return "Bearer " + corpo(r).get("token").asText();
    }

    @Test
    @DisplayName("Perfis: PRODUTOR recebe 403 em rotas de ADMIN; ADMIN acessa")
    void perfisDeAcesso() throws Exception {
        String produtor = bearerMaria();
        String admin = bearerAdmin();

        mvc.perform(get("/api/usuarios").header("Authorization", produtor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", notNullValue()));
        mvc.perform(get("/api/usuarios").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItems("maria@email.com", "admin@solovivo.com")))
                .andExpect(jsonPath("$[*].senhaHash").doesNotExist());

        var cultura = Map.of("nome", "Morango", "umidadeMinPadrao", 40, "umidadeMaxPadrao", 70);
        mvc.perform(post("/api/culturas").header("Authorization", produtor)
                        .contentType(MediaType.APPLICATION_JSON).content(json(cultura)))
                .andExpect(status().isForbidden());
        MvcResult criada = mvc.perform(post("/api/culturas").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(cultura)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.padraoSistema").value(true))
                .andReturn();
        mvc.perform(post("/api/culturas").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(cultura)))
                .andExpect(status().isConflict());

        String idCultura = corpo(criada).get("id").asText();
        mvc.perform(delete("/api/culturas/" + idCultura).header("Authorization", produtor))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/culturas/" + idCultura).header("Authorization", admin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Dono do recurso: outro produtor não vê nem altera os cultivos da Maria (404)")
    void isolamentoEntreProdutores() throws Exception {
        String joao = cadastrar("João Lima", "joao@email.com");
        long canteiro2 = idCultivo("Canteiro 2");

        mvc.perform(get("/api/cultivos").header("Authorization", joao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/cultivos/" + canteiro2).header("Authorization", joao))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/cultivos/" + canteiro2).header("Authorization", joao))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/cultivos/" + canteiro2 + "/irrigacao/acionar").header("Authorization", joao)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/alertas").header("Authorization", joao))
                .andExpect(jsonPath("$", hasSize(0)));
        long alertaDaMaria = contar("SELECT MIN(id) FROM alertas");
        mvc.perform(put("/api/alertas/" + alertaDaMaria + "/resolver").header("Authorization", joao))
                .andExpect(status().isNotFound());

        // ADMIN enxerga os cultivos de todos
        mvc.perform(get("/api/cultivos").header("Authorization", bearerAdmin()))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("Cadastro: senha salva com hash BCrypt, e-mail duplicado 409, senha curta 400")
    void cadastroSeguro() throws Exception {
        String token = cadastrar("Ana", "ana@email.com");
        String hash = jdbc.queryForObject("SELECT senha_hash FROM usuarios WHERE email = 'ana@email.com'", String.class);
        assertThat(hash).startsWith("$2a$").doesNotContain("senhaForte123");
        assertThat(jdbc.queryForObject("SELECT perfil::text FROM usuarios WHERE email = 'ana@email.com'", String.class))
                .isEqualTo("produtor");

        mvc.perform(get("/api/perfil").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propriedade").value("Minha propriedade"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Ana 2", "email", "ANA@email.com", "senha", "senhaForte123"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Bia", "email", "bia@email.com", "senha", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senha", notNullValue()));
    }

    @Test
    @DisplayName("Força bruta: 6ª tentativa seguida com senha errada retorna 429")
    void forcaBruta() throws Exception {
        var errado = json(Map.of("email", "invasor@teste.com", "senha", "chute"));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(errado))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(errado))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("XSS e SQL Injection: entradas maliciosas são recusadas e não afetam o banco")
    void entradasMaliciosas() throws Exception {
        String auth = bearerMaria();
        mvc.perform(post("/api/cultivos").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "<script>alert(1)</script>", "cultura", "Alface",
                                "umidadeMin", 30, "umidadeMax", 60, "vazaoLMin", 1.2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome", notNullValue()));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "' OR '1'='1", "senha", "x"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cultivos/" + idCultivo("Canteiro 2") + "/historico")
                        .param("periodo", "7d'; DROP TABLE cultivos; --").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cultivos/1 OR 1=1").header("Authorization", auth))
                .andExpect(status().isBadRequest());

        // Nome com aspas e comandos SQL e gravado como texto comum (consulta parametrizada)
        mvc.perform(post("/api/cultivos").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Canteiro'); DROP TABLE cultivos; --", "cultura", "Alface",
                                "umidadeMin", 30, "umidadeMax", 60, "vazaoLMin", 1.2))))
                .andExpect(status().isCreated());
        assertThat(contar("SELECT COUNT(*) FROM cultivos")).isEqualTo(4);
        mvc.perform(get("/api/cultivos").header("Authorization", auth))
                .andExpect(jsonPath("$[*].nome", hasItem("Canteiro'); DROP TABLE cultivos; --")));
    }
}
