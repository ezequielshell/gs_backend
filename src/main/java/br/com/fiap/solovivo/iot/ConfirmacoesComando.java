package br.com.fiap.solovivo.iot;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Liga um comando enviado por MQTT ao evento que o confirma: quem envia "ligar" aguarda o evento
 * "inicio" do mesmo dispositivo em /irrigacao; quem envia "desligar" aguarda o evento "fim".
 */
@Component
public class ConfirmacoesComando {

    private final Map<String, CompletableFuture<Void>> pendentes = new ConcurrentHashMap<>();

    /** Registre ANTES de publicar o comando, para nao perder uma confirmacao rapida. */
    public CompletableFuture<Void> aguardar(String dispositivo, String evento) {
        return pendentes.computeIfAbsent(chave(dispositivo, evento), k -> new CompletableFuture<>());
    }

    public void confirmar(String dispositivo, String evento) {
        CompletableFuture<Void> f = pendentes.remove(chave(dispositivo, evento));
        if (f != null) {
            f.complete(null);
        }
    }

    public void cancelar(String dispositivo, String evento, CompletableFuture<Void> futuro) {
        pendentes.remove(chave(dispositivo, evento), futuro);
    }

    private static String chave(String dispositivo, String evento) {
        return dispositivo + "|" + evento;
    }
}
