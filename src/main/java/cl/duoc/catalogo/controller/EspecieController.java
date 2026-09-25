package cl.duoc.catalogo.controller;

import cl.duoc.catalogo.dto.EspecieRequest;
import cl.duoc.catalogo.dto.EspecieResponse;
import cl.duoc.catalogo.service.EspecieService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/especies")
public class EspecieController {

    private final EspecieService servicio;

    public EspecieController(EspecieService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<EspecieResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    public EspecieResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** La autorización (scope write + rol ADMIN) se aplica en SeguridadConfig. */
    @PostMapping
    public ResponseEntity<EspecieResponse> publicar(@Valid @RequestBody EspecieRequest solicitud) {
        EspecieResponse creada = servicio.publicar(solicitud);
        // Location relativa: detrás de API Gateway la URL absoluta revelaría el host interno del EC2.
        return ResponseEntity.created(URI.create("/api/especies/" + creada.id())).body(creada);
    }
}
