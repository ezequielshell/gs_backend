package br.com.fiap.solovivo.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de leitura que reaproveitam as VIEWS e FUNCOES criadas pelo time de banco
 * (v_cultivos_status e resumo_historico). Todas usam parametros nomeados (protecao contra SQL Injection).
 */
@Repository
public class ConsultaRepository {

    /** Linha da view v_cultivos_status (+ nome do sensor vinculado). */
    public record CultivoStatusRow(
            Long cultivoId, Long propriedadeId, String canteiro, String cultura, String sensor, Long sensorId,
            double umidadeMin, double umidadeMax, int tempoMaxAcionamentoMin, double vazaoBombaLMin,
            String modoIrrigacao, boolean irrigacaoAutomaticaAtiva, boolean bombaLigada,
            String irrigacaoPausadaMotivo, Double umidadeSolo, OffsetDateTime ultimaLeituraEm,
            Double temperaturaAr, Double umidadeAr, String status, String mensagemStatus) {
    }

    /** Resultado da funcao resumo_historico (cards da Tela 7). */
    public record ResumoHistorico(long totalIrrigacoes, double consumoEstimadoLitros, long tempoForaFaixaSegundos) {
    }

    public record AlertaRow(
            Long id, Long cultivoId, String cultivoNome, String tipo, String severidade, String mensagem,
            String acaoSugerida, Double valor, OffsetDateTime criadoEm, OffsetDateTime resolvidoEm) {
    }

    private static final String SQL_STATUS = """
            SELECT v.cultivo_id, v.propriedade_id, v.canteiro, v.cultura, s.nome AS sensor_nome,
                   c.sensor_umidade_id, v.umidade_min, v.umidade_max, v.tempo_max_acionamento_min,
                   v.vazao_bomba_l_min, v.modo_irrigacao::text AS modo_irrigacao,
                   v.irrigacao_automatica_ativa, v.bomba_ligada, v.irrigacao_pausada_motivo,
                   v.umidade_solo, v.ultima_leitura_em, v.temperatura_ar, v.umidade_ar,
                   v.status, v.mensagem_status
              FROM v_cultivos_status v
              JOIN cultivos c ON c.id = v.cultivo_id
              LEFT JOIN sensores s ON s.id = c.sensor_umidade_id
            """;

    private static final String SQL_ALERTAS = """
            SELECT a.id, a.cultivo_id, c.nome AS cultivo_nome, a.tipo::text AS tipo,
                   a.severidade::text AS severidade, a.mensagem, a.acao_sugerida, a.valor,
                   a.criado_em, a.resolvido_em
              FROM alertas a
              LEFT JOIN cultivos c ON c.id = a.cultivo_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param propriedades null = todas (perfil ADMIN). */
    public List<CultivoStatusRow> listarStatus(Collection<Long> propriedades) {
        if (propriedades == null) {
            return jdbc.query(SQL_STATUS + " ORDER BY v.canteiro", Map.of(), STATUS_MAPPER);
        }
        if (propriedades.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SQL_STATUS + " WHERE v.propriedade_id IN (:propriedades) ORDER BY v.canteiro",
                Map.of("propriedades", propriedades), STATUS_MAPPER);
    }

    public Optional<CultivoStatusRow> buscarStatus(Long cultivoId) {
        return jdbc.query(SQL_STATUS + " WHERE v.cultivo_id = :id", Map.of("id", cultivoId), STATUS_MAPPER)
                .stream().findFirst();
    }

    public ResumoHistorico resumoHistorico(Long cultivoId, OffsetDateTime desde) {
        var params = new MapSqlParameterSource().addValue("id", cultivoId).addValue("desde", desde);
        return jdbc.queryForObject("""
                SELECT total_irrigacoes, consumo_estimado_litros, tempo_fora_faixa_segundos
                  FROM resumo_historico(:id, :desde)
                """, params, (rs, i) -> new ResumoHistorico(
                rs.getLong("total_irrigacoes"),
                Optional.ofNullable(dbl(rs, "consumo_estimado_litros")).orElse(0.0),
                rs.getLong("tempo_fora_faixa_segundos")));
    }

    /** @param propriedades null = todas (perfil ADMIN). */
    public List<AlertaRow> listarAlertas(Collection<Long> propriedades, boolean ativos) {
        String filtroStatus = ativos ? "a.resolvido_em IS NULL" : "a.resolvido_em IS NOT NULL";
        String ordem = ativos ? " ORDER BY a.criado_em DESC" : " ORDER BY a.resolvido_em DESC";
        if (propriedades == null) {
            return jdbc.query(SQL_ALERTAS + " WHERE " + filtroStatus + ordem + " LIMIT 100",
                    Map.of(), ALERTA_MAPPER);
        }
        if (propriedades.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SQL_ALERTAS + " WHERE a.propriedade_id IN (:propriedades) AND " + filtroStatus
                + ordem + " LIMIT 100", Map.of("propriedades", propriedades), ALERTA_MAPPER);
    }

    public Optional<AlertaRow> buscarAlerta(Long id) {
        return jdbc.query(SQL_ALERTAS + " WHERE a.id = :id", Map.of("id", id), ALERTA_MAPPER).stream().findFirst();
    }

    private static final RowMapper<CultivoStatusRow> STATUS_MAPPER = (rs, i) -> new CultivoStatusRow(
            rs.getLong("cultivo_id"),
            rs.getLong("propriedade_id"),
            rs.getString("canteiro"),
            rs.getString("cultura"),
            rs.getString("sensor_nome"),
            (Long) rs.getObject("sensor_umidade_id", Long.class),
            rs.getDouble("umidade_min"),
            rs.getDouble("umidade_max"),
            rs.getInt("tempo_max_acionamento_min"),
            rs.getDouble("vazao_bomba_l_min"),
            rs.getString("modo_irrigacao"),
            rs.getBoolean("irrigacao_automatica_ativa"),
            rs.getBoolean("bomba_ligada"),
            rs.getString("irrigacao_pausada_motivo"),
            dbl(rs, "umidade_solo"),
            rs.getObject("ultima_leitura_em", OffsetDateTime.class),
            dbl(rs, "temperatura_ar"),
            dbl(rs, "umidade_ar"),
            rs.getString("status"),
            rs.getString("mensagem_status"));

    private static final RowMapper<AlertaRow> ALERTA_MAPPER = (rs, i) -> new AlertaRow(
            rs.getLong("id"),
            (Long) rs.getObject("cultivo_id", Long.class),
            rs.getString("cultivo_nome"),
            rs.getString("tipo"),
            rs.getString("severidade"),
            rs.getString("mensagem"),
            rs.getString("acao_sugerida"),
            dbl(rs, "valor"),
            rs.getObject("criado_em", OffsetDateTime.class),
            rs.getObject("resolvido_em", OffsetDateTime.class));

    private static Double dbl(ResultSet rs, String coluna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(coluna);
        return valor == null ? null : valor.doubleValue();
    }
}
