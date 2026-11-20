package br.com.fiap.solovivo.exception;

import org.springframework.http.HttpStatus;

/** Erro de negocio com status HTTP associado. A mensagem e exibida no app. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException naoEncontrado(String mensagem) {
        return new ApiException(HttpStatus.NOT_FOUND, mensagem);
    }

    public static ApiException invalido(String mensagem) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensagem);
    }

    public static ApiException conflito(String mensagem) {
        return new ApiException(HttpStatus.CONFLICT, mensagem);
    }

    public static ApiException naoAutorizado(String mensagem) {
        return new ApiException(HttpStatus.UNAUTHORIZED, mensagem);
    }

    public static ApiException muitasTentativas(String mensagem) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, mensagem);
    }

    public static ApiException regraNegocio(String mensagem) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem);
    }
}
