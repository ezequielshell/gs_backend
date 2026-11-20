package br.com.fiap.solovivo.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.solovivo.dto.CultivoDtos.AcionamentoResponse;
import br.com.fiap.solovivo.dto.CultivoDtos.CreateCultivoRequest;
import br.com.fiap.solovivo.dto.CultivoDtos.CultivoResponse;
import br.com.fiap.solovivo.dto.CultivoDtos.HistoricoResponse;
import br.com.fiap.solovivo.dto.CultivoDtos.UpdateLimitesRequest;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.model.Cultivo;
import br.com.fiap.solovivo.model.Cultura;
import br.com.fiap.solovivo.model.Dispositivo;
import br.com.fiap.solovivo.model.Irrigacao;
import br.com.fiap.solovivo.model.ModoIrrigacao;
import br.com.fiap.solovivo.model.OrigemIrrigacao;
import br.com.fiap.solovivo.model.Propriedade;
import br.com.fiap.solovivo.model.Sensor;
import br.com.fiap.solovivo.model.TipoAlerta;
import br.com.fiap.solovivo.model.TipoSensor;
import br.com.fiap.solovivo.repository.ConsultaRepository;
import br.com.fiap.solovivo.repository.ConsultaRepository.ResumoHistorico;
import br.com.fiap.solovivo.repository.CultivoRepository;
import br.com.fiap.solovivo.repository.DispositivoRepository;
import br.com.fiap.solovivo.repository.IrrigacaoRepository;
import br.com.fiap.solovivo.repository.PropriedadeRepository;
import br.com.fiap.solovivo.repository.SensorRepository;
import br.com.fiap.solovivo.security.UsuarioAutenticado;

/** Cadastro, limites, exclusao e historico dos cultivos (Telas 3 a 7). */
@Service
public class CultivoService {

    private static final int TEMPO_MAXIMO_PADRAO_MIN = 15;

    private final CultivoRepository cultivoRepository;
    private final PropriedadeRepository propriedadeRepository;
    private final SensorRepository sensorRepository;
    private final DispositivoRepository dispositivoRepository;
    private final IrrigacaoRepository irrigacaoRepository;
    private final ConsultaRepository consultaRepository;
    private final CulturaService culturaService;
    private final IrrigacaoService irrigacaoService;
    private final AlertaService alertaService;
    private final AcessoService acessoService;
    private final CultivoMapper cultivoMapper;
    private final Formatador formatador;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public CultivoService(CultivoRepository cultivoRepository, PropriedadeRepository propriedadeRepository,
                          SensorRepository sensorRepository, DispositivoRepository dispositivoRepository,
                          IrrigacaoRepository irrigacaoRepository, ConsultaRepository consultaRepository,
                          CulturaService culturaService, IrrigacaoService irrigacaoService,
                          AlertaService alertaService, AcessoService acessoService, CultivoMapper cultivoMapper,
                          Formatador formatador, ApplicationEventPublisher eventos, Clock clock) {
        this.eventos = eventos;
        this.cultivoRepository = cultivoRepository;
        this.propriedadeRepository = propriedadeRepository;
        this.sensorRepository = sensorRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.irrigacaoRepository = irrigacaoRepository;
        this.consultaRepository = consultaRepository;
        this.culturaService = culturaService;
        this.irrigacaoService = irrigacaoService;
        this.alertaService = alertaService;
        this.acessoService = acessoService;
        this.cultivoMapper = cultivoMapper;
        this.formatador = formatador;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CultivoResponse> listar(UsuarioAutenticado usuario) {
        return consultaRepository.listarStatus(acessoService.propriedadesVisiveis(usuario))
                .stream().map(cultivoMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CultivoResponse buscar(Long id, UsuarioAutenticado usuario) {
        acessoService.cultivoAutorizado(id, usuario);
        return resposta(id);
    }

    /** CT-03: cadastro de novo cultivo (ex.: Canteiro 4, Alface, 30% a 60%, 1,2 L/min). */
    @Transactional
    public CultivoResponse criar(CreateCultivoRequest req, UsuarioAutenticado usuario) {
        Propriedade propriedade = propriedadeRepository.findFirstByUsuarioIdOrderByIdAsc(usuario.id())
                .orElseThrow(() -> ApiException.regraNegocio("Cadastre uma propriedade antes de adicionar cultivos."));

        String nome = req.nome().trim();
        if (cultivoRepository.existsByPropriedadeIdAndNomeIgnoreCaseAndAtivoTrue(propriedade.getId(), nome)) {
            throw ApiException.conflito("Já existe um cultivo chamado '" + nome + "' nesta propriedade.");
        }

        Cultura cultura = culturaService.resolverOuCriar(req.cultura().trim(), req.umidadeMin(), req.umidadeMax(),
                usuario.id());
        Sensor sensor = resolverSensor(req.sensorId(), req.sensor(), propriedade.getId());

        Cultivo cultivo = new Cultivo();
        cultivo.setPropriedadeId(propriedade.getId());
        cultivo.setCulturaId(cultura.getId());
        cultivo.setNome(nome);
        cultivo.setSensorUmidadeId(sensor == null ? null : sensor.getId());
        // A bomba fica no mesmo ESP32 do sensor; sem sensor, usa o primeiro dispositivo da propriedade.
        cultivo.setDispositivoBombaId(sensor != null ? sensor.getDispositivoId()
                : dispositivoRepository.findFirstByPropriedadeIdOrderByIdAsc(propriedade.getId())
                        .map(Dispositivo::getId).orElse(null));
        cultivo.setUmidadeMin(req.umidadeMin());
        cultivo.setUmidadeMax(req.umidadeMax());
        cultivo.setVazaoBombaLMin(req.vazaoLMin());
        cultivo.setTempoMaxAcionamentoMin(req.tempoMaximoMin() != null ? req.tempoMaximoMin() : TEMPO_MAXIMO_PADRAO_MIN);
        cultivo.setModoIrrigacao(ModoIrrigacao.automatico);
        cultivo.setIrrigacaoAutomaticaAtiva(true);
        cultivoRepository.saveAndFlush(cultivo);
        return resposta(cultivo.getId());
    }

    /** Tela 6 - Limites. */
    @Transactional
    public CultivoResponse atualizarLimites(Long id, UpdateLimitesRequest req, UsuarioAutenticado usuario) {
        Cultivo cultivo = acessoService.cultivoAutorizado(id, usuario);
        cultivo.setUmidadeMin(req.umidadeMin());
        cultivo.setUmidadeMax(req.umidadeMax());
        cultivo.setTempoMaxAcionamentoMin(req.tempoMaximoMin());
        cultivo.setVazaoBombaLMin(req.vazaoLMin());
        cultivo.setIrrigacaoAutomaticaAtiva(req.irrigacaoAutomaticaAtivada());
        cultivoRepository.saveAndFlush(cultivo);
        eventos.publishEvent(new ConfiguracaoCultivoAlterada(id)); // ESP32 recebe os novos limites (MQTT)
        return resposta(id);
    }

    /**
     * Exclusao logica (ativo = false): o historico de irrigacoes e leituras e preservado.
     * Por seguranca, se a bomba estiver ligada ela e desligada antes.
     */
    @Transactional
    public void excluir(Long id, UsuarioAutenticado usuario) {
        Cultivo cultivo = acessoService.cultivoAutorizado(id, usuario);
        irrigacaoRepository.findFirstByCultivoIdAndFinalizadoEmIsNull(id)
                .ifPresent(i -> irrigacaoService.finalizar(cultivo, i, irrigacaoService.umidadeAtual(cultivo)));
        alertaService.resolver(id, EnumSet.allOf(TipoAlerta.class));
        cultivo.setAtivo(false);
        cultivoRepository.saveAndFlush(cultivo);
    }

    /** Tela 7 - Historico: periodo = hoje | 7d | 30d. */
    @Transactional(readOnly = true)
    public HistoricoResponse historico(Long id, String periodo, UsuarioAutenticado usuario) {
        acessoService.cultivoAutorizado(id, usuario);
        OffsetDateTime agora = OffsetDateTime.now(clock);
        String p = periodo == null ? "7d" : periodo;
        OffsetDateTime desde = switch (p) {
            case "hoje" -> formatador.inicioDoDia();
            case "7d" -> agora.minusDays(7);
            case "30d" -> agora.minusDays(30);
            default -> throw ApiException.invalido("Período inválido. Use hoje, 7d ou 30d.");
        };

        ResumoHistorico resumo = consultaRepository.resumoHistorico(id, desde);
        List<AcionamentoResponse> acionamentos = irrigacaoRepository
                .findTop50ByCultivoIdAndIniciadoEmGreaterThanEqualOrderByIniciadoEmDesc(id, desde)
                .stream().map(i -> toAcionamento(i, agora)).toList();

        return new HistoricoResponse(p, resumo.totalIrrigacoes(),
                Math.round(resumo.consumoEstimadoLitros() * 10.0) / 10.0,
                Formatador.duracao(resumo.tempoForaFaixaSegundos()), resumo.tempoForaFaixaSegundos(),
                acionamentos);
    }

    public CultivoResponse resposta(Long cultivoId) {
        return consultaRepository.buscarStatus(cultivoId).map(cultivoMapper::toResponse)
                .orElseThrow(() -> ApiException.naoEncontrado("Cultivo não encontrado."));
    }

    /** "Sensor do solo 01" <-> "Sensor de solo 01" (o app usa "do", o banco usa "de"). */
    public static String alternarPreposicao(String nome) {
        if (nome.matches("(?i).*\\bdo\\b.*")) {
            return nome.replaceAll("(?i)\\bdo\\b", "de");
        }
        return nome.replaceAll("(?i)\\bde\\b", "do");
    }

    private Sensor resolverSensor(Long sensorId, String nomeSensor, Long propriedadeId) {
        if (sensorId != null) {
            Sensor sensor = sensorRepository.findById(sensorId).filter(Sensor::isAtivo)
                    .filter(s -> sensorRepository.propriedadeDoSensor(s.getId()).map(propriedadeId::equals).orElse(false))
                    .orElseThrow(() -> ApiException.invalido("Sensor não encontrado nesta propriedade."));
            if (sensor.getTipo() != TipoSensor.umidade_solo) {
                throw ApiException.invalido("O sensor vinculado deve ser de umidade do solo.");
            }
            return sensor;
        }
        if (nomeSensor == null || nomeSensor.isBlank()) {
            return null;
        }
        String nome = nomeSensor.trim();
        List<Long> propriedades = List.of(propriedadeId);
        List<Sensor> encontrados = sensorRepository.buscarPorNome(propriedades, TipoSensor.umidade_solo, nome);
        if (encontrados.isEmpty()) {
            encontrados = sensorRepository.buscarPorNome(propriedades, TipoSensor.umidade_solo, alternarPreposicao(nome));
        }
        if (encontrados.isEmpty()) {
            throw ApiException.invalido("Sensor de umidade do solo não encontrado: '" + nome + "'.");
        }
        return encontrados.get(0);
    }

    private AcionamentoResponse toAcionamento(Irrigacao i, OffsetDateTime agora) {
        boolean emAndamento = i.getFinalizadoEm() == null;
        long segundos = i.getDuracaoSegundos() != null ? i.getDuracaoSegundos()
                : Duration.between(i.getIniciadoEm(), emAndamento ? agora : i.getFinalizadoEm()).toSeconds();
        String descricao = i.getMotivo() != null ? i.getMotivo()
                : (i.getOrigem() == OrigemIrrigacao.manual ? "Acionamento manual pelo usuário" : "Regra automática");
        return new AcionamentoResponse(
                String.valueOf(i.getId()),
                i.getOrigem() == OrigemIrrigacao.automatica ? "automatico" : "manual",
                descricao,
                formatador.dataHoraRelativa(i.getIniciadoEm()),
                (int) Math.round(segundos / 60.0),
                emAndamento,
                i.getVolumeLitros());
    }
}
