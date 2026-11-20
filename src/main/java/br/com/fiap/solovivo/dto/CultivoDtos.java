package br.com.fiap.solovivo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de cultivos, limites, irrigacao e historico (formatos de src/types/index.ts do app). */
public final class CultivoDtos {

    private CultivoDtos() {
    }

    /** Ponto do grafico de umidade das ultimas 24h (ex.: {"hora": "16h", "umidade": 44}). */
    public record MoisturePoint(String hora, double umidade) {
    }

    /** Interface Cultivo do app + campos extras opcionais (ultimaLeituraEm, irrigacaoPausadaMotivo). */
    public record CultivoResponse(
            String id,
            String nome,
            String cultura,
            String sensor,
            Double umidadeSolo,
            double umidadeMin,
            double umidadeMax,
            Double temperatura,
            Double umidadeAr,
            String status,
            String mensagemStatus,
            String atualizadoEm,
            String ultimaLeituraEm,
            String modoIrrigacao,
            boolean irrigacaoAtiva,
            boolean irrigacaoAutomaticaAtivada,
            String irrigacaoPausadaMotivo,
            int tempoMaximoMin,
            double vazaoLMin,
            double consumoEstimadoL,
            List<MoisturePoint> historico24h) {
    }

    /** CreateCultivoPayload do app (+ sensorId e tempoMaximoMin opcionais). */
    public record CreateCultivoRequest(
            @NotBlank(message = "informe o nome do canteiro") @Size(max = 100) @SemHtml String nome,
            @NotBlank(message = "informe a cultura") @Size(max = 100) @SemHtml String cultura,
            @Size(max = 100) @SemHtml String sensor,
            Long sensorId,
            @NotNull(message = "informe a umidade mínima")
            @DecimalMin(value = "0", message = "deve ser entre 0 e 100")
            @DecimalMax(value = "100", message = "deve ser entre 0 e 100") Double umidadeMin,
            @NotNull(message = "informe a umidade máxima")
            @DecimalMin(value = "0", message = "deve ser entre 0 e 100")
            @DecimalMax(value = "100", message = "deve ser entre 0 e 100") Double umidadeMax,
            @NotNull(message = "informe a vazão da bomba")
            @DecimalMin(value = "0.01", message = "deve ser maior que zero")
            @DecimalMax(value = "1000", message = "valor muito alto") Double vazaoLMin,
            @Min(value = 1, message = "deve ser de 1 a 120 minutos")
            @Max(value = 120, message = "deve ser de 1 a 120 minutos (limite do firmware)") Integer tempoMaximoMin) {

        @JsonIgnore
        @AssertTrue(message = "a umidade mínima deve ser menor que a umidade máxima")
        public boolean isFaixaUmidadeValida() {
            return umidadeMin == null || umidadeMax == null || umidadeMin < umidadeMax;
        }
    }

    /** UpdateLimitesPayload do app (Tela 6 - Limites). */
    public record UpdateLimitesRequest(
            @NotNull(message = "informe a umidade mínima")
            @DecimalMin(value = "0", message = "deve ser entre 0 e 100")
            @DecimalMax(value = "100", message = "deve ser entre 0 e 100") Double umidadeMin,
            @NotNull(message = "informe a umidade máxima")
            @DecimalMin(value = "0", message = "deve ser entre 0 e 100")
            @DecimalMax(value = "100", message = "deve ser entre 0 e 100") Double umidadeMax,
            @NotNull(message = "informe o tempo máximo de acionamento")
            @Min(value = 1, message = "deve ser de 1 a 120 minutos")
            @Max(value = 120, message = "deve ser de 1 a 120 minutos (limite do firmware)") Integer tempoMaximoMin,
            @NotNull(message = "informe a vazão da bomba")
            @DecimalMin(value = "0.01", message = "deve ser maior que zero")
            @DecimalMax(value = "1000", message = "valor muito alto") Double vazaoLMin,
            @NotNull(message = "informe se a irrigação automática está ativada") Boolean irrigacaoAutomaticaAtivada) {

        @JsonIgnore
        @AssertTrue(message = "a umidade mínima deve ser menor que a umidade máxima")
        public boolean isFaixaUmidadeValida() {
            return umidadeMin == null || umidadeMax == null || umidadeMin < umidadeMax;
        }
    }

    /**
     * Corpo de POST /cultivos/{id}/irrigacao/acionar (igual ao irrigacaoService do app):
     * {"modo":"manual"} alterna a bomba; {"modo":"manual","apenasAlterarModo":true} so troca o modo.
     */
    public record AcionarIrrigacaoRequest(
            @Pattern(regexp = "manual|automatico", message = "use manual ou automatico") String modo,
            Boolean apenasAlterarModo) {
    }

    /** AcionamentoHistorico do app (+ emAndamento e volumeLitros). */
    public record AcionamentoResponse(
            String id,
            String origem,
            String descricaoRegra,
            String dataHoraLabel,
            int duracaoMin,
            boolean emAndamento,
            Double volumeLitros) {
    }

    /** HistoricoCultivo do app (Tela 7). */
    public record HistoricoResponse(
            String periodo,
            long irrigacoes,
            double consumoEstimadoL,
            String tempoForaFaixa,
            long tempoForaFaixaSegundos,
            List<AcionamentoResponse> acionamentos) {
    }
}
