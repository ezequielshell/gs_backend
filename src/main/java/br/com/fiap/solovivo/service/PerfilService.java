package br.com.fiap.solovivo.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.solovivo.dto.GeralDtos.NotificacoesDto;
import br.com.fiap.solovivo.dto.GeralDtos.PerfilResponse;
import br.com.fiap.solovivo.dto.GeralDtos.PerfilUpdateRequest;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.model.Propriedade;
import br.com.fiap.solovivo.model.TemaApp;
import br.com.fiap.solovivo.model.TipoAlerta;
import br.com.fiap.solovivo.model.Usuario;
import br.com.fiap.solovivo.repository.PreferenciaNotificacaoRepository;
import br.com.fiap.solovivo.repository.PropriedadeRepository;
import br.com.fiap.solovivo.repository.UsuarioRepository;
import br.com.fiap.solovivo.security.UsuarioAutenticado;

/** Tela 8 - Perfil: dados do usuario, propriedade e preferencias de notificacao. */
@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final PreferenciaNotificacaoRepository preferenciaRepository;

    public PerfilService(UsuarioRepository usuarioRepository, PropriedadeRepository propriedadeRepository,
                         PreferenciaNotificacaoRepository preferenciaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.preferenciaRepository = preferenciaRepository;
    }

    @Transactional(readOnly = true)
    public PerfilResponse obter(UsuarioAutenticado autenticado) {
        Usuario usuario = carregar(autenticado);
        String propriedade = propriedadeRepository.findFirstByUsuarioIdOrderByIdAsc(usuario.getId())
                .map(Propriedade::getNome).orElse(null);
        Map<TipoAlerta, Boolean> prefs = preferenciaRepository.buscar(usuario.getId());
        return new PerfilResponse(usuario.getNome(), usuario.getEmail(), propriedade,
                new NotificacoesDto(prefs.getOrDefault(TipoAlerta.solo_abaixo_limite, true),
                        prefs.getOrDefault(TipoAlerta.sem_leitura_sensor, true)),
                usuario.getPerfil().name(), usuario.getTema().name());
    }

    @Transactional
    public PerfilResponse atualizar(UsuarioAutenticado autenticado, PerfilUpdateRequest req) {
        Usuario usuario = carregar(autenticado);
        String email = req.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailIgnoreCaseAndIdNot(email, usuario.getId())) {
            throw ApiException.conflito("E-mail já cadastrado por outro usuário.");
        }
        usuario.setNome(req.nome().trim());
        usuario.setEmail(email);
        if (req.tema() != null) {
            usuario.setTema(TemaApp.valueOf(req.tema()));
        }
        usuarioRepository.save(usuario);

        Propriedade propriedade = propriedadeRepository.findFirstByUsuarioIdOrderByIdAsc(usuario.getId())
                .orElseGet(() -> {
                    Propriedade nova = new Propriedade();
                    nova.setUsuarioId(usuario.getId());
                    return nova;
                });
        propriedade.setNome(req.propriedade().trim());
        propriedadeRepository.save(propriedade);

        preferenciaRepository.salvar(usuario.getId(), TipoAlerta.solo_abaixo_limite, req.notificacoes().soloAbaixoLimite());
        preferenciaRepository.salvar(usuario.getId(), TipoAlerta.sem_leitura_sensor, req.notificacoes().semLeituraSensor());

        usuarioRepository.flush();
        return obter(autenticado);
    }

    private Usuario carregar(UsuarioAutenticado autenticado) {
        return usuarioRepository.findById(autenticado.id())
                .orElseThrow(() -> ApiException.naoEncontrado("Usuário não encontrado."));
    }
}
