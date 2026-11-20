package br.com.fiap.solovivo.iot;

/**
 * Resultado de um comando confirmado.
 *
 * @param registradoPeloDispositivo true quando a confirmacao veio de um evento do ESP32 em /irrigacao,
 *                                  que ja gravou o inicio/fim da irrigacao no banco (a API nao grava de novo).
 */
public record ResultadoComando(boolean registradoPeloDispositivo) {

    public static ResultadoComando local() {
        return new ResultadoComando(false);
    }

    public static ResultadoComando peloDispositivo() {
        return new ResultadoComando(true);
    }
}
