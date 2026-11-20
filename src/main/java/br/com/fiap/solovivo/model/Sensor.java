package br.com.fiap.solovivo.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabela sensores (umidade do solo, DHT22 temperatura/umidade do ar, etc.). */
@Entity
@Table(name = "sensores")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispositivo_id", nullable = false)
    private Long dispositivoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TipoSensor tipo;

    @Column(length = 50)
    private String modelo;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, length = 15)
    private String unidade;

    @Column(nullable = false)
    private boolean ativo = true;

    public Long getId() { return id; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public TipoSensor getTipo() { return tipo; }
    public void setTipo(TipoSensor tipo) { this.tipo = tipo; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
