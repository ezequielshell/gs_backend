package br.com.fiap.solovivo.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.solovivo.dto.GeralDtos.AlertaResponse;
import br.com.fiap.solovivo.exception.ApiException;
import br.com.fiap.solovivo.model.Alerta;
import br.com.fiap.solovivo.model.Cultivo;
import br.com.fiap.solovivo.model.TipoAlerta;
import br.com.fiap.solovivo.repository.AlertaRepository;
import br.com.fiap.solovivo.repository.ConsultaRepository;
import br.com.fiap.solovivo.repository.ConsultaRepository.AlertaRow;
import br.com.fiap.solovivo.repository.CultivoRepository;
import br.com.fiap.solovivo.security.UsuarioAutenticado;
import br.com.fiap.solovivo.service.RegraIrrigacao.AlertaPlanejado;

/** Alertas: abertura/resolucao automatica pelas regras e consulta pelo app (Modal 2). */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);

    private final AlertaRepository alertaRepository;
    private final CultivoRepository cultivoRepository;
    private final ConsultaRepository consultaRepository;
    private final AcessoService acessoService;
    private final Formatador formatador;
    private final Clock clock;

    public AlertaService(AlertaRepository alertaRepository, CultivoRepository cultivoRepository,
                         ConsultaRepository consultaRepository, AcessoService acessoService,
                         Formatador formatador, Clock clock) {
        this.alertaRepository = alertaRepository;
        this.cultivoRepository = cultivoRepository;
        this.consultaRepository = consultaRepository;
        this.acessoService = acessoService;
        this.formatador = formatador;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AlertaResponse> listar(String status, UsuarioAutenticado usuario) {
        boolean ativos = switch (status == null ? "ativos" : status) {
            case "ativos" -> true;
            case "resolvidos" -> false;
            default -> throw ApiException.invalido("Status inválido. Use ativos ou resolvidos.");
        };
        return consultaRepository.listarAlertas(acessoService.propriedadesVisiveis(usuario), ativos)
                .stream().map(this::toResponse).toList();
    }

    /** Resolve manualmente um alerta (o produtor confirma que conferiu o cultivo). */
    @Transactional
    public AlertaResponse resolverManualmente(Long id, UsuarioAutenticado usuario) {
        Alerta alerta = alertaRepository.findById(id)
                .filter(a -> acessoService.podeAcessarPropriedade(usuario, a.getPropriedadeId()))
                .orElseThrow(() -> ApiException.naoEncontrado("Alerta não encontrado."));
        if (alerta.getResolvidoEm() == null) {
            OffsetDateTime agora = OffsetDateTime.now(clock);
            alerta.setResolvidoEm(agora);
            if (alerta.getLidoEm() == null) {
                alerta.setLidoEm(agora);
            }
            alertaRepository.saveAndFlush(alerta);
        }
        return consultaRepository.buscarAlerta(id).map(this::toResponse).orElseThrow();
    }

    /** Abre os alertas planejados que ainda nao estao ativos. Retorna os tipos abertos. */
    public List<String> abrir(Cultivo cultivo, Long sensorId, List<AlertaPlanejado> planejados, Double valor) {
        List<String> abertos = new ArrayList<>();
        for (AlertaPlanejado p : planejados) {
            if (alertaRepository.findFirstByCultivoIdAndTipoAndResolvidoEmIsNull(cultivo.getId(), p.tipo()).isPresent()) {
                continue;
            }
            Alerta a = new Alerta();
            a.setPropriedadeId(cultivo.getPropriedadeId());
            a.setCultivoId(cultivo.getId());
            a.setSensorId(sensorId);
            a.setTipo(p.tipo());
            a.setSeveridade(p.severidade());
            a.setMensagem(p.mensagem());
            a.setAcaoSugerida(RegraIrrigacao.ACAO_SUGERIDA);
            a.setValor(valor);
            a.setCriadoEm(OffsetDateTime.now(clock));
            alertaRepository.save(a);
            abertos.add(p.tipo().name());
            log.info("[Alerta] {} aberto para {} (valor={})", p.tipo(), cultivo.getNome(), valor);
        }
        return abertos;
    }

    /** Resolve os alertas ativos do cultivo com os tipos informados. Retorna os tipos resolvidos. */
    public List<String> resolver(Long cultivoId, Collection<TipoAlerta> tipos) {
        List<String> resolvidos = new ArrayList<>();
        OffsetDateTime agora = OffsetDateTime.now(clock);
        for (Alerta a : alertaRepository.findByCultivoIdAndResolvidoEmIsNull(cultivoId)) {
            if (tipos.contains(a.getTipo())) {
                a.setResolvidoEm(agora);
                alertaRepository.save(a);
                resolvidos.add(a.getTipo().name());
            }
        }
        return resolvidos;
    }

    /** Rotina periodica: abre "Sem leitura do sensor" para cultivos sem dados recentes. */
    @Transactional
    public int verificarSensoresSemLeitura() {
        int abertos = 0;
        for (var row : consultaRepository.listarStatus(null)) {
            if (!"sem_leitura".equals(row.status()) || row.sensorId() == null) {
                continue;
            }
            Cultivo cultivo = cultivoRepository.findById(row.cultivoId()).orElse(null);
            if (cultivo != null) {
                abertos += abrir(cultivo, row.sensorId(), List.of(RegraIrrigacao.alertaSemLeitura()), null).size();
            }
        }
        return abertos;
    }

    private AlertaResponse toResponse(AlertaRow r) {
        return new AlertaResponse(
                String.valueOf(r.id()),
                r.cultivoId() == null ? null : String.valueOf(r.cultivoId()),
                r.cultivoNome(),
                r.tipo(),
                formatador.horarioAlerta(r.criadoEm()),
                r.severidade(),
                r.resolvidoEm() == null ? "ativos" : "resolvidos",
                r.mensagem(),
                r.acaoSugerida() != null ? r.acaoSugerida() : RegraIrrigacao.ACAO_SUGERIDA,
                r.valor(),
                r.criadoEm().toString());
    }
}
