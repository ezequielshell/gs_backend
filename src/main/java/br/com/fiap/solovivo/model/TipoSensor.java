package br.com.fiap.solovivo.model;

/**
 * Tipos de sensor (ENUM tipo_sensor).
 * Os nomes ficam em minusculo para coincidir com os rotulos do ENUM no PostgreSQL.
 */
public enum TipoSensor {
    umidade_solo, temperatura_ar, umidade_ar, luminosidade, chuva, vento
}
