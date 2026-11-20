package br.com.fiap.solovivo.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fiap.solovivo.model.Irrigacao;

public interface IrrigacaoRepository extends JpaRepository<Irrigacao, Long> {

    /** Irrigacao em andamento (no maximo uma por cultivo - indice uq_irrigacao_em_andamento). */
    Optional<Irrigacao> findFirstByCultivoIdAndFinalizadoEmIsNull(Long cultivoId);

    Optional<Irrigacao> findFirstByCultivoIdAndFinalizadoEmIsNotNullOrderByFinalizadoEmDesc(Long cultivoId);

    List<Irrigacao> findByFinalizadoEmIsNull();

    List<Irrigacao> findTop50ByCultivoIdAndIniciadoEmGreaterThanEqualOrderByIniciadoEmDesc(
            Long cultivoId, OffsetDateTime desde);
}
