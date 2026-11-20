package br.com.fiap.solovivo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import org.springframework.boot.SpringApplication;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * MODO DEMONSTRACAO (sem Docker): sobe um PostgreSQL embarcado, carrega os scripts de /database
 * e inicia a API apontando para ele. Os dados sao recriados a cada execucao.
 *
 * <pre>
 *   ./mvnw spring-boot:test-run          (Linux/Mac)
 *   .\mvnw.cmd spring-boot:test-run      (Windows)
 * </pre>
 */
public final class SoloVivoModoDemo {

    private SoloVivoModoDemo() {
    }

    public static void main(String[] args) throws Exception {
        EmbeddedPostgres banco = EmbeddedPostgres.builder().start();
        String scripts = Files.readString(Path.of("database", "01-dump-iot.sql")) + "\n"
                + Files.readString(Path.of("database", "02-ajustes-api.sql"));
        try (Connection c = banco.getPostgresDatabase().getConnection(); Statement st = c.createStatement()) {
            st.execute(scripts);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                banco.close();
            } catch (Exception ignored) {
                // encerrando
            }
        }));

        System.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:" + banco.getPort() + "/postgres");
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        System.out.println(">>> MODO DEMO: PostgreSQL embarcado na porta " + banco.getPort()
                + " com os dados de database/*.sql");
        SpringApplication.run(SoloVivoApplication.class, args);
    }
}
