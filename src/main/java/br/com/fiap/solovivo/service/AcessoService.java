package br.com.fiap.solovivo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.model.Cultivo;
import br.com.fiap.solovivo.repository.CultivoRepository;
import br.com.fiap.solovivo.repository.PropriedadeRepository;
import br.com.fiap.solovivo.security.UsuarioAutenticado;

/**
 * Controle de acesso por dono do recurso: o PRODUTOR so enxerga dados das proprias
 * propriedades; o ADMIN enxerga tudo. Recurso de outro usuario responde 404 (nao revela que existe).
 */
@Service
public class AcessoService {

    private final PropriedadeRepository propriedadeRepository;
    private final CultivoRepository cultivoRepository;

    public AcessoService(PropriedadeRepository propriedadeRepository, CultivoRepository cultivoRepository) {
        this.propriedadeRepository = propriedadeRepository;
        this.cultivoRepository = cultivoRepository;
    }

    /** @return null para ADMIN (todas), ou os ids das propriedades do produtor. */
    public List<Long> propriedadesVisiveis(UsuarioAutenticado usuario) {
        return usuario.isAdmin() ? null : propriedadeRepository.idsDoUsuario(usuario.id());
    }

    public boolean podeAcessarPropriedade(UsuarioAutenticado usuario, Long propriedadeId) {
        return usuario.isAdmin() || propriedadeRepository.idsDoUsuario(usuario.id()).contains(propriedadeId);
    }

    public Cultivo cultivoAutorizado(Long cultivoId, UsuarioAutenticado usuario) {
        return cultivoRepository.findByIdAndAtivoTrue(cultivoId)
                .filter(c -> podeAcessarPropriedade(usuario, c.getPropriedadeId()))
                .orElseThrow(() -> ApiException.naoEncontrado("Cultivo não encontrado."));
    }
}
