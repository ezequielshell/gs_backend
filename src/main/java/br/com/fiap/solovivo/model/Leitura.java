package br.com.fiap.solovivo.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabela leituras: serie temporal enviada pelos sensores. */
@Entity
@Table(name = "leituras")
public class Leitura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id", nullable = false)
    private Long sensorId;

    @Column(nullable = false)
    private Double valor;

    @Column(name = "lida_em", nullable = false)
    private OffsetDateTime lidaEm;

    public Leitura() {
    }

    public Leitura(Long sensorId, Double valor, OffsetDateTime lidaEm) {
        this.sensorId = sensorId;
        this.valor = valor;
        this.lidaEm = lidaEm;
    }

    public Long getId() { return id; }
    public Long getSensorId() { return sensorId; }
    public Double getValor() { return valor; }
    public OffsetDateTime getLidaEm() { return lidaEm; }
}
