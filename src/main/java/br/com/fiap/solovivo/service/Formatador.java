package br.com.fiap.solovivo.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/** Textos de data/hora no formato exibido pelo app ("14h32", "Hoje, 13h10", "1 h 45 min"). */
@Component
public class Formatador {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH'h'mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final ZoneId zona;
    private final Clock clock;

    public Formatador(ZoneId zonaHoraria, Clock clock) {
        this.zona = zonaHoraria;
        this.clock = clock;
    }

    /** "14h32" */
    public String hora(OffsetDateTime instante) {
        return instante == null ? null : instante.atZoneSameInstant(zona).format(HORA);
    }

    /** "Hoje, 13h10" | "Ontem, 18h40" | "16/11, 14h05" */
    public String dataHoraRelativa(OffsetDateTime instante) {
        ZonedDateTime local = instante.atZoneSameInstant(zona);
        LocalDate hoje = LocalDate.now(clock.withZone(zona));
        String hora = local.format(HORA);
        if (local.toLocalDate().equals(hoje)) {
            return "Hoje, " + hora;
        }
        if (local.toLocalDate().equals(hoje.minusDays(1))) {
            return "Ontem, " + hora;
        }
        return local.format(DIA) + ", " + hora;
    }

    /** Horario do alerta: "14h32" (hoje) | "Ontem, 16h20" | "16/11, 14h05" */
    public String horarioAlerta(OffsetDateTime instante) {
        String texto = dataHoraRelativa(instante);
        return texto.startsWith("Hoje, ") ? texto.substring("Hoje, ".length()) : texto;
    }

    /** "há 6h" | "há 25 min" | "há 2 dias" */
    public String tempoDesde(OffsetDateTime instante) {
        Duration d = Duration.between(instante.toInstant(), clock.instant());
        long minutos = d.toMinutes();
        if (minutos < 1) {
            return "agora";
        }
        if (minutos < 60) {
            return "há " + minutos + " min";
        }
        if (d.toHours() < 24) {
            return "há " + d.toHours() + "h";
        }
        long dias = d.toDays();
        return "há " + dias + (dias == 1 ? " dia" : " dias");
    }

    /** Meia-noite de hoje no fuso do produtor (periodo "hoje" do historico). */
    public OffsetDateTime inicioDoDia() {
        return LocalDate.now(clock.withZone(zona)).atStartOfDay(zona).toOffsetDateTime();
    }

    public ZoneId zona() {
        return zona;
    }

    /** 6300 s -> "1 h 45 min"; 1500 s -> "25 min"; 7200 s -> "2 h". */
    public static String duracao(long segundos) {
        long totalMin = Math.max(0, segundos) / 60;
        long h = totalMin / 60;
        long m = totalMin % 60;
        if (h == 0) {
            return m + " min";
        }
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
}
