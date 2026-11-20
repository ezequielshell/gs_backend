package br.com.fiap.solovivo.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import br.com.fiap.solovivo.config.SoloVivoProperties;
import br.com.fiap.solovivo.model.PerfilUsuario;
import br.com.fiap.solovivo.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Gera e valida tokens JWT assinados com HMAC-SHA256. */
@Service
public class JwtService {

    private static final String EMISSOR = "solo-vivo-api";

    private final SecretKey chave;
    private final Duration expiracao;
    private final Clock clock;

    public JwtService(SoloVivoProperties properties, Clock clock) {
        // Keys.hmacShaKeyFor recusa segredos com menos de 256 bits (32 bytes).
        this.chave = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expiracao = Duration.ofMinutes(properties.jwt().expiracaoMinutos());
        this.clock = clock;
    }

    public String gerar(Usuario usuario) {
        Instant agora = clock.instant();
        return Jwts.builder()
                .issuer(EMISSOR)
                .subject(usuario.getId().toString())
                .claim("email", usuario.getEmail())
                .claim("perfil", usuario.getPerfil().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracao)))
                .signWith(chave)
                .compact();
    }

    public Optional<UsuarioAutenticado> validar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .requireIssuer(EMISSOR)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new UsuarioAutenticado(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    PerfilUsuario.valueOf(claims.get("perfil", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long expiracaoSegundos() {
        return expiracao.toSeconds();
    }
}
