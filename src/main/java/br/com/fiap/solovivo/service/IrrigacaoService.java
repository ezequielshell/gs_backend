package br.com.fiap.solovivo.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.solovivo.dto.CultivoDtos.AcionarIrrigacaoRequest;
import br.com.fiap.solovivo.dto.CultivoDtos.CultivoResponse;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.iot.BombaGateway;
import br.com.fiap.solovivo.iot.BombaIndisponivelException;
import br.com.fiap.solovivo.iot.ComandoBomba;
import br.com.fiap.solovivo.iot.ResultadoComando;
import br.com.fiap.solovivo.iot.TopologiaIot;
import org.springframework.context.ApplicationEventPublisher;
import br.com.fiap.solovivo.model.Cultivo;
import br.com.fiap.solovivo.model.Dispositivo;
import br.com.fiap.solovivo.model.Irrigacao;
import br.com.fiap.solovivo.model.Leitura;
import br.com.fiap.solovivo.model.ModoIrrigacao;
import br.com.fiap.solovivo.model.OrigemIrrigacao;
import br.com.fiap.solovivo.repository.ConsultaRepository;
import br.com.fiap.solovivo.repository.CultivoRepository;
import br.com.fiap.solovivo.repository.DispositivoRepository;
import br.com.fiap.solovivo.repository.IrrigacaoRepository;
import br.com.fiap.solovivo.repository.LeituraRepository;
import br.com.fiap.solovivo.security.UsuarioAutenticado;

/**
 * Liga/desliga a irrigacao (manual pelo app ou automatica pelas regras).
 *
 * <p>Ordem importante (CT-08): primeiro o comando e enviado a bomba; somente se ela confirmar
 * a irrigacao e registrada no banco. Se a bomba falhar, nada e gravado e a API responde 503.
 *
 * <p>Os metodos iniciar/finalizar nao sao @Transactional de proposito: rodam dentro da transacao
 * de quem chama, e uma falha da bomba capturada pela automacao nao marca a transacao para rollback.
 */
@Service
public class IrrigacaoService {

    private static final Logger log = LoggerFactory.getLogger(IrrigacaoService.class);

    private final IrrigacaoRepository irrigacaoRepository;
    private final CultivoRepository cultivoRepository;
    private final DispositivoRepository dispositivoRepository;
    private final LeituraRepository leituraRepository;
    private final BombaGateway bombaGateway;
    private final AcessoService acessoService;
    private final ConsultaRepository consultaRepository;
    private final CultivoMapper cultivoMapper;
    private final TopologiaIot topologia;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public IrrigacaoService(IrrigacaoRepository irrigacaoRepository, CultivoRepository cultivoRepository,
                            DispositivoRepository dispositivoRepository, LeituraRepository leituraRepository,
                            BombaGateway bombaGateway, AcessoService acessoService,
                            ConsultaRepository consultaRepository, CultivoMapper cultivoMapper,
                            TopologiaIot topologia, ApplicationEventPublisher eventos, Clock clock) {
        this.topologia = topologia;
        this.eventos = eventos;
        this.irrigacaoRepository = irrigacaoRepository;
        this.cultivoRepository = cultivoRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.leituraRepository = leituraRepository;
        this.bombaGateway = bombaGateway;
        this.acessoService = acessoService;
        this.consultaRepository = consultaRepository;
        this.cultivoMapper = cultivoMapper;
        this.clock = clock;
    }

    /** POST /cultivos/{id}/irrigacao/acionar: alterna a bomba ou apenas troca o modo. */
    @Transactional
    public CultivoResponse acionar(Long cultivoId, AcionarIrrigacaoRequest request, UsuarioAutenticado usuario) {
        Cultivo cultivo = acessoService.cultivoAutorizado(cultivoId, usuario);
        boolean apenasModo = request != null && Boolean.TRUE.equals(request.apenasAlterarModo());
        String modo = request == null ? null : request.modo();
        if (modo == null && apenasModo) {
            throw ApiException.invalido("Informe o modo (manual ou automatico).");
        }

        // 1) Comando para a bomba primeiro: se nao for confirmado, nada muda (nem o modo).
        if (!apenasModo) {
            Double umidade = umidadeAtual(cultivo);
            Optional<Irrigacao> emAndamento = irrigacaoRepository.findFirstByCultivoIdAndFinalizadoEmIsNull(cultivo.getId());
            if (emAndamento.isPresent()) {
                finalizar(cultivo, emAndamento.get(), umidade);
                log.info("[Irrigação manual] {} desligada pelo usuário {}", cultivo.getNome(), usuario.id());
            } else {
                iniciar(cultivo, OrigemIrrigacao.manual, usuario.id(), "Ação confirmada pelo usuário", umidade);
                log.info("[Irrigação manual] {} ligada pelo usuário {}", cultivo.getNome(), usuario.id());
            }
        }

        // 2) Modo (Manual | Automatico). Se mudou, o ESP32 recebe a nova configuracao apos o commit.
        if (modo != null) {
            ModoIrrigacao novo = ModoIrrigacao.valueOf(modo);
            if (novo != cultivo.getModoIrrigacao()) {
                cultivo.setModoIrrigacao(novo);
                eventos.publishEvent(new ConfiguracaoCultivoAlterada(cultivo.getId()));
            }
        }
        cultivoRepository.saveAndFlush(cultivo);
        return consultaRepository.buscarStatus(cultivo.getId()).map(cultivoMapper::toResponse).orElseThrow();
    }

    /** Liga a bomba e registra a irrigacao (somente apos o comando ser confirmado). */
    public Irrigacao iniciar(Cultivo cultivo, OrigemIrrigacao origem, Long usuarioId, String motivo,
                             Double umidadeInicial) {
        ComandoBomba comando = comando(cultivo, true, usuarioId);
        ResultadoComando resultado = bombaGateway.ligar(comando); // lanca BombaIndisponivelException se nao confirmar
        if (resultado.registradoPeloDispositivo()) {
            // O ESP32 publicou o evento "inicio" e a irrigacao ja foi gravada pelo DispositivoEventosService.
            return irrigacaoRepository.findFirstByCultivoIdAndFinalizadoEmIsNull(cultivo.getId())
                    .orElseThrow(() -> new BombaIndisponivelException(
                            "O dispositivo confirmou o comando, mas o início da irrigação não foi registrado."));
        }

        Irrigacao irrigacao = new Irrigacao();
        irrigacao.setCultivoId(cultivo.getId());
        irrigacao.setUsuarioId(usuarioId);
        irrigacao.setOrigem(origem);
        irrigacao.setMotivo(motivo);
        irrigacao.setIniciadoEm(OffsetDateTime.now(clock));
        irrigacao.setDuracaoPrevistaMin(cultivo.getTempoMaxAcionamentoMin());
        irrigacao.setVazaoLitrosMin(cultivo.getVazaoBombaLMin());
        irrigacao.setUmidadeInicial(umidadeInicial);
        try {
            return irrigacaoRepository.saveAndFlush(irrigacao);
        } catch (RuntimeException e) {
            tentarDesligar(comando); // nao deixar a bomba ligada sem registro
            throw e;
        }
    }

    /** Desliga a bomba e fecha o registro (o banco calcula duracao e volume). */
    public void finalizar(Cultivo cultivo, Irrigacao irrigacao, Double umidadeFinal) {
        ResultadoComando resultado = bombaGateway.desligar(comando(cultivo, false, null));
        if (resultado.registradoPeloDispositivo()) {
            return; // o evento "fim" do ESP32 ja fechou o registro
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        irrigacao.setFinalizadoEm(agora.isBefore(irrigacao.getIniciadoEm()) ? irrigacao.getIniciadoEm() : agora);
        irrigacao.setUmidadeFinal(umidadeFinal);
        irrigacaoRepository.saveAndFlush(irrigacao);
    }

    /** Seguranca: encerra irrigacoes que passaram do tempo maximo de acionamento. */
    @Transactional
    public int encerrarIrrigacoesExcedidas() {
        int encerradas = 0;
        OffsetDateTime agora = OffsetDateTime.now(clock);
        for (Irrigacao irrigacao : irrigacaoRepository.findByFinalizadoEmIsNull()) {
            Cultivo cultivo = cultivoRepository.findById(irrigacao.getCultivoId()).orElse(null);
            if (cultivo == null) {
                continue;
            }
            int limite = irrigacao.getDuracaoPrevistaMin() != null
                    ? irrigacao.getDuracaoPrevistaMin() : cultivo.getTempoMaxAcionamentoMin();
            if (irrigacao.getIniciadoEm().plusMinutes(limite).isAfter(agora)) {
                continue;
            }
            try {
                finalizar(cultivo, irrigacao, umidadeAtual(cultivo));
                encerradas++;
                log.info("[Segurança] Irrigação de {} encerrada ao atingir o tempo máximo ({} min)",
                        cultivo.getNome(), limite);
            } catch (BombaIndisponivelException e) {
                log.warn("[Segurança] Não foi possível desligar a bomba de {}: {}", cultivo.getNome(), e.getMessage());
            }
        }
        return encerradas;
    }

    /** Ultima umidade do solo, ou null se nao houver leitura recente (nao usar dado antigo como atual). */
    public Double umidadeAtual(Cultivo cultivo) {
        if (cultivo.getSensorUmidadeId() == null) {
            return null;
        }
        OffsetDateTime limite = OffsetDateTime.now(clock).minusMinutes(cultivo.getLimiteSemLeituraMin());
        return leituraRepository.findFirstBySensorIdOrderByLidaEmDesc(cultivo.getSensorUmidadeId())
                .filter(l -> l.getLidaEm().isAfter(limite))
                .map(Leitura::getValor)
                .orElse(null);
    }

    private ComandoBomba comando(Cultivo cultivo, boolean exigirDispositivo, Long usuarioId) {
        Dispositivo dispositivo = cultivo.getDispositivoBombaId() == null ? null
                : dispositivoRepository.findById(cultivo.getDispositivoBombaId()).orElse(null);
        if (dispositivo == null && exigirDispositivo) {
            throw ApiException.regraNegocio("Este cultivo não tem controlador de irrigação (bomba) vinculado.");
        }
        return new ComandoBomba(dispositivo == null ? null : dispositivo.getIdentificador(),
                cultivo.getId(), cultivo.getTempoMaxAcionamentoMin(), usuarioId,
                topologia.controladoPorDispositivo(cultivo));
    }

    private void tentarDesligar(ComandoBomba comando) {
        try {
            bombaGateway.desligar(comando);
        } catch (RuntimeException e) {
            log.error("Falha ao desligar a bomba apos erro de registro: {}", e.getMessage());
        }
    }
}
