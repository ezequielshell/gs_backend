package br.com.fiap.solovivo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fiap.solovivo.model.Dispositivo;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    Optional<Dispositivo> findByTokenApi(String tokenApi);

    Optional<Dispositivo> findByIdentificador(String identificador);

    Optional<Dispositivo> findFirstByPropriedadeIdOrderByIdAsc(Long propriedadeId);
}
