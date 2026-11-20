package br.com.fiap.solovivo.exception;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corpo padrao de erro. O app le o campo "message" (ver src/services/api.ts do frontend).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(
        int status,
        String error,
        String message,
        String path,
        String timestamp,
        Map<String, String> campos) {

    public static ErroResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, null);
    }

    public static ErroResponse of(HttpStatus status, String message, String path, Map<String, String> campos) {
        return new ErroResponse(status.value(), status.getReasonPhrase(), message, path,
                OffsetDateTime.now().toString(), campos);
    }
}
