package br.com.fiap.solovivo.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.solovivo.dto.GeralDtos.CulturaRequest;
import br.com.fiap.solovivo.dto.GeralDtos.CulturaResponse;
import br.com.fiap.solovivo.security.UsuarioAutenticado;
import br.com.fiap.solovivo.service.CulturaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/culturas")
@Tag(name = "Culturas", description = "Catálogo de culturas com limites sugeridos")
@SecurityRequirement(name = "bearerAuth")
public class CulturaController {

    private final CulturaService culturaService;

    public CulturaController(CulturaService culturaService) {
        this.culturaService = culturaService;
    }

    @GetMapping
    @Operation(summary = "Lista culturas padrão + culturas do usuário")
    public List<CulturaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return culturaService.listar(usuario);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Cadastra cultura padrão do sistema")
    public ResponseEntity<CulturaResponse> criar(@Valid @RequestBody CulturaRequest request) {
        CulturaResponse criada = culturaService.criarPadrao(request);
        return ResponseEntity.created(URI.create("/api/culturas/" + criada.id())).body(criada);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Remove uma cultura")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        culturaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
