package cl.duoc.catalogo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class NinjaConfig {

    /**
     * Builder propio con timeouts: si API Ninjas se cuelga, respondemos 502 rápido
     * en vez de bloquear hilos del servidor. Prototype porque el Builder es mutable.
     */
    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(fabrica);
    }
}
