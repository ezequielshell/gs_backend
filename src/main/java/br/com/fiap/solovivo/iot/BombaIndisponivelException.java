package br.com.fiap.solovivo.iot;

/** O controlador de irrigacao nao confirmou o comando (offline, timeout, broker fora do ar...). */
public class BombaIndisponivelException extends RuntimeException {

    public BombaIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public BombaIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
