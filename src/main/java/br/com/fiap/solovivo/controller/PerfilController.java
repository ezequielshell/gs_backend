package br.com.fiap.solovivo.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.solovivo.dto.GeralDtos.PerfilResponse;
import br.com.fiap.solovivo.dto.GeralDtos.PerfilUpdateRequest;
import br.com.fiap.solovivo.security.UsuarioAutenticado;
import br.com.fiap.solovivo.service.PerfilService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/perfil")
@Tag(name = "Perfil", description = "Dados do usuário logado e preferências de notificação")
@SecurityRequirement(name = "bearerAuth")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping
    @Operation(summary = "Perfil do usuário logado")
    public PerfilResponse obter(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return perfilService.obter(usuario);
    }

    @PutMapping
    @Operation(summary = "Atualiza nome, e-mail, propriedade e notificações")
    public PerfilResponse atualizar(@Valid @RequestBody PerfilUpdateRequest request,
                                    @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return perfilService.atualizar(usuario, request);
    }
}
