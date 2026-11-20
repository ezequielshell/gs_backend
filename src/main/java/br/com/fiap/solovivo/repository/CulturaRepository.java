package br.com.fiap.solovivo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fiap.solovivo.model.Cultura;

public interface CulturaRepository extends JpaRepository<Cultura, Long> {

    /** Culturas visiveis ao usuario: as padrao do sistema + as que ele cadastrou. */
    @Query("""
            select c from Cultura c
             where c.usuarioId is null or c.usuarioId = :usuarioId
             order by c.nome
            """)
    List<Cultura> listarVisiveis(@Param("usuarioId") Long usuarioId);

    @Query("""
            select c from Cultura c
             where lower(c.nome) = lower(:nome)
               and (c.usuarioId is null or c.usuarioId = :usuarioId)
             order by c.usuarioId nulls first
            """)
    List<Cultura> buscarVisivelPorNome(@Param("nome") String nome, @Param("usuarioId") Long usuarioId);

    boolean existsByUsuarioIdIsNullAndNomeIgnoreCase(String nome);
}
