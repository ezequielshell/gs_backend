package br.com.fiap.solovivo.model;

/**
 * Situacao do dispositivo ESP32 (ENUM status_dispositivo).
 * Os nomes ficam em minusculo para coincidir com os rotulos do ENUM no PostgreSQL.
 */
public enum StatusDispositivo {
    online, offline, manutencao
}
