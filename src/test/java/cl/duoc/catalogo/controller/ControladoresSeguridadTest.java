package cl.duoc.catalogo.controller;

import cl.duoc.catalogo.model.Especie;
import cl.duoc.catalogo.ninja.NinjaAnimal;
import cl.duoc.catalogo.ninja.NinjaClient;
import cl.duoc.catalogo.ninja.NinjaNoDisponibleException;
import cl.duoc.catalogo.repository.EspecieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static cl.duoc.catalogo.config.SeguridadConfig.SCOPE_READ;
import static cl.duoc.catalogo.config.SeguridadConfig.SCOPE_WRITE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de extremo a extremo de la capa web (seguridad + controladores + BD H2).
 * API Ninjas se reemplaza por un mock: ningún test sale a internet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ControladoresSeguridadTest {

    private static final String LEON_JSON = """
            {"nombreApi": "lion", "nombreEs": "León", "precio": 1500000, "stock": 3}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EspecieRepository repositorio;

    @MockitoBean
    private NinjaClient ninjaClient;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
    }

    // ---------- Autenticación y autorización ----------

    @Test
    void sinTokenResponde401EnJson() throws Exception {
        mvc.perform(get("/api/especies"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void tokenSinScopeResponde403EnJson() throws Exception {
        mvc.perform(get("/api/especies").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getConScopeReadResponde200() throws Exception {
        repositorio.save(especie("Lion", "León"));

        mvc.perform(get("/api/especies").with(lector()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombreApi").value("Lion"))
                .andExpect(jsonPath("$[0].ubicaciones[0]").value("Africa"));
    }

    @Test
    void postSinRolAdminResponde403() throws Exception {
        mvc.perform(post("/api/especies").with(jwt().authorities(new SimpleGrantedAuthority(SCOPE_WRITE)))
                        .contentType(MediaType.APPLICATION_JSON).content(LEON_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(ninjaClient);
    }

    @Test
    void postConAdminSinScopeWriteResponde403() throws Exception {
        mvc.perform(post("/api/especies")
                        .with(jwt().authorities(new SimpleGrantedAuthority(SCOPE_READ),
                                new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(LEON_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void metodoNoDeclaradoSeDeniega() throws Exception {
        mvc.perform(delete("/api/especies/1").with(administrador()))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void preflightDelFrontSinToken() throws Exception {
        mvc.perform(options("/api/especies")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void preflightDeOtroOrigenSeRechaza() throws Exception {
        mvc.perform(options("/api/especies")
                        .header("Origin", "https://sitio-malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    // ---------- Especies ----------

    @Test
    void postConAdminYWriteResponde201() throws Exception {
        when(ninjaClient.buscar("lion")).thenReturn(List.of(leon("Lion"), leon("Sea Lion")));

        mvc.perform(post("/api/especies").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content(LEON_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/especies/")))
                // Se guarda el name exacto de API Ninjas, no lo que escribió el administrador.
                .andExpect(jsonPath("$.nombreApi").value("Lion"))
                .andExpect(jsonPath("$.nombreEs").value("León"))
                .andExpect(jsonPath("$.nombreCientifico").value("Panthera leo"))
                .andExpect(jsonPath("$.ubicaciones[0]").value("Africa"))
                .andExpect(jsonPath("$.precio").value(1500000))
                .andExpect(jsonPath("$.stock").value(3));
    }

    @Test
    void idInexistenteResponde404EnJson() throws Exception {
        mvc.perform(get("/api/especies/999999").with(lector()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void obtenerPorIdResponde200() throws Exception {
        Long id = repositorio.save(especie("Lion", "León")).getId();

        mvc.perform(get("/api/especies/" + id).with(lector()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.ubicaciones[0]").value("Africa"));
    }

    @Test
    void bodyInvalidoResponde400ConDetalles() throws Exception {
        mvc.perform(post("/api/especies").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreApi\": \"\", \"precio\": 0, \"stock\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detalles.nombreApi").exists())
                .andExpect(jsonPath("$.detalles.nombreEs").exists())
                .andExpect(jsonPath("$.detalles.precio").exists())
                .andExpect(jsonPath("$.detalles.stock").exists());
        verifyNoInteractions(ninjaClient);
    }

    @Test
    void jsonMalformadoResponde400() throws Exception {
        mvc.perform(post("/api/especies").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void nombreDuplicadoResponde409() throws Exception {
        repositorio.save(especie("Lion", "León"));

        mvc.perform(post("/api/especies").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content(LEON_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        verifyNoInteractions(ninjaClient);
    }

    @Test
    void sinFichaExactaResponde422() throws Exception {
        when(ninjaClient.buscar("lion")).thenReturn(List.of(leon("Sea Lion")));

        mvc.perform(post("/api/especies").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content(LEON_JSON))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));
    }

    // ---------- Búsqueda en API Ninjas ----------

    @Test
    void buscarCruzaConElCatalogo() throws Exception {
        Long id = repositorio.save(especie("Lion", "León")).getId();
        when(ninjaClient.buscar("lion")).thenReturn(List.of(leon("Lion"), leon("Sea Lion")));

        mvc.perform(get("/api/animales/buscar").param("nombre", "lion").with(lector()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombre").value("Lion"))
                .andExpect(jsonPath("$[0].nombreCientifico").value("Panthera leo"))
                .andExpect(jsonPath("$[0].enVenta").value(true))
                .andExpect(jsonPath("$[0].especieId").value(id))
                .andExpect(jsonPath("$[1].enVenta").value(false))
                .andExpect(jsonPath("$[1].especieId").value(nullValue()));
    }

    @Test
    void buscarSinResultadosDevuelveListaVacia() throws Exception {
        when(ninjaClient.buscar(anyString())).thenReturn(List.of());

        mvc.perform(get("/api/animales/buscar").param("nombre", "xyz").with(lector()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void buscarConNombreCortoResponde400() throws Exception {
        mvc.perform(get("/api/animales/buscar").param("nombre", "a").with(lector()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(ninjaClient);
    }

    @Test
    void buscarCuandoNinjaFallaResponde502SinFiltrarDetalles() throws Exception {
        when(ninjaClient.buscar(anyString())).thenThrow(
                new NinjaNoDisponibleException(new ResourceAccessException("detalle interno secreto")));

        mvc.perform(get("/api/animales/buscar").param("nombre", "lion").with(lector()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(content().string(not(containsString("secreto"))));
    }

    // ---------- Utilidades ----------

    private static JwtRequestPostProcessor lector() {
        return jwt().authorities(new SimpleGrantedAuthority(SCOPE_READ));
    }

    private static JwtRequestPostProcessor administrador() {
        return jwt().jwt(j -> j.claim("username", "admin"))
                .authorities(new SimpleGrantedAuthority(SCOPE_READ), new SimpleGrantedAuthority(SCOPE_WRITE),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static NinjaAnimal leon(String nombre) {
        return new NinjaAnimal(nombre, new NinjaAnimal.Taxonomia("Panthera leo"), List.of("Africa"),
                new NinjaAnimal.Caracteristicas("Grassland", "Carnivore", "50 mph", "8 - 15 years"));
    }

    private static Especie especie(String nombreApi, String nombreEs) {
        return new Especie(nombreApi, nombreEs, "Panthera leo", "Grassland", "Carnivore", "50 mph",
                "8 - 15 years", List.of("Africa"), 1500000, 3);
    }
}
