package br.com.fiap.solovivo.iot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Ultimo estado informado por cada ESP32 na telemetria (bomba, modo, pausa), mantido em memoria. */
@Component
public class EstadoDispositivos {

    public record Estado(Boolean bomba, String modo, Boolean automaticaAtiva, String pausa, Instant recebidoEm) {
    }

    private final Map<String, Estado> porDispositivo = new ConcurrentHashMap<>();
    private final Clock clock;

    public EstadoDispositivos(Clock clock) {
        this.clock = clock;
    }

    public void atualizar(String dispositivo, Boolean bomba, String modo, Boolean automaticaAtiva, String pausa) {
        porDispositivo.put(dispositivo, new Estado(bomba, modo, automaticaAtiva, pausa, clock.instant()));
    }

    /** Estado recebido ha no maximo {@code idadeMaxima}. */
    public Optional<Estado> recente(String dispositivo, Duration idadeMaxima) {
        return Optional.ofNullable(porDispositivo.get(dispositivo))
                .filter(e -> e.recebidoEm().plus(idadeMaxima).isAfter(clock.instant()));
    }
}
