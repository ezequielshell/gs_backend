package br.com.fiap.solovivo.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

    /** Relogio injetavel: facilita testes de regras que dependem de horario. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Fuso usado nos textos exibidos no app (ex.: "Hoje, 13h10"). */
    @Bean
    public ZoneId zonaHoraria(SoloVivoProperties properties) {
        return ZoneId.of(properties.timezone());
    }
}
