package br.com.fiap.solovivo.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.solovivo.dto.IotDtos.DecisaoResponse;
import br.com.fiap.solovivo.dto.IotDtos.LeituraItemRequest;
import br.com.fiap.solovivo.dto.IotDtos.LeiturasRequest;
import br.com.fiap.solovivo.dto.IotDtos.LeiturasResponse;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.model.Dispositivo;
import br.com.fiap.solovivo.model.Leitura;
import br.com.fiap.solovivo.model.Sensor;
import br.com.fiap.solovivo.model.StatusDispositivo;
import br.com.fiap.solovivo.model.TipoSensor;
import br.com.fiap.solovivo.repository.CultivoRepository;
import br.com.fiap.solovivo.repository.DispositivoRepository;
import br.com.fiap.solovivo.repository.LeituraRepository;
import br.com.fiap.solovivo.repository.SensorRepository;

/**
 * Recebe leituras dos sensores (HTTP ou MQTT), valida, grava e dispara a automacao.
 * O dispositivo se autentica pelo token_api (tabela dispositivos).
 */
@Service
public class LeituraService {

    /** Faixa fisicamente possivel de cada tipo de sensor: leituras fora disso sao rejeitadas. */
    private static final Map<TipoSensor, double[]> FAIXAS = Map.of(
            TipoSensor.umidade_solo, new double[] {0, 100},
            TipoSensor.umidade_ar, new double[] {0, 100},
            TipoSensor.temperatura_ar, new double[] {-40, 80},
            TipoSensor.luminosidade, new double[] {0, 200_000},
            TipoSensor.chuva, new double[] {0, 500},
            TipoSensor.vento, new double[] {0, 300});

    private static final int MAX_LEITURAS = 50;

    private final DispositivoRepository dispositivoRepository;
    private final SensorRepository sensorRepository;
    private final LeituraRepository leituraRepository;
    private final CultivoRepository cultivoRepository;
    private final AutomacaoService automacaoService;
    private final Clock clock;

    public LeituraService(DispositivoRepository dispositivoRepository, SensorRepository sensorRepository,
                          LeituraRepository leituraRepository, CultivoRepository cultivoRepository,
                          AutomacaoService automacaoService, Clock clock) {
        this.dispositivoRepository = dispositivoRepository;
        this.sensorRepository = sensorRepository;
        this.leituraRepository = leituraRepository;
        this.cultivoRepository = cultivoRepository;
        this.automacaoService = automacaoService;
        this.clock = clock;
    }

    @Transactional
    public LeiturasResponse registrar(String tokenDispositivo, LeiturasRequest request) {
        if (tokenDispositivo == null || tokenDispositivo.isBlank()) {
            throw ApiException.naoAutorizado("Informe o token do dispositivo no header X-Device-Token.");
        }
        Dispositivo dispositivo = dispositivoRepository.findByTokenApi(tokenDispositivo.trim())
                .orElseThrow(() -> ApiException.naoAutorizado("Token do dispositivo inválido."));
        return processar(dispositivo, request);
    }

    /**
     * Valida, grava e aplica as regras. Usado pelo endpoint HTTP (dispositivo autenticado por token)
     * e pela telemetria MQTT (dispositivo identificado pelo topico). Roda na transacao de quem chama.
     */
    public LeiturasResponse processar(Dispositivo dispositivo, LeiturasRequest request) {
        if (request == null || request.leituras() == null || request.leituras().isEmpty()) {
            throw ApiException.invalido("Envie ao menos uma leitura.");
        }
        if (request.leituras().size() > MAX_LEITURAS) {
            throw ApiException.invalido("No máximo " + MAX_LEITURAS + " leituras por envio.");
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        OffsetDateTime lidaEm = request.lidaEm() == null ? agora : request.lidaEm();
        if (lidaEm.isAfter(agora.plusMinutes(5))) {
            throw ApiException.invalido("lidaEm não pode estar no futuro.");
        }
        if (lidaEm.isBefore(agora.minusDays(7))) {
            throw ApiException.invalido("lidaEm muito antiga (máximo 7 dias).");
        }

        // 1) Valida tudo antes de gravar: ou o pacote inteiro e aceito, ou nada e gravado.
        List<Sensor> sensores = sensorRepository.findByDispositivoIdAndAtivoTrue(dispositivo.getId());
        List<Sensor> sensoresValidos = new ArrayList<>();
        List<Double> valores = new ArrayList<>();
        List<String> erros = new ArrayList<>();
        for (int i = 0; i < request.leituras().size(); i++) {
            LeituraItemRequest item = request.leituras().get(i);
            if (item == null || item.valor() == null) {
                erros.add("leituras[" + i + "]: informe o valor");
                continue;
            }
            Optional<Sensor> sensor = resolverSensor(item, sensores);
            if (sensor.isEmpty()) {
                erros.add("leituras[" + i + "]: sensor não encontrado neste dispositivo");
                continue;
            }
            double valor = item.valor();
            double[] faixa = FAIXAS.get(sensor.get().getTipo());
            if (Double.isNaN(valor) || valor < faixa[0] || valor > faixa[1]) {
                erros.add(String.format(Locale.ROOT, "leituras[%d]: valor %s fora da faixa válida de %s (%s a %s)",
                        i, RegraIrrigacao.pct(valor), sensor.get().getTipo(),
                        RegraIrrigacao.pct(faixa[0]), RegraIrrigacao.pct(faixa[1])));
                continue;
            }
            sensoresValidos.add(sensor.get());
            valores.add(valor);
        }
        if (!erros.isEmpty()) {
            throw ApiException.invalido("Leituras rejeitadas: " + String.join("; ", erros));
        }

        // 2) Grava as leituras e atualiza o status do dispositivo.
        List<Leitura> leituras = new ArrayList<>();
        for (int i = 0; i < sensoresValidos.size(); i++) {
            leituras.add(new Leitura(sensoresValidos.get(i).getId(), valores.get(i), lidaEm));
        }
        leituraRepository.saveAll(leituras);
        dispositivo.setUltimoContato(agora);
        dispositivo.setStatus(StatusDispositivo.online);
        dispositivoRepository.save(dispositivo);

        // 3) Aplica as regras (alertas + irrigacao automatica).
        List<DecisaoResponse> decisoes = new ArrayList<>();
        for (int i = 0; i < sensoresValidos.size(); i++) {
            Sensor sensor = sensoresValidos.get(i);
            double valor = valores.get(i);
            switch (sensor.getTipo()) {
                case umidade_solo -> cultivoRepository.findBySensorUmidadeIdAndAtivoTrue(sensor.getId())
                        .forEach(c -> decisoes.add(automacaoService.aplicarLeituraSolo(c, sensor, valor, agora)));
                case temperatura_ar -> cultivoRepository.buscarAtivosPorDispositivo(dispositivo.getId())
                        .forEach(c -> Optional.ofNullable(automacaoService.aplicarLeituraTemperatura(c, sensor, valor))
                                .ifPresent(decisoes::add));
                default -> { }
            }
        }
        return new LeiturasResponse(dispositivo.getNome(), leituras.size(), lidaEm.toString(), decisoes);
    }

    private Optional<Sensor> resolverSensor(LeituraItemRequest item, List<Sensor> sensores) {
        if (item.sensorId() != null) {
            return sensores.stream().filter(s -> s.getId().equals(item.sensorId())).findFirst();
        }
        if (item.sensor() != null && !item.sensor().isBlank()) {
            String nome = item.sensor().trim();
            String alternativo = CultivoService.alternarPreposicao(nome);
            return sensores.stream()
                    .filter(s -> s.getNome().equalsIgnoreCase(nome) || s.getNome().equalsIgnoreCase(alternativo))
                    .findFirst();
        }
        if (item.tipo() != null) {
            List<Sensor> doTipo = sensores.stream().filter(s -> s.getTipo().name().equals(item.tipo())).toList();
            return doTipo.size() == 1 ? Optional.of(doTipo.get(0)) : Optional.empty();
        }
        return Optional.empty();
    }
}
