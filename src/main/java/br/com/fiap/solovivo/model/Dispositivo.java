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

/** Tabela dispositivos: ESP32 (real ou simulado) com sensores e rele da bomba. */
@Entity
@Table(name = "dispositivos")
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "propriedade_id", nullable = false)
    private Long propriedadeId;

    @Column(nullable = false, length = 100)
    private String nome;

    /** MAC / chip id do ESP32. */
    @Column(nullable = false, length = 64, unique = true)
    private String identificador;

    /** Token que o dispositivo envia no header X-Device-Token. */
    @Column(name = "token_api", nullable = false, length = 128, unique = true)
    private String tokenApi;

    @Column(length = 30)
    private String firmware;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private StatusDispositivo status = StatusDispositivo.offline;

    @Column(name = "ultimo_contato")
    private OffsetDateTime ultimoContato;

    public Long getId() { return id; }
    public Long getPropriedadeId() { return propriedadeId; }
    public void setPropriedadeId(Long propriedadeId) { this.propriedadeId = propriedadeId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }
    public String getTokenApi() { return tokenApi; }
    public void setTokenApi(String tokenApi) { this.tokenApi = tokenApi; }
    public String getFirmware() { return firmware; }
    public void setFirmware(String firmware) { this.firmware = firmware; }
    public StatusDispositivo getStatus() { return status; }
    public void setStatus(StatusDispositivo status) { this.status = status; }
    public OffsetDateTime getUltimoContato() { return ultimoContato; }
    public void setUltimoContato(OffsetDateTime ultimoContato) { this.ultimoContato = ultimoContato; }
}
