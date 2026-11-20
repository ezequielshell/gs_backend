package br.com.fiap.solovivo.model;

import java.time.OffsetDateTime;

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

/** Tabela alertas (Modal 2 - abas Ativos | Resolvidos). resolvido_em NULL = ativo. */
@Entity
@Table(name = "alertas")
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "propriedade_id", nullable = false)
    private Long propriedadeId;

    @Column(name = "cultivo_id")
    private Long cultivoId;

    @Column(name = "sensor_id")
    private Long sensorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TipoAlerta tipo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private SeveridadeAlerta severidade = SeveridadeAlerta.atencao;

    @Column(nullable = false)
    private String mensagem;

    @Column(name = "acao_sugerida")
    private String acaoSugerida;

    private Double valor;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "lido_em")
    private OffsetDateTime lidoEm;

    @Column(name = "resolvido_em")
    private OffsetDateTime resolvidoEm;

    public Long getId() { return id; }
    public Long getPropriedadeId() { return propriedadeId; }
    public void setPropriedadeId(Long propriedadeId) { this.propriedadeId = propriedadeId; }
    public Long getCultivoId() { return cultivoId; }
    public void setCultivoId(Long cultivoId) { this.cultivoId = cultivoId; }
    public Long getSensorId() { return sensorId; }
    public void setSensorId(Long sensorId) { this.sensorId = sensorId; }
    public TipoAlerta getTipo() { return tipo; }
    public void setTipo(TipoAlerta tipo) { this.tipo = tipo; }
    public SeveridadeAlerta getSeveridade() { return severidade; }
    public void setSeveridade(SeveridadeAlerta severidade) { this.severidade = severidade; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
    public String getAcaoSugerida() { return acaoSugerida; }
    public void setAcaoSugerida(String acaoSugerida) { this.acaoSugerida = acaoSugerida; }
    public Double getValor() { return valor; }
    public void setValor(Double valor) { this.valor = valor; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(OffsetDateTime criadoEm) { this.criadoEm = criadoEm; }
    public OffsetDateTime getLidoEm() { return lidoEm; }
    public void setLidoEm(OffsetDateTime lidoEm) { this.lidoEm = lidoEm; }
    public OffsetDateTime getResolvidoEm() { return resolvidoEm; }
    public void setResolvidoEm(OffsetDateTime resolvidoEm) { this.resolvidoEm = resolvidoEm; }
}
