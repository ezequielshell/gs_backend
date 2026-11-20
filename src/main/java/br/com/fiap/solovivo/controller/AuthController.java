package br.com.fiap.solovivo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.solovivo.dto.AuthDtos.LoginRequest;
import br.com.fiap.solovivo.dto.AuthDtos.LoginResponse;
import br.com.fiap.solovivo.dto.AuthDtos.RegisterRequest;
import br.com.fiap.solovivo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login e cadastro (públicos)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login com e-mail e senha; retorna o token JWT")
    @ApiResponse(responseCode = "200", description = "Autenticado")
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas: e-mail bloqueado temporariamente")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastro de novo produtor (cria também uma propriedade padrão)")
    @ApiResponse(responseCode = "201", description = "Cadastrado e autenticado")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }
}
