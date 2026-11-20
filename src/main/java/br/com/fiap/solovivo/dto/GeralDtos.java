package br.com.fiap.solovivo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de alertas, perfil, culturas e usuarios. */
public final class GeralDtos {

    private GeralDtos() {
    }

    /** Alerta do app (+ tipo, valor e criadoEm). */
    public record AlertaResponse(
            String id,
            String cultivoId,
            String cultivoNome,
            String tipo,
            String horario,
            String severidade,
            String status,
            String mensagem,
            String acao,
            Double valor,
            String criadoEm) {
    }

    public record NotificacoesDto(
            @NotNull(message = "informe soloAbaixoLimite") Boolean soloAbaixoLimite,
            @NotNull(message = "informe semLeituraSensor") Boolean semLeituraSensor) {
    }

    /** PerfilUsuario do app (+ perfil de acesso e tema). */
    public record PerfilResponse(
            String nome,
            String email,
            String propriedade,
            NotificacoesDto notificacoes,
            String perfil,
            String tema) {
    }

    /** O app envia o PerfilUsuario completo no PUT /perfil. */
    public record PerfilUpdateRequest(
            @NotBlank(message = "informe o nome") @Size(max = 120) @SemHtml String nome,
            @NotBlank(message = "informe o e-mail") @Email(message = "e-mail inválido") @Size(max = 160) String email,
            @NotBlank(message = "informe a propriedade") @Size(max = 120) @SemHtml String propriedade,
            @NotNull(message = "informe as notificações") @Valid NotificacoesDto notificacoes,
            @Pattern(regexp = "claro|escuro|sistema", message = "use claro, escuro ou sistema") String tema) {
    }

    public record CulturaRequest(
            @NotBlank(message = "informe o nome") @Size(max = 100) @SemHtml String nome,
            @NotNull(message = "informe a umidade mínima padrão")
            @DecimalMin("0") @DecimalMax("100") Double umidadeMinPadrao,
            @NotNull(message = "informe a umidade máxima padrão")
            @DecimalMin("0") @DecimalMax("100") Double umidadeMaxPadrao) {

        @JsonIgnore
        @AssertTrue(message = "a umidade mínima deve ser menor que a umidade máxima")
        public boolean isFaixaUmidadeValida() {
            return umidadeMinPadrao == null || umidadeMaxPadrao == null || umidadeMinPadrao < umidadeMaxPadrao;
        }
    }

    public record CulturaResponse(String id, String nome, double umidadeMinPadrao, double umidadeMaxPadrao,
                                  boolean padraoSistema) {
    }

    public record UsuarioResponse(String id, String nome, String email, String perfil, boolean ativo,
                                  String criadoEm) {
    }
}
