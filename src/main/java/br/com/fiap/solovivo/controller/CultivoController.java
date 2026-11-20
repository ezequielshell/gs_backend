package br.com.fiap.solovivo.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.solovivo.dto.CultivoDtos.AcionarIrrigacaoRequest;
import br.com.fiap.solovivo.dto.CultivoDtos.CreateCultivoRequest;
import br.com.fiap.solovivo.dto.CultivoDtos.CultivoResponse;
import br.com.fiap.solovivo.dto.CultivoDtos.HistoricoResponse;
import br.com.fiap.solovivo.dto.CultivoDtos.UpdateLimitesRequest;
import br.com.fiap.solovivo.security.UsuarioAutenticado;
import br.com.fiap.solovivo.service.CultivoService;
import br.com.fiap.solovivo.service.IrrigacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cultivos")
@Tag(name = "Cultivos", description = "Canteiros, limites, irrigação e histórico")
@SecurityRequirement(name = "bearerAuth")
public class CultivoController {

    private final CultivoService cultivoService;
    private final IrrigacaoService irrigacaoService;

    public CultivoController(CultivoService cultivoService, IrrigacaoService irrigacaoService) {
        this.cultivoService = cultivoService;
        this.irrigacaoService = irrigacaoService;
    }

    @GetMapping
    @Operation(summary = "Lista os cultivos com status atual (adequado | atencao | sem_leitura)")
    public List<CultivoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return cultivoService.listar(usuario);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhes de um cultivo, com leituras atuais e gráfico das últimas 24h")
    @ApiResponse(responseCode = "404", description = "Cultivo não encontrado")
    public CultivoResponse buscar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return cultivoService.buscar(id, usuario);
    }

    @PostMapping
    @Operation(summary = "Cadastra um novo cultivo (canteiro)")
    @ApiResponse(responseCode = "201", description = "Cultivo criado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos (ex.: mínima >= máxima, sensor inexistente)")
    @ApiResponse(responseCode = "409", description = "Já existe cultivo com este nome")
    public ResponseEntity<CultivoResponse> criar(@Valid @RequestBody CreateCultivoRequest request,
                                                 @AuthenticationPrincipal UsuarioAutenticado usuario) {
        CultivoResponse criado = cultivoService.criar(request, usuario);
        return ResponseEntity.created(URI.create("/api/cultivos/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}/limites")
    @Operation(summary = "Atualiza limites de umidade, tempo máximo, vazão e automação")
    public CultivoResponse atualizarLimites(@PathVariable Long id, @Valid @RequestBody UpdateLimitesRequest request,
                                            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return cultivoService.atualizarLimites(id, request, usuario);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui (desativa) o cultivo; desliga a bomba se estiver ligada")
    @ApiResponse(responseCode = "204", description = "Cultivo excluído")
    public ResponseEntity<Void> excluir(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        cultivoService.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Histórico de irrigações, consumo estimado e tempo fora da faixa")
    public HistoricoResponse historico(@PathVariable Long id,
                                       @Parameter(description = "hoje | 7d | 30d")
                                       @RequestParam(defaultValue = "7d") String periodo,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return cultivoService.historico(id, periodo, usuario);
    }

    @PostMapping("/{id}/irrigacao/acionar")
    @Operation(summary = "Liga/desliga a irrigação manualmente, ou só troca o modo (apenasAlterarModo=true)")
    @ApiResponse(responseCode = "200", description = "Comando confirmado pela bomba e registrado")
    @ApiResponse(responseCode = "503", description = "Bomba não confirmou o comando: nada foi registrado")
    public CultivoResponse acionarIrrigacao(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) AcionarIrrigacaoRequest request,
                                            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return irrigacaoService.acionar(id, request, usuario);
    }
}
