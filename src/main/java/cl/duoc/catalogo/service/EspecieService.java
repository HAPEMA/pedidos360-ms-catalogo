package cl.duoc.catalogo.service;

import cl.duoc.catalogo.dto.EspecieRequest;
import cl.duoc.catalogo.dto.EspecieResponse;
import cl.duoc.catalogo.model.Especie;
import cl.duoc.catalogo.ninja.NinjaAnimal;
import cl.duoc.catalogo.ninja.NinjaClient;
import cl.duoc.catalogo.repository.EspecieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Catálogo de especies a la venta. Siempre devuelve DTOs, nunca la entidad. */
@Service
public class EspecieService {

    private final EspecieRepository repositorio;
    private final NinjaClient ninjaClient;
    private final TransactionTemplate transaccion;

    public EspecieService(EspecieRepository repositorio, NinjaClient ninjaClient, TransactionTemplate transaccion) {
        this.repositorio = repositorio;
        this.ninjaClient = ninjaClient;
        this.transaccion = transaccion;
    }

    /** El mapeo ocurre dentro de la transacción porque ubicaciones es perezosa (open-in-view=false). */
    @Transactional(readOnly = true)
    public List<EspecieResponse> listar() {
        return repositorio.findAllByOrderByIdAsc().stream().map(EspecieResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public EspecieResponse obtener(Long id) {
        return repositorio.findById(id)
                .map(EspecieResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la especie con id " + id));
    }

    /**
     * Publica una especie copiando su ficha desde API Ninjas.
     * La llamada externa se hace fuera de la transacción para no retener una conexión
     * de la BD; solo el guardado y el mapeo van dentro (TransactionTemplate).
     */
    public EspecieResponse publicar(EspecieRequest solicitud) {
        String nombreApi = solicitud.nombreApi().trim();
        if (repositorio.existsByNombreApiIgnoreCase(nombreApi)) {
            throw new EspecieDuplicadaException("La especie '" + nombreApi + "' ya está en el catálogo");
        }

        // API Ninjas busca por coincidencia parcial: exigimos el nombre exacto (sin mayúsculas).
        NinjaAnimal ficha = ninjaClient.buscar(nombreApi).stream()
                .filter(a -> a.name() != null && a.name().trim().equalsIgnoreCase(nombreApi))
                .findFirst()
                .orElseThrow(() -> new FichaNoEncontradaException(
                        "API Ninjas no tiene una ficha con el nombre exacto '" + nombreApi + "'"));

        Especie especie = new Especie(ficha.name().trim(), solicitud.nombreEs().trim(), ficha.nombreCientifico(),
                ficha.habitat(), ficha.dieta(), ficha.velocidadMaxima(), ficha.esperanzaVida(),
                ficha.ubicaciones(), solicitud.precio(), solicitud.stock());

        return transaccion.execute(estado -> EspecieResponse.desde(repositorio.save(especie)));
    }
}
