package cl.duoc.catalogo.service;

import cl.duoc.catalogo.dto.FichaResponse;
import cl.duoc.catalogo.model.Especie;
import cl.duoc.catalogo.ninja.NinjaAnimal;
import cl.duoc.catalogo.ninja.NinjaClient;
import cl.duoc.catalogo.repository.EspecieRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Búsqueda de fichas en API Ninjas cruzadas con el catálogo local. */
@Service
public class AnimalService {

    static final int LARGO_MINIMO = 2;

    private final NinjaClient ninjaClient;
    private final EspecieRepository repositorio;

    public AnimalService(NinjaClient ninjaClient, EspecieRepository repositorio) {
        this.ninjaClient = ninjaClient;
        this.repositorio = repositorio;
    }

    /**
     * Sin @Transactional a propósito: no se retiene una conexión a la BD mientras
     * se espera a API Ninjas. La consulta al repositorio abre su propia transacción
     * y solo se leen id y nombreApi (no la colección perezosa).
     */
    public List<FichaResponse> buscar(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.length() < LARGO_MINIMO) {
            throw new SolicitudInvalidaException(
                    "El parámetro 'nombre' debe tener al menos " + LARGO_MINIMO + " caracteres");
        }

        List<NinjaAnimal> animales = ninjaClient.buscar(limpio).stream()
                .filter(a -> a.name() != null && !a.name().isBlank())
                .toList();
        if (animales.isEmpty()) {
            return List.of();
        }

        List<String> nombres = animales.stream().map(a -> clave(a.name())).distinct().toList();
        Map<String, Long> enCatalogo = repositorio.buscarPorNombresApi(nombres).stream()
                .collect(Collectors.toMap(e -> clave(e.getNombreApi()), Especie::getId, (a, b) -> a));

        return animales.stream().map(a -> aFicha(a, enCatalogo.get(clave(a.name())))).toList();
    }

    private static FichaResponse aFicha(NinjaAnimal a, Long especieId) {
        return new FichaResponse(a.name(), a.nombreCientifico(), a.habitat(), a.dieta(),
                a.velocidadMaxima(), a.esperanzaVida(), a.ubicaciones(), especieId, especieId != null);
    }

    static String clave(String nombre) {
        return Objects.requireNonNull(nombre).trim().toLowerCase(Locale.ROOT);
    }
}
