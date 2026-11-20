package br.com.fiap.solovivo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.solovivo.dto.IotDtos.LeiturasRequest;
import br.com.fiap.solovivo.dto.IotDtos.LeiturasResponse;
import br.com.fiap.solovivo.service.LeituraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/iot")
@Tag(name = "IoT", description = "Entrada de leituras dos sensores (ESP32 ou simulador)")
@SecurityRequirement(name = "deviceToken")
public class IotController {

    private final LeituraService leituraService;

    public IotController(LeituraService leituraService) {
        this.leituraService = leituraService;
    }

    @PostMapping("/leituras")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recebe leituras dos sensores, gera alertas e aplica a irrigação automática",
            description = "Autenticado pelo header X-Device-Token (token_api do dispositivo). "
                    + "Exemplo: {\"leituras\":[{\"sensor\":\"Sensor de solo 01\",\"valor\":24},"
                    + "{\"tipo\":\"temperatura_ar\",\"valor\":31},{\"tipo\":\"umidade_ar\",\"valor\":62}]}")
    @ApiResponse(responseCode = "201", description = "Leituras gravadas; decisões da automação na resposta")
    @ApiResponse(responseCode = "400", description = "Leitura inválida (sensor desconhecido ou valor fora da faixa)")
    @ApiResponse(responseCode = "401", description = "Token do dispositivo ausente ou inválido")
    public LeiturasResponse receber(@RequestHeader(name = "X-Device-Token", required = false) String token,
                                    @Valid @RequestBody LeiturasRequest request) {
        return leituraService.registrar(token, request);
    }
}
