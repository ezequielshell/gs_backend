package br.com.fiap.solovivo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs de autenticacao (mesmo formato de LoginResponse/AuthUser do app). */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "informe o e-mail") @Email(message = "e-mail inválido") @Size(max = 160) String email,
            @NotBlank(message = "informe a senha") @Size(max = 72) String senha) {
    }

    public record RegisterRequest(
            @NotBlank(message = "informe o nome") @Size(max = 120) @SemHtml String nome,
            @NotBlank(message = "informe o e-mail") @Email(message = "e-mail inválido") @Size(max = 160) String email,
            @NotBlank(message = "informe a senha")
            @Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres") String senha) {
    }

    public record AuthUserResponse(String id, String nome, String email, String perfil) {
    }

    public record LoginResponse(String token, String tipo, long expiraEmSegundos, AuthUserResponse user) {
    }
}
