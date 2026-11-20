package br.com.fiap.solovivo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabela culturas (campo "Cultura" da Tela 4). usuario_id NULL = cultura padrao do sistema
 * (gerenciada pelo ADMIN); preenchido = cultura cadastrada pelo proprio produtor.
 */
@Entity
@Table(name = "culturas")
public class Cultura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "umidade_min_padrao", nullable = false)
    private Double umidadeMinPadrao;

    @Column(name = "umidade_max_padrao", nullable = false)
    private Double umidadeMaxPadrao;

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Double getUmidadeMinPadrao() { return umidadeMinPadrao; }
    public void setUmidadeMinPadrao(Double umidadeMinPadrao) { this.umidadeMinPadrao = umidadeMinPadrao; }
    public Double getUmidadeMaxPadrao() { return umidadeMaxPadrao; }
    public void setUmidadeMaxPadrao(Double umidadeMaxPadrao) { this.umidadeMaxPadrao = umidadeMaxPadrao; }

    public boolean isPadraoSistema() {
        return usuarioId == null;
    }
}
