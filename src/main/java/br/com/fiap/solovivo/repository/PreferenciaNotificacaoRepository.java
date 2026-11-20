package br.com.fiap.solovivo.repository;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.fiap.solovivo.model.TipoAlerta;

/**
 * Tabela preferencias_notificacao (Tela 8 - switches de notificacao).
 * As linhas sao criadas pelo trigger trg_usuarios_preferencias quando o usuario e cadastrado.
 */
@Repository
public class PreferenciaNotificacaoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PreferenciaNotificacaoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<TipoAlerta, Boolean> buscar(Long usuarioId) {
        Map<TipoAlerta, Boolean> prefs = new EnumMap<>(TipoAlerta.class);
        jdbc.query("""
                SELECT tipo_alerta::text AS tipo, ativo
                  FROM preferencias_notificacao
                 WHERE usuario_id = :usuarioId
                """, Map.of("usuarioId", usuarioId),
                rs -> {
                    prefs.put(TipoAlerta.valueOf(rs.getString("tipo")), rs.getBoolean("ativo"));
                });
        return prefs;
    }

    public void salvar(Long usuarioId, TipoAlerta tipo, boolean ativo) {
        var params = new MapSqlParameterSource()
                .addValue("usuarioId", usuarioId)
                .addValue("tipo", tipo.name())
                .addValue("ativo", ativo);
        jdbc.update("""
                INSERT INTO preferencias_notificacao (usuario_id, tipo_alerta, ativo)
                VALUES (:usuarioId, CAST(:tipo AS tipo_alerta), :ativo)
                ON CONFLICT (usuario_id, tipo_alerta) DO UPDATE SET ativo = EXCLUDED.ativo
                """, params);
    }
}
