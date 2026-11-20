package br.com.fiap.solovivo.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.solovivo.dto.GeralDtos.AlertaResponse;
import br.com.fiap.solovivo.security.UsuarioAutenticado;
import br.com.fiap.solovivo.service.AlertaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/alertas")
@Tag(name = "Alertas", description = "Alertas gerados pelas leituras dos sensores")
@SecurityRequirement(name = "bearerAuth")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping
    @Operation(summary = "Lista alertas ativos ou resolvidos")
    public List<AlertaResponse> listar(@Parameter(description = "ativos | resolvidos")
                                       @RequestParam(defaultValue = "ativos") String status,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return alertaService.listar(status, usuario);
    }

    @PutMapping("/{id}/resolver")
    @Operation(summary = "Marca um alerta como resolvido")
    public AlertaResponse resolver(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return alertaService.resolverManualmente(id, usuario);
    }
}
