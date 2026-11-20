package br.com.fiap.solovivo.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fiap.solovivo.exception.ErroResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Respostas JSON para 401 (sem login) e 403 (perfil sem permissao). */
@Component
public class JsonSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String mensagem = request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_TOKEN_INVALIDO) != null
                ? "Token inválido ou expirado. Faça login novamente."
                : "Autenticação necessária. Faça login para continuar.";
        escrever(response, HttpStatus.UNAUTHORIZED, mensagem, request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escrever(response, HttpStatus.FORBIDDEN,
                "Acesso negado: seu perfil não tem permissão para este recurso.", request.getRequestURI());
    }

    private void escrever(HttpServletResponse response, HttpStatus status, String mensagem, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErroResponse.of(status, mensagem, path));
    }
}
