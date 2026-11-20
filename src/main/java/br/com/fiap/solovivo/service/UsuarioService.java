package br.com.fiap.solovivo.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.solovivo.dto.GeralDtos.UsuarioResponse;
import br.com.fiap.solovivo.repository.UsuarioRepository;

/** Funcoes administrativas (somente perfil ADMIN). */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll(Sort.by("id")).stream()
                .map(u -> new UsuarioResponse(String.valueOf(u.getId()), u.getNome(), u.getEmail(),
                        u.getPerfil().name(), u.isAtivo(), u.getCriadoEm() == null ? null : u.getCriadoEm().toString()))
                .toList();
    }
}
