package br.com.fiap.solovivo.model;

/**
 * Tipos de alerta (ENUM tipo_alerta).
 * Os nomes ficam em minusculo para coincidir com os rotulos do ENUM no PostgreSQL.
 */
public enum TipoAlerta {
    solo_abaixo_limite, solo_acima_limite, sem_leitura_sensor, irrigacao_pausada, temperatura_alta
}
