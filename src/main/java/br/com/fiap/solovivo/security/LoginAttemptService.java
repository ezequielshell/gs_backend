package br.com.fiap.solovivo.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import br.com.fiap.solovivo.config.SoloVivoProperties;
import br.com.fiap.solovivo.exception.ApiException;

/**
 * Protecao contra forca bruta: apos N senhas erradas seguidas o e-mail fica
 * bloqueado por alguns minutos (HTTP 429). Mantido em memoria (suficiente para o MVP).
 */
@Service
public class LoginAttemptService {

    private record Tentativas(int falhas, Instant bloqueadoAte) {
    }

    private final Map<String, Tentativas> porEmail = new ConcurrentHashMap<>();
    private final int maxTentativas;
    private final Duration bloqueio;
    private final Clock clock;

    public LoginAttemptService(SoloVivoProperties properties, Clock clock) {
        this.maxTentativas = properties.seguranca().loginMaxTentativas();
        this.bloqueio = Duration.ofMinutes(properties.seguranca().loginBloqueioMinutos());
        this.clock = clock;
    }

    public void verificarBloqueio(String email) {
        Tentativas t = porEmail.get(chave(email));
        if (t != null && t.bloqueadoAte() != null && clock.instant().isBefore(t.bloqueadoAte())) {
            throw ApiException.muitasTentativas(
                    "Muitas tentativas de login. Aguarde alguns minutos e tente novamente.");
        }
    }

    public void registrarFalha(String email) {
        porEmail.compute(chave(email), (k, atual) -> {
            int falhas = (atual == null || expirou(atual)) ? 1 : atual.falhas() + 1;
            Instant bloqueadoAte = falhas >= maxTentativas ? clock.instant().plus(bloqueio) : null;
            return new Tentativas(falhas, bloqueadoAte);
        });
    }

    public void registrarSucesso(String email) {
        porEmail.remove(chave(email));
    }

    private boolean expirou(Tentativas t) {
        return t.bloqueadoAte() != null && !clock.instant().isBefore(t.bloqueadoAte());
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
