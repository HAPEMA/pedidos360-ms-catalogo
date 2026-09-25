package cl.duoc.catalogo.ninja;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Sin internet: MockRestServiceServer intercepta las llamadas del RestClient. */
class NinjaClientTest {

    private static final String BASE = "https://ninja.test/v1";
    private static final String CLAVE = "clave-de-prueba";

    private MockRestServiceServer servidor;
    private NinjaClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new NinjaClient(builder, new NinjaProperties(CLAVE, BASE));
    }

    @Test
    void mapeaLosCamposDeLaFicha() {
        String json = """
                [{
                  "name": "Lion",
                  "taxonomy": {"kingdom": "Animalia", "scientific_name": "Panthera leo"},
                  "locations": ["Africa", "Asia"],
                  "characteristics": {
                    "habitat": "Open woodland, scrub, grassland",
                    "diet": "Carnivore",
                    "top_speed": "50 mph",
                    "lifespan": "8 - 15 years",
                    "color": "Brown"
                  }
                }]
                """;
        servidor.expect(requestTo(BASE + "/animals?name=lion"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", CLAVE))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<NinjaAnimal> resultado = cliente.buscar("lion");

        servidor.verify();
        assertThat(resultado).hasSize(1);
        NinjaAnimal leon = resultado.get(0);
        assertThat(leon.name()).isEqualTo("Lion");
        assertThat(leon.nombreCientifico()).isEqualTo("Panthera leo");
        assertThat(leon.habitat()).isEqualTo("Open woodland, scrub, grassland");
        assertThat(leon.dieta()).isEqualTo("Carnivore");
        assertThat(leon.velocidadMaxima()).isEqualTo("50 mph");
        assertThat(leon.esperanzaVida()).isEqualTo("8 - 15 years");
        assertThat(leon.ubicaciones()).containsExactly("Africa", "Asia");
    }

    @Test
    void toleraCamposFaltantes() {
        servidor.expect(requestTo(BASE + "/animals?name=snow%20leopard"))
                .andRespond(withSuccess("[{\"name\": \"Snow Leopard\"}]", MediaType.APPLICATION_JSON));

        NinjaAnimal animal = cliente.buscar("snow leopard").get(0);

        assertThat(animal.name()).isEqualTo("Snow Leopard");
        assertThat(animal.nombreCientifico()).isNull();
        assertThat(animal.habitat()).isNull();
        assertThat(animal.ubicaciones()).isEmpty();
    }

    @Test
    void devuelveListaVaciaSinResultados() {
        servidor.expect(requestTo(BASE + "/animals?name=xyz"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(cliente.buscar("xyz")).isEmpty();
    }

    @Test
    void errorDelServidorSeTraduceANoDisponible() {
        servidor.expect(requestTo(BASE + "/animals?name=lion"))
                .andRespond(withServerError().body("fallo interno con " + CLAVE));

        assertThatThrownBy(() -> cliente.buscar("lion"))
                .isInstanceOf(NinjaNoDisponibleException.class)
                .message().doesNotContain(CLAVE);
    }

    @Test
    void claveInvalidaSeTraduceANoDisponible() {
        servidor.expect(requestTo(BASE + "/animals?name=lion"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Invalid API Key.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.buscar("lion")).isInstanceOf(NinjaNoDisponibleException.class);
    }
}
