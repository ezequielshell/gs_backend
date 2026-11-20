package br.com.fiap.solovivo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.solovivo.dto.GeralDtos.UsuarioResponse;
import br.com.fiap.solovivo.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Restrito ao perfil ADMIN (regra em SecurityConfig: /api/usuarios/** -> hasRole ADMIN). */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários (admin)", description = "Somente perfil ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @Operation(summary = "[ADMIN] Lista todos os usuários")
    public List<UsuarioResponse> listar() {
        return usuarioService.listar();
    }
}
