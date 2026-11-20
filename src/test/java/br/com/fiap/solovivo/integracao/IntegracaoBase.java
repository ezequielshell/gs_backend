package br.com.fiap.solovivo.integracao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base dos testes de API: sobe a aplicacao inteira (Spring Boot + seguranca + banco real)
 * e recria o banco antes de cada teste, garantindo testes independentes.
 */
@SpringBootTest(properties = "solovivo.monitoramento.habilitado=false")
@AutoConfigureMockMvc
public abstract class IntegracaoBase {

    protected static final String TOKEN_ESP32 = "demo-token-esp32-01";

    @DynamicPropertySource
    static void bancoEmbarcado(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", BancoDeTeste::jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void recriarBanco() {
        BancoDeTeste.recriar(dataSource);
    }

    protected String json(Object corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }

    protected JsonNode corpo(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected String login(String email, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "senha", senha))))
                .andExpect(status().isOk())
                .andReturn();
        return corpo(r).get("token").asText();
    }

    protected String bearerMaria() throws Exception {
        return "Bearer " + login("maria@email.com", "solovivo123");
    }

    protected String bearerAdmin() throws Exception {
        return "Bearer " + login("admin@solovivo.com", "admin12345");
    }

    protected long idCultivo(String nome) {
        return jdbc.queryForObject("SELECT id FROM cultivos WHERE nome = ? AND ativo", Long.class, nome);
    }

    protected ResultActions enviarLeituras(String corpoJson) throws Exception {
        return mvc.perform(post("/api/iot/leituras").header("X-Device-Token", TOKEN_ESP32)
                .contentType(MediaType.APPLICATION_JSON).content(corpoJson));
    }

    protected long contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
