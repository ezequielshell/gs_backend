package br.com.fiap.solovivo.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fiap.solovivo.model.Leitura;

public interface LeituraRepository extends JpaRepository<Leitura, Long> {

    Optional<Leitura> findFirstBySensorIdOrderByLidaEmDesc(Long sensorId);

    List<Leitura> findBySensorIdAndLidaEmGreaterThanEqualOrderByLidaEmAsc(Long sensorId, OffsetDateTime desde);
}
