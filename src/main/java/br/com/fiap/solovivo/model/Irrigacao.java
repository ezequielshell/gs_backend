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

/**
 * Tabela irrigacoes (Modal "Ligar irrigacao" / Tela 7 - Historico).
 * finalizado_em NULL = bomba ligada. duracao_segundos e volume_litros sao calculados
 * pelo trigger trg_irrigacoes_preparar quando a irrigacao e finalizada.
 */
@Entity
@Table(name = "irrigacoes")
public class Irrigacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cultivo_id", nullable = false)
    private Long cultivoId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private OrigemIrrigacao origem;

    @Column(length = 160)
    private String motivo;

    @Column(name = "iniciado_em", nullable = false)
    private OffsetDateTime iniciadoEm;

    @Column(name = "finalizado_em")
    private OffsetDateTime finalizadoEm;

    @Column(name = "duracao_prevista_min")
    private Integer duracaoPrevistaMin;

    @Column(name = "duracao_segundos", insertable = false, updatable = false)
    private Integer duracaoSegundos;

    @Column(name = "vazao_litros_min")
    private Double vazaoLitrosMin;

    @Column(name = "volume_litros", insertable = false, updatable = false)
    private Double volumeLitros;

    @Column(name = "umidade_inicial")
    private Double umidadeInicial;

    @Column(name = "umidade_final")
    private Double umidadeFinal;

    public Long getId() { return id; }
    public Long getCultivoId() { return cultivoId; }
    public void setCultivoId(Long cultivoId) { this.cultivoId = cultivoId; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public OrigemIrrigacao getOrigem() { return origem; }
    public void setOrigem(OrigemIrrigacao origem) { this.origem = origem; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public OffsetDateTime getIniciadoEm() { return iniciadoEm; }
    public void setIniciadoEm(OffsetDateTime iniciadoEm) { this.iniciadoEm = iniciadoEm; }
    public OffsetDateTime getFinalizadoEm() { return finalizadoEm; }
    public void setFinalizadoEm(OffsetDateTime finalizadoEm) { this.finalizadoEm = finalizadoEm; }
    public Integer getDuracaoPrevistaMin() { return duracaoPrevistaMin; }
    public void setDuracaoPrevistaMin(Integer duracaoPrevistaMin) { this.duracaoPrevistaMin = duracaoPrevistaMin; }
    public Integer getDuracaoSegundos() { return duracaoSegundos; }
    public Double getVazaoLitrosMin() { return vazaoLitrosMin; }
    public void setVazaoLitrosMin(Double vazaoLitrosMin) { this.vazaoLitrosMin = vazaoLitrosMin; }
    public Double getVolumeLitros() { return volumeLitros; }
    public Double getUmidadeInicial() { return umidadeInicial; }
    public void setUmidadeInicial(Double umidadeInicial) { this.umidadeInicial = umidadeInicial; }
    public Double getUmidadeFinal() { return umidadeFinal; }
    public void setUmidadeFinal(Double umidadeFinal) { this.umidadeFinal = umidadeFinal; }
}
