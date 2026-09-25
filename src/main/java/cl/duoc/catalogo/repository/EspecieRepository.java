package cl.duoc.catalogo.repository;

import cl.duoc.catalogo.model.Especie;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EspecieRepository extends JpaRepository<Especie, Long> {

    boolean existsByNombreApiIgnoreCase(String nombreApi);

    /** Trae las ubicaciones en la misma consulta para evitar N+1 al listar. */
    @EntityGraph(attributePaths = "ubicaciones")
    List<Especie> findAllByOrderByIdAsc();

    /** Cruza los nombres devueltos por API Ninjas con el catálogo (sin distinguir mayúsculas). */
    @Query("select e from Especie e where lower(e.nombreApi) in :nombres")
    List<Especie> buscarPorNombresApi(@Param("nombres") Collection<String> nombresEnMinusculas);
}
