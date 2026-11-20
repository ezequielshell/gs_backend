package br.com.fiap.solovivo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fiap.solovivo.model.Propriedade;

public interface PropriedadeRepository extends JpaRepository<Propriedade, Long> {

    Optional<Propriedade> findFirstByUsuarioIdOrderByIdAsc(Long usuarioId);

    @Query("select p.id from Propriedade p where p.usuarioId = :usuarioId")
    List<Long> idsDoUsuario(@Param("usuarioId") Long usuarioId);
}
