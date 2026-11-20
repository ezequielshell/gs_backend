package br.com.fiap.solovivo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fiap.solovivo.model.Alerta;
import br.com.fiap.solovivo.model.TipoAlerta;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    Optional<Alerta> findFirstByCultivoIdAndTipoAndResolvidoEmIsNull(Long cultivoId, TipoAlerta tipo);

    List<Alerta> findByCultivoIdAndResolvidoEmIsNull(Long cultivoId);
}
