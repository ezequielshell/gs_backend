package br.com.fiap.solovivo.integracao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * PostgreSQL REAL embarcado nos testes (sem Docker). E iniciado uma unica vez por execucao
 * e recriado antes de cada teste com os MESMOS scripts usados em producao:
 * database/01-dump-iot.sql + database/02-ajustes-api.sql.
 */
final class BancoDeTeste {

    private static EmbeddedPostgres instancia;
    private static String scripts;

    private BancoDeTeste() {
    }

    static synchronized EmbeddedPostgres iniciar() {
        if (instancia == null) {
            try {
                instancia = EmbeddedPostgres.builder().start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        instancia.close();
                    } catch (IOException ignored) {
                        // encerrando a JVM
                    }
                }));
            } catch (IOException e) {
                throw new UncheckedIOException("Nao foi possivel iniciar o PostgreSQL embarcado", e);
            }
        }
        return instancia;
    }

    static String jdbcUrl() {
        // prepareThreshold=0: sem prepared statements no servidor, pois o esquema e recriado a cada teste.
        return "jdbc:postgresql://localhost:" + iniciar().getPort() + "/postgres?prepareThreshold=0";
    }

    /** Apaga e recria o esquema inteiro (o dump comeca com DROP SCHEMA public CASCADE). */
    static void recriar(DataSource dataSource) {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(scripts());
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao recriar o banco de teste: " + e.getMessage(), e);
        }
    }

    private static synchronized String scripts() {
        if (scripts == null) {
            try {
                scripts = Files.readString(Path.of("database", "01-dump-iot.sql")) + "\n"
                        + Files.readString(Path.of("database", "02-ajustes-api.sql"));
            } catch (IOException e) {
                throw new UncheckedIOException("Scripts SQL nao encontrados em ./database", e);
            }
        }
        return scripts;
    }
}
