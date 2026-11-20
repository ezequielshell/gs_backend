package br.com.fiap.solovivo.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.solovivo.dto.AuthDtos.AuthUserResponse;
import br.com.fiap.solovivo.dto.AuthDtos.LoginRequest;
import br.com.fiap.solovivo.dto.AuthDtos.LoginResponse;
import br.com.fiap.solovivo.dto.AuthDtos.RegisterRequest;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.model.PerfilUsuario;
import br.com.fiap.solovivo.model.Propriedade;
import br.com.fiap.solovivo.model.Usuario;
import br.com.fiap.solovivo.repository.PropriedadeRepository;
import br.com.fiap.solovivo.repository.UsuarioRepository;
import br.com.fiap.solovivo.security.JwtService;
import br.com.fiap.solovivo.security.LoginAttemptService;

/** Login (CT-01) e cadastro de produtor. */
@Service
public class AuthService {

    private static final String MENSAGEM_CREDENCIAIS = "E-mail ou senha inválidos.";

    private final UsuarioRepository usuarioRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    /** Hash ficticio: compara a senha mesmo quando o e-mail nao existe (evita descobrir e-mails pelo tempo). */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarioRepository, PropriedadeRepository propriedadeRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptService loginAttemptService) {
        this.usuarioRepository = usuarioRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.hashFicticio = passwordEncoder.encode("senha-ficticia-solo-vivo");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        String email = req.email().trim();
        loginAttemptService.verificarBloqueio(email);

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean senhaConfere = passwordEncoder.matches(req.senha(),
                usuario != null ? usuario.getSenhaHash() : hashFicticio);
        if (usuario == null || !usuario.isAtivo() || !senhaConfere) {
            loginAttemptService.registrarFalha(email);
            throw ApiException.naoAutorizado(MENSAGEM_CREDENCIAIS);
        }
        loginAttemptService.registrarSucesso(email);
        return resposta(usuario);
    }

    /** Cadastro aberto apenas para o perfil PRODUTOR (admin nao se cadastra pelo app). */
    @Transactional
    public LoginResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflito("E-mail já cadastrado.");
        }
        Usuario usuario = new Usuario();
        usuario.setNome(req.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(req.senha()));
        usuario.setPerfil(PerfilUsuario.produtor);
        usuarioRepository.saveAndFlush(usuario); // trigger cria as preferencias de notificacao

        Propriedade propriedade = new Propriedade();
        propriedade.setUsuarioId(usuario.getId());
        propriedade.setNome("Minha propriedade");
        propriedadeRepository.save(propriedade);

        return resposta(usuario);
    }

    private LoginResponse resposta(Usuario u) {
        return new LoginResponse(jwtService.gerar(u), "Bearer", jwtService.expiracaoSegundos(),
                new AuthUserResponse(String.valueOf(u.getId()), u.getNome(), u.getEmail(), u.getPerfil().name()));
    }
}
