package cl.duoc.catalogo.ninja;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** La API key vive solo en el servidor (variable de entorno): este servicio es el BFF de API Ninjas. */
@ConfigurationProperties(prefix = "ninja")
public record NinjaProperties(String apiKey, String baseUrl) {
}
