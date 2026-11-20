package br.com.fiap.solovivo.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import br.com.fiap.solovivo.dto.SemHtmlValidator;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.model.PerfilUsuario;
import br.com.fiap.solovivo.model.Usuario;
import br.com.fiap.solovivo.security.JwtService;
import br.com.fiap.solovivo.security.LoginAttemptService;
import br.com.fiap.solovivo.security.UsuarioAutenticado;

@DisplayName("Unidade | Segurança (JWT, senha, força bruta, XSS)")
class SegurancaUnitTest {

    private final Fixtures.RelogioAjustavel relogio = new Fixtures.RelogioAjustavel(Instant.parse("2026-11-20T12:00:00Z"));

    private static Usuario usuario(PerfilUsuario perfil) {
        Usuario u = new Usuario();
        u.setId(7L);
        u.setEmail("maria@email.com");
        u.setPerfil(perfil);
        return u;
    }

    @Test
    @DisplayName("JWT gerado é validado e carrega id, e-mail e perfil")
    void jwtValido() {
        JwtService jwt = new JwtService(Fixtures.propriedades(), relogio);
        String token = jwt.gerar(usuario(PerfilUsuario.admin));
        UsuarioAutenticado u = jwt.validar(token).orElseThrow();
        assertThat(u.id()).isEqualTo(7L);
        assertThat(u.email()).isEqualTo("maria@email.com");
        assertThat(u.isAdmin()).isTrue();
        assertThat(u.role()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("JWT adulterado é recusado")
    void jwtAdulterado() {
        JwtService jwt = new JwtService(Fixtures.propriedades(), relogio);
        String token = jwt.gerar(usuario(PerfilUsuario.produtor));
        String adulterado = token.substring(0, token.length() - 3) + (token.endsWith("abc") ? "xyz" : "abc");
        assertThat(jwt.validar(adulterado)).isEmpty();
        assertThat(jwt.validar("nao-e-um-jwt")).isEmpty();
    }

    @Test
    @DisplayName("JWT expirado é recusado (expiração de 60 min)")
    void jwtExpirado() {
        JwtService jwt = new JwtService(Fixtures.propriedades(), relogio);
        String token = jwt.gerar(usuario(PerfilUsuario.produtor));
        relogio.avancar(Duration.ofMinutes(61));
        assertThat(jwt.validar(token)).isEmpty();
    }

    @Test
    @DisplayName("Senha armazenada como hash BCrypt, nunca em texto puro")
    void senhaComHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("solovivo123");
        assertThat(hash).startsWith("$2a$").doesNotContain("solovivo123");
        assertThat(encoder.matches("solovivo123", hash)).isTrue();
        assertThat(encoder.matches("errada", hash)).isFalse();
    }

    @Test
    @DisplayName("Força bruta: bloqueia após 5 falhas e libera após 5 minutos")
    void bloqueioPorForcaBruta() {
        LoginAttemptService tentativas = new LoginAttemptService(Fixtures.propriedades(), relogio);
        for (int i = 0; i < 5; i++) {
            tentativas.verificarBloqueio("x@y.com");
            tentativas.registrarFalha("x@y.com");
        }
        assertThatThrownBy(() -> tentativas.verificarBloqueio("X@Y.com"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        relogio.avancar(Duration.ofMinutes(5));
        tentativas.verificarBloqueio("x@y.com"); // nao lanca mais
    }

    @Test
    @DisplayName("Login com sucesso zera o contador de falhas")
    void sucessoZeraContador() {
        LoginAttemptService tentativas = new LoginAttemptService(Fixtures.propriedades(), relogio);
        for (int i = 0; i < 4; i++) {
            tentativas.registrarFalha("x@y.com");
        }
        tentativas.registrarSucesso("x@y.com");
        tentativas.registrarFalha("x@y.com");
        tentativas.verificarBloqueio("x@y.com"); // apenas 1 falha: nao bloqueia
    }

    @Test
    @DisplayName("XSS: textos com tags HTML são rejeitados")
    void semHtml() {
        SemHtmlValidator v = new SemHtmlValidator();
        assertThat(v.isValid("Canteiro 4", null)).isTrue();
        assertThat(v.isValid(null, null)).isTrue();
        assertThat(v.isValid("<script>alert(1)</script>", null)).isFalse();
        assertThat(v.isValid("a > b", null)).isFalse();
    }
}
