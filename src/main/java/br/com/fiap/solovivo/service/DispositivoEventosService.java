package br.com.fiap.solovivo.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.solovivo.dto.IotDtos.LeituraItemRequest;
import br.com.fiap.solovivo.dto.IotDtos.LeiturasRequest;
import br.com.fiap.solovivo.dto.IotDtos.LeiturasResponse;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.iot.EstadoDispositivos;
import br.com.fiap.solovivo.iot.TopologiaIot;
import br.com.fiap.solovivo.iot.mqtt.MqttMensagemParser.EventoIrrigacao;
import br.com.fiap.solovivo.iot.mqtt.MqttMensagemParser.Telemetria;
import br.com.fiap.solovivo.model.Cultivo;
import br.com.fiap.solovivo.model.Dispositivo;
import br.com.fiap.solovivo.model.Irrigacao;
import br.com.fiap.solovivo.model.OrigemIrrigacao;
import br.com.fiap.solovivo.model.Sensor;
import br.com.fiap.solovivo.model.StatusDispositivo;
import br.com.fiap.solovivo.model.TipoSensor;
import br.com.fiap.solovivo.repository.CultivoRepository;
import br.com.fiap.solovivo.repository.DispositivoRepository;
import br.com.fiap.solovivo.repository.IrrigacaoRepository;
import br.com.fiap.solovivo.repository.PropriedadeRepository;
import br.com.fiap.solovivo.repository.SensorRepository;
import br.com.fiap.solovivo.repository.UsuarioRepository;

/**
 * Traduz as mensagens do firmware do ESP32 (recebidas por MQTT) para o banco.
 * O ESP32 decide a irrigacao automatica localmente; a API registra, gera alertas e mostra no app.
 */
@Service
public class DispositivoEventosService {

    private static final Logger log = LoggerFactory.getLogger(DispositivoEventosService.class);
    /** Telemetria "bomba=false" fecha um registro aberto ha mais tempo que isso (evento "fim" perdido). */
    private static final long CARENCIA_SINCRONIZACAO_S = 30;

    private final DispositivoRepository dispositivoRepository;
    private final SensorRepository sensorRepository;
    private final CultivoRepository cultivoRepository;
    private final IrrigacaoRepository irrigacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final LeituraService leituraService;
    private final TopologiaIot topologia;
    private final EstadoDispositivos estados;
    private final Clock clock;

    public DispositivoEventosService(DispositivoRepository dispositivoRepository, SensorRepository sensorRepository,
                                     CultivoRepository cultivoRepository, IrrigacaoRepository irrigacaoRepository,
                                     UsuarioRepository usuarioRepository, PropriedadeRepository propriedadeRepository,
                                     LeituraService leituraService, TopologiaIot topologia,
                                     EstadoDispositivos estados, Clock clock) {
        this.dispositivoRepository = dispositivoRepository;
        this.sensorRepository = sensorRepository;
        this.cultivoRepository = cultivoRepository;
        this.irrigacaoRepository = irrigacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.leituraService = leituraService;
        this.topologia = topologia;
        this.estados = estados;
        this.clock = clock;
    }

    /** /telemetria: {"umidade_solo":24,"temperatura":31,"umidade_ar":62,"luminosidade":80,"bomba":false,...} */
    @Transactional
    public LeiturasResponse processarTelemetria(String identificador, Telemetria t) {
        Dispositivo dispositivo = dispositivo(identificador);
        estados.atualizar(identificador, t.bomba(), t.modo(), t.automaticaAtiva(), t.pausa());
        OffsetDateTime agora = OffsetDateTime.now(clock);

        topologia.cultivoControlado(dispositivo.getId()).ifPresent(cultivo -> {
            // Pausa informada pelo ESP32 (ex.: "Aguardando intervalo minimo entre ciclos") aparece no app.
            if (!Objects.equals(t.pausa(), cultivo.getIrrigacaoPausadaMotivo())) {
                cultivo.setIrrigacaoPausadaMotivo(t.pausa());
                cultivoRepository.save(cultivo);
            }
            if (Boolean.FALSE.equals(t.bomba())) {
                fecharIrrigacaoEsquecida(cultivo, t.umidadeSolo(), agora);
            }
        });

        Set<TipoSensor> tipos = sensorRepository.findByDispositivoIdAndAtivoTrue(dispositivo.getId()).stream()
                .map(Sensor::getTipo).collect(Collectors.toSet());
        List<LeituraItemRequest> itens = new ArrayList<>();
        if (t.umidadeSolo() != null) {
            topologia.sensorSoloPrincipal(dispositivo.getId())
                    .ifPresent(s -> itens.add(new LeituraItemRequest(s.getId(), null, null, t.umidadeSolo())));
        }
        adicionar(itens, tipos, TipoSensor.temperatura_ar, t.temperatura());
        adicionar(itens, tipos, TipoSensor.umidade_ar, t.umidadeAr());
        adicionar(itens, tipos, TipoSensor.luminosidade, t.luminosidade());

        if (itens.isEmpty()) {
            dispositivo.setUltimoContato(agora);
            dispositivo.setStatus(StatusDispositivo.online);
            return new LeiturasResponse(dispositivo.getNome(), 0, agora.toString(), List.of());
        }
        return leituraService.processar(dispositivo, new LeiturasRequest(itens, null));
    }

    /** /irrigacao: eventos "inicio" e "fim" publicados pelo firmware (automaticos ou por comando). */
    @Transactional
    public void processarEventoIrrigacao(String identificador, EventoIrrigacao e) {
        Dispositivo dispositivo = dispositivo(identificador);
        OffsetDateTime agora = OffsetDateTime.now(clock);
        dispositivo.setUltimoContato(agora);
        dispositivo.setStatus(StatusDispositivo.online);

        Cultivo cultivo = topologia.cultivoControlado(dispositivo.getId()).orElse(null);
        if (cultivo == null) {
            log.warn("[ESP32] {} sem cultivo ligado ao sensor de solo principal; evento ignorado", identificador);
            return;
        }
        var emAndamento = irrigacaoRepository.findFirstByCultivoIdAndFinalizadoEmIsNull(cultivo.getId());

        if ("inicio".equals(e.evento())) {
            if (emAndamento.isPresent()) {
                return; // ja registrado (evento repetido)
            }
            OrigemIrrigacao origem = "manual".equals(e.origem()) ? OrigemIrrigacao.manual : OrigemIrrigacao.automatica;
            Irrigacao i = new Irrigacao();
            i.setCultivoId(cultivo.getId());
            i.setOrigem(origem);
            i.setUsuarioId(usuarioDoEvento(e.usuarioId(), cultivo, origem));
            i.setMotivo(e.motivo() != null && !e.motivo().isBlank() ? limitar(e.motivo(), 160)
                    : (origem == OrigemIrrigacao.manual ? "Ação confirmada pelo usuário" : "Regra automática"));
            i.setIniciadoEm(agora);
            i.setDuracaoPrevistaMin(e.duracaoPrevistaMin() != null ? e.duracaoPrevistaMin()
                    : cultivo.getTempoMaxAcionamentoMin());
            i.setVazaoLitrosMin(e.vazaoLMin() != null && e.vazaoLMin() > 0 ? e.vazaoLMin() : cultivo.getVazaoBombaLMin());
            i.setUmidadeInicial(e.umidade());
            irrigacaoRepository.saveAndFlush(i);
            log.info("[ESP32] {}: irrigação {} iniciada ({})", cultivo.getNome(), origem, i.getMotivo());
        } else {
            emAndamento.ifPresent(i -> {
                OffsetDateTime fim = agora;
                if (e.duracaoS() != null && e.duracaoS() >= 0) {
                    OffsetDateTime peloDispositivo = i.getIniciadoEm().plusSeconds(e.duracaoS());
                    fim = peloDispositivo.isBefore(agora) ? peloDispositivo : agora;
                }
                i.setFinalizadoEm(fim);
                i.setUmidadeFinal(e.umidade());
                irrigacaoRepository.saveAndFlush(i);
                log.info("[ESP32] {}: irrigação finalizada (motivo: {}, {} s, {} L)", cultivo.getNome(),
                        e.motivoFim(), e.duracaoS(), e.volumeL());
            });
        }
    }

    /** /status: "online" | "offline" (Last Will do firmware). */
    @Transactional
    public void processarStatus(String identificador, String status) {
        Dispositivo dispositivo = dispositivo(identificador);
        if ("online".equals(status)) {
            dispositivo.setStatus(StatusDispositivo.online);
            dispositivo.setUltimoContato(OffsetDateTime.now(clock));
        } else {
            dispositivo.setStatus(StatusDispositivo.offline);
        }
        log.info("[ESP32] {} está {}", identificador, status);
    }

    private Dispositivo dispositivo(String identificador) {
        return dispositivoRepository.findByIdentificador(identificador)
                .orElseThrow(() -> ApiException.naoEncontrado("Dispositivo não cadastrado: " + identificador));
    }

    private void fecharIrrigacaoEsquecida(Cultivo cultivo, Double umidade, OffsetDateTime agora) {
        irrigacaoRepository.findFirstByCultivoIdAndFinalizadoEmIsNull(cultivo.getId())
                .filter(i -> i.getIniciadoEm().plusSeconds(CARENCIA_SINCRONIZACAO_S).isBefore(agora))
                .ifPresent(i -> {
                    i.setFinalizadoEm(agora);
                    i.setUmidadeFinal(umidade);
                    irrigacaoRepository.save(i);
                    log.info("[ESP32] {}: irrigação aberta fechada pela telemetria (bomba desligada)", cultivo.getNome());
                });
    }

    /** Comando manual sem usuario conhecido (ex.: botao do Node-RED) fica no nome do dono da propriedade. */
    private Long usuarioDoEvento(Long usuarioId, Cultivo cultivo, OrigemIrrigacao origem) {
        if (usuarioId != null && usuarioId > 0 && usuarioRepository.existsById(usuarioId)) {
            return usuarioId;
        }
        if (origem == OrigemIrrigacao.manual) {
            return propriedadeRepository.findById(cultivo.getPropriedadeId()).map(p -> p.getUsuarioId()).orElse(null);
        }
        return null;
    }

    private static void adicionar(List<LeituraItemRequest> itens, Set<TipoSensor> tipos, TipoSensor tipo, Double valor) {
        if (valor != null && tipos.contains(tipo)) {
            itens.add(new LeituraItemRequest(null, null, tipo.name(), valor));
        }
    }

    private static String limitar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
