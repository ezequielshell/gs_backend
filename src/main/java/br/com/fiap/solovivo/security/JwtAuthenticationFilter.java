package br.com.fiap.solovivo.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Le o header "Authorization: Bearer &lt;token&gt;" e autentica a requisicao.
 * Token ausente ou invalido: a requisicao segue anonima e as rotas protegidas respondem 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO_TOKEN_INVALIDO = "solovivo.tokenInvalido";
    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIXO)) {
            String token = header.substring(PREFIXO.length()).trim();
            jwtService.validar(token).ifPresentOrElse(usuario -> {
                var auth = new UsernamePasswordAuthenticationToken(
                        usuario, null, List.of(new SimpleGrantedAuthority(usuario.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }, () -> request.setAttribute(ATRIBUTO_TOKEN_INVALIDO, Boolean.TRUE));
        }
        chain.doFilter(request, response);
    }
}
