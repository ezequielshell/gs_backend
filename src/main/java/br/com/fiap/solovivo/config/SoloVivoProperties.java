package br.com.fiap.solovivo.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracoes da aplicacao (prefixo "solovivo" no application.yml). */
@ConfigurationProperties(prefix = "solovivo")
public record SoloVivoProperties(
        String timezone,
        Jwt jwt,
        Cors cors,
        Seguranca seguranca,
        Irrigacao irrigacao,
        BombaSimulada bombaSimulada,
        Monitoramento monitoramento,
        Mqtt mqtt) {

    public record Jwt(String secret, long expiracaoMinutos) {
    }

    public record Cors(List<String> origensPermitidas) {
    }

    public record Seguranca(int loginMaxTentativas, int loginBloqueioMinutos) {
    }

    public record Irrigacao(int intervaloMinimoEntreCiclosMin, double temperaturaAltaC) {
    }

    public record BombaSimulada(boolean falhar) {
    }

    public record Monitoramento(boolean habilitado, long intervaloMs) {
    }

    /**
     * Integracao com o firmware do ESP32 (projeto Arduino/Wokwi).
     *
     * @param topicoBase            prefixo dos topicos; o firmware usa {base}/{identificador}/telemetria etc.
     * @param timeoutConfirmacaoS   quanto tempo esperar o evento /irrigacao que confirma um comando
     */
    public record Mqtt(boolean habilitado, String brokerUrl, String clientId, String topicoBase,
                       int timeoutConfirmacaoS) {
    }
}
