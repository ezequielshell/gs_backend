package br.com.fiap.solovivo.security;

import br.com.fiap.solovivo.model.PerfilUsuario;

/** Dados do usuario logado, extraidos do token JWT. */
public record UsuarioAutenticado(Long id, String email, PerfilUsuario perfil) {

    public boolean isAdmin() {
        return perfil == PerfilUsuario.admin;
    }

    public String role() {
        return "ROLE_" + perfil.name().toUpperCase();
    }
}
