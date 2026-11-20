package br.com.fiap.solovivo.unit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import br.com.fiap.solovivo.config.SoloVivoProperties;

/** Objetos auxiliares dos testes de unidade. */
final class Fixtures {

    static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres-123";

    private Fixtures() {
    }

    static SoloVivoProperties propriedades() {
        return new SoloVivoProperties(
                "America/Sao_Paulo",
                new SoloVivoProperties.Jwt(SEGREDO, 60),
                new SoloVivoProperties.Cors(List.of("http://localhost:*")),
                new SoloVivoProperties.Seguranca(5, 5),
                new SoloVivoProperties.Irrigacao(30, 35),
                new SoloVivoProperties.BombaSimulada(false),
                new SoloVivoProperties.Monitoramento(false, 60000),
                new SoloVivoProperties.Mqtt(false, "tcp://localhost:1883", "teste",
                        "fiap/iot/3esoa/tech-group/solovivo", 8));
    }

    /** Relogio que o teste pode avancar (ex.: simular 6 minutos depois). */
    static final class RelogioAjustavel extends Clock {

        private Instant agora;

        RelogioAjustavel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(agora, zone);
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
