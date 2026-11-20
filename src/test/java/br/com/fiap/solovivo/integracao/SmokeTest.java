package br.com.fiap.solovivo.integracao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Smoke | aplicação sobe com o banco real e lista os cultivos do dump")
class SmokeTest extends IntegracaoBase {

    @Test
    @DisplayName("GET /api/cultivos com a usuária de demonstração retorna os 3 canteiros")
    void listaCultivosDoDump() throws Exception {
        mvc.perform(get("/api/cultivos").header("Authorization", bearerMaria()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
