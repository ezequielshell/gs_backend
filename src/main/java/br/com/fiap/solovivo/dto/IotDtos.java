package br.com.fiap.solovivo.dto;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs da integracao IoT (ESP32 / simulador de sensores). */
public final class IotDtos {

    private IotDtos() {
    }

    /**
     * Uma leitura de sensor. Identifique o sensor por sensorId, pelo nome (ex.: "Sensor de solo 01")
     * ou pelo tipo (ex.: "temperatura_ar", quando o dispositivo tem um unico sensor desse tipo).
     */
    public record LeituraItemRequest(
            Long sensorId,
            @Size(max = 100) String sensor,
            @Size(max = 30) String tipo,
            @NotNull(message = "informe o valor da leitura") Double valor) {
    }

    /** Pacote de leituras enviado pelo ESP32 / simulador (POST /api/iot/leituras). */
    public record LeiturasRequest(
            @NotEmpty(message = "envie ao menos uma leitura")
            @Size(max = 50, message = "no máximo 50 leituras por envio") @Valid List<LeituraItemRequest> leituras,
            OffsetDateTime lidaEm) {
    }

    /** Decisao tomada pela automacao para um cultivo apos uma leitura (evidencia do CT-09). */
    public record DecisaoResponse(
            String cultivoId,
            String cultivo,
            String sensor,
            Double valor,
            String acao,
            String descricao,
            List<String> alertasAbertos,
            List<String> alertasResolvidos) {
    }

    public record LeiturasResponse(String dispositivo, int recebidas, String lidaEm,
                                   List<DecisaoResponse> decisoes) {
    }
}
