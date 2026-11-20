package br.com.fiap.solovivo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Solo Vivo API - irrigacao inteligente e monitoramento ambiental.
 *
 * <p>Camadas: controller (HTTP) -> service (regras de negocio) -> repository (banco).
 * A autenticacao e feita com JWT; o usuario padrao do Spring Security e desativado.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class SoloVivoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoloVivoApplication.class, args);
    }
}
