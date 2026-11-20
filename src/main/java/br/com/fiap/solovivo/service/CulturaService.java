package br.com.fiap.solovivo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.solovivo.dto.GeralDtos.CulturaRequest;
import br.com.fiap.solovivo.dto.GeralDtos.CulturaResponse;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.model.Cultura;
import br.com.fiap.solovivo.repository.CulturaRepository;
import br.com.fiap.solovivo.security.UsuarioAutenticado;

/** Catalogo de culturas com limites de umidade sugeridos. */
@Service
public class CulturaService {

    private final CulturaRepository culturaRepository;

    public CulturaService(CulturaRepository culturaRepository) {
        this.culturaRepository = culturaRepository;
    }

    @Transactional(readOnly = true)
    public List<CulturaResponse> listar(UsuarioAutenticado usuario) {
        return culturaRepository.listarVisiveis(usuario.id()).stream().map(CulturaService::toResponse).toList();
    }

    /** ADMIN: cadastra cultura padrao do sistema (visivel para todos os produtores). */
    @Transactional
    public CulturaResponse criarPadrao(CulturaRequest req) {
        String nome = req.nome().trim();
        if (culturaRepository.existsByUsuarioIdIsNullAndNomeIgnoreCase(nome)) {
            throw ApiException.conflito("Já existe uma cultura padrão chamada '" + nome + "'.");
        }
        Cultura cultura = new Cultura();
        cultura.setNome(nome);
        cultura.setUmidadeMinPadrao(req.umidadeMinPadrao());
        cultura.setUmidadeMaxPadrao(req.umidadeMaxPadrao());
        return toResponse(culturaRepository.saveAndFlush(cultura));
    }

    /** ADMIN: remove uma cultura (cultivos que a usavam ficam sem cultura - ON DELETE SET NULL). */
    @Transactional
    public void excluir(Long id) {
        Cultura cultura = culturaRepository.findById(id)
                .orElseThrow(() -> ApiException.naoEncontrado("Cultura não encontrada."));
        culturaRepository.delete(cultura);
    }

    /** Usa a cultura existente (padrao ou do usuario) ou cria uma cultura propria do produtor. */
    public Cultura resolverOuCriar(String nome, Double umidadeMin, Double umidadeMax, Long usuarioId) {
        List<Cultura> existentes = culturaRepository.buscarVisivelPorNome(nome, usuarioId);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        Cultura nova = new Cultura();
        nova.setUsuarioId(usuarioId);
        nova.setNome(nome);
        nova.setUmidadeMinPadrao(umidadeMin);
        nova.setUmidadeMaxPadrao(umidadeMax);
        return culturaRepository.save(nova);
    }

    private static CulturaResponse toResponse(Cultura c) {
        return new CulturaResponse(String.valueOf(c.getId()), c.getNome(), c.getUmidadeMinPadrao(),
                c.getUmidadeMaxPadrao(), c.isPadraoSistema());
    }
}
