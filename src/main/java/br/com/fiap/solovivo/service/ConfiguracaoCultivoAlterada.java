package br.com.fiap.solovivo.service;

/** Evento: limites ou modo de irrigacao de um cultivo mudaram (o ESP32 precisa receber a nova config). */
public record ConfiguracaoCultivoAlterada(Long cultivoId) {
}
