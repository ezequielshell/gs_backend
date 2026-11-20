package br.com.fiap.solovivo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fiap.solovivo.model.Cultivo;

public interface CultivoRepository extends JpaRepository<Cultivo, Long> {

    Optional<Cultivo> findByIdAndAtivoTrue(Long id);

    List<Cultivo> findBySensorUmidadeIdAndAtivoTrue(Long sensorId);

    boolean existsByPropriedadeIdAndNomeIgnoreCaseAndAtivoTrue(Long propriedadeId, String nome);

    /** Cultivos cujo sensor de solo esta ligado ao dispositivo (para alertas de temperatura). */
    @Query("""
            select c from Cultivo c, Sensor s
             where s.id = c.sensorUmidadeId
               and s.dispositivoId = :dispositivoId
               and c.ativo = true
            """)
    List<Cultivo> buscarAtivosPorDispositivo(@Param("dispositivoId") Long dispositivoId);
}
