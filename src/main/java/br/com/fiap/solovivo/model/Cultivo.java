package br.com.fiap.solovivo.model;

import org.hibernate.annotations.DynamicUpdate;
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

/** Tabela cultivos (canteiros): limites de umidade e configuracao da irrigacao. */
@Entity
@Table(name = "cultivos")
@DynamicUpdate // UPDATE so das colunas alteradas (bomba_ligada e pausa vem do banco/ESP32)
public class Cultivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "propriedade_id", nullable = false)
    private Long propriedadeId;

    @Column(name = "cultura_id")
    private Long culturaId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "sensor_umidade_id")
    private Long sensorUmidadeId;

    @Column(name = "dispositivo_bomba_id")
    private Long dispositivoBombaId;

    @Column(name = "umidade_min", nullable = false)
    private Double umidadeMin = 30.0;

    @Column(name = "umidade_max", nullable = false)
    private Double umidadeMax = 60.0;

    @Column(name = "tempo_max_acionamento_min", nullable = false)
    private Integer tempoMaxAcionamentoMin = 15;

    @Column(name = "vazao_bomba_l_min", nullable = false)
    private Double vazaoBombaLMin = 1.2;

    @Column(name = "irrigacao_automatica_ativa", nullable = false)
    private boolean irrigacaoAutomaticaAtiva = true;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "modo_irrigacao", nullable = false)
    private ModoIrrigacao modoIrrigacao = ModoIrrigacao.automatico;

    /** Mantido pelo trigger trg_irrigacoes_bomba no banco: somente leitura aqui. */
    @Column(name = "bomba_ligada", insertable = false, updatable = false)
    private Boolean bombaLigada;

    @Column(name = "irrigacao_pausada_motivo")
    private String irrigacaoPausadaMotivo;

    @Column(name = "limite_sem_leitura_min", nullable = false)
    private Integer limiteSemLeituraMin = 60;

    @Column(nullable = false)
    private boolean ativo = true;

    public Long getId() { return id; }
    public Long getPropriedadeId() { return propriedadeId; }
    public void setPropriedadeId(Long propriedadeId) { this.propriedadeId = propriedadeId; }
    public Long getCulturaId() { return culturaId; }
    public void setCulturaId(Long culturaId) { this.culturaId = culturaId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Long getSensorUmidadeId() { return sensorUmidadeId; }
    public void setSensorUmidadeId(Long sensorUmidadeId) { this.sensorUmidadeId = sensorUmidadeId; }
    public Long getDispositivoBombaId() { return dispositivoBombaId; }
    public void setDispositivoBombaId(Long dispositivoBombaId) { this.dispositivoBombaId = dispositivoBombaId; }
    public Double getUmidadeMin() { return umidadeMin; }
    public void setUmidadeMin(Double umidadeMin) { this.umidadeMin = umidadeMin; }
    public Double getUmidadeMax() { return umidadeMax; }
    public void setUmidadeMax(Double umidadeMax) { this.umidadeMax = umidadeMax; }
    public Integer getTempoMaxAcionamentoMin() { return tempoMaxAcionamentoMin; }
    public void setTempoMaxAcionamentoMin(Integer v) { this.tempoMaxAcionamentoMin = v; }
    public Double getVazaoBombaLMin() { return vazaoBombaLMin; }
    public void setVazaoBombaLMin(Double vazaoBombaLMin) { this.vazaoBombaLMin = vazaoBombaLMin; }
    public boolean isIrrigacaoAutomaticaAtiva() { return irrigacaoAutomaticaAtiva; }
    public void setIrrigacaoAutomaticaAtiva(boolean v) { this.irrigacaoAutomaticaAtiva = v; }
    public ModoIrrigacao getModoIrrigacao() { return modoIrrigacao; }
    public void setModoIrrigacao(ModoIrrigacao modoIrrigacao) { this.modoIrrigacao = modoIrrigacao; }
    public Boolean getBombaLigada() { return bombaLigada; }
    public String getIrrigacaoPausadaMotivo() { return irrigacaoPausadaMotivo; }
    public void setIrrigacaoPausadaMotivo(String motivo) { this.irrigacaoPausadaMotivo = motivo; }
    public Integer getLimiteSemLeituraMin() { return limiteSemLeituraMin; }
    public void setLimiteSemLeituraMin(Integer v) { this.limiteSemLeituraMin = v; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
