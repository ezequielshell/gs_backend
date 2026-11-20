package br.com.fiap.solovivo.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;

/** Documentacao Swagger disponivel em /swagger-ui.html (JSON em /v3/api-docs). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Solo Vivo API",
        version = "1.0.0",
        description = """
                API REST do Solo Vivo: cultivos, limites de umidade, leituras de sensores (IoT simulado),
                irrigacao manual/automatica, alertas e historico.

                Como testar: faca POST /api/auth/login (maria@email.com / solovivo123), copie o token e
                clique em "Authorize". Os endpoints /api/iot usam o header X-Device-Token (demo-token-esp32-01).
                """))
@SecuritySchemes({
        @SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT"),
        @SecurityScheme(name = "deviceToken", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER,
                paramName = "X-Device-Token")
})
public class OpenApiConfig {
}
