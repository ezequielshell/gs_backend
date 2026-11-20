package br.com.fiap.solovivo.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fiap.solovivo.model.Sensor;
import br.com.fiap.solovivo.model.TipoSensor;

public interface SensorRepository extends JpaRepository<Sensor, Long> {

    List<Sensor> findByDispositivoIdAndAtivoTrue(Long dispositivoId);

    Optional<Sensor> findFirstByDispositivoIdAndTipoAndAtivoTrueOrderByIdAsc(Long dispositivoId, TipoSensor tipo);

    @Query("""
            select s from Sensor s, Dispositivo d
             where d.id = s.dispositivoId
               and d.propriedadeId in :propriedades
               and s.tipo = :tipo
               and s.ativo = true
               and lower(s.nome) = lower(:nome)
            """)
    List<Sensor> buscarPorNome(@Param("propriedades") Collection<Long> propriedades,
                               @Param("tipo") TipoSensor tipo,
                               @Param("nome") String nome);

    @Query("select d.propriedadeId from Sensor s, Dispositivo d where d.id = s.dispositivoId and s.id = :sensorId")
    Optional<Long> propriedadeDoSensor(@Param("sensorId") Long sensorId);
}
