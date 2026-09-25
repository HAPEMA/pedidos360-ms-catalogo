package cl.duoc.catalogo.controller;

import cl.duoc.catalogo.dto.FichaResponse;
import cl.duoc.catalogo.service.AnimalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** BFF de API Ninjas: el navegador nunca llama a API Ninjas ni ve la API key. */
@RestController
@RequestMapping("/api/animales")
public class AnimalController {

    private final AnimalService servicio;

    public AnimalController(AnimalService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/buscar")
    public List<FichaResponse> buscar(@RequestParam(required = false) String nombre) {
        return servicio.buscar(nombre);
    }
}
