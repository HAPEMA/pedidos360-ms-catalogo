package cl.duoc.catalogo.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Resource Server OAuth2. Aunque API Gateway ya valida el token con el Cognito Authorizer,
 * aquí se vuelve a validar (Defense in Depth): si alguien llega directo al EC2 saltándose
 * el Gateway, igual necesita un access token válido de nuestro User Pool y de nuestro app client.
 */
@Configuration
public class SeguridadConfig {

    public static final String SCOPE_READ = "SCOPE_pedidos360-api/read";
    public static final String SCOPE_WRITE = "SCOPE_pedidos360-api/write";

    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        http
                // API sin estado con Bearer token: no hay cookies de sesión que un atacante pueda
                // reutilizar desde otro sitio, por eso CSRF no aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Usa el bean CorsConfigurationSource definido abajo.
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Sin esto, un 404/500 reenviado a /error se evaluaría de nuevo y saldría como 403.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // El preflight CORS lo envía el navegador sin Authorization.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Health check para el balanceador/monitoreo; solo expone UP/DOWN.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAuthority(SCOPE_READ)
                        // Publicar exige las dos cosas: el cliente pidió permiso de escritura (scope)
                        // Y el usuario pertenece al grupo ADMIN de Cognito (rol).
                        .requestMatchers(HttpMethod.POST, "/api/especies").access(AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager.hasAuthority(SCOPE_WRITE),
                                AuthorityAuthorizationManager.hasRole("ADMIN")))
                        // Lista blanca: todo lo no declarado se rechaza.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorAutenticacion()))
                        .authenticationEntryPoint(entradaNoAutenticado())
                        .accessDeniedHandler(accesoDenegado()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entradaNoAutenticado())
                        .accessDeniedHandler(accesoDenegado()));
        return http.build();
    }

    /**
     * Se construye con la URL de JWKS en vez de withIssuerLocation: así la app arranca sin
     * llamar a Cognito y las claves públicas se descargan (y cachean) con el primer token.
     */
    @Bean
    public JwtDecoder jwtDecoder(SeguridadProperties propiedades) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(propiedades.issuer() + "/.well-known/jwks.json")
                .build();
        decoder.setJwtValidator(validadorToken(propiedades.issuer(), propiedades.clientId()));
        return decoder;
    }

    /**
     * - Default + issuer: firma, exp/nbf y que lo emitió nuestro User Pool.
     * - token_use == access: rechaza el id token (está pensado para el cliente, no para APIs).
     * - client_id: el token debe ser de nuestro app client, no de otra app del mismo pool.
     */
    public static OAuth2TokenValidator<Jwt> validadorToken(String issuer, String clientId) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<String>("token_use", "access"::equals),
                new JwtClaimValidator<String>("client_id", clientId::equals));
    }

    /**
     * scope → SCOPE_xxx (convención estándar de Spring) y cognito:groups → ROLE_xxx.
     * El principal es el claim username (el sub es un UUID poco legible en logs).
     */
    public static JwtAuthenticationConverter convertidorAutenticacion() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        Converter<Jwt, Collection<GrantedAuthority>> autoridades = jwt -> {
            Collection<GrantedAuthority> resultado = new ArrayList<>(scopes.convert(jwt));
            List<String> grupos = jwt.getClaimAsStringList("cognito:groups");
            if (grupos != null) {
                grupos.forEach(g -> resultado.add(new SimpleGrantedAuthority("ROLE_" + g)));
            }
            return resultado;
        };
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        convertidor.setPrincipalClaimName("username");
        return convertidor;
    }

    /**
     * Origen exacto (nada de "*"). Se configura aquí porque API Gateway usa integración proxy
     * y la respuesta real (no el preflight) sale de este servicio, sin headers que agregue el Gateway.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origen}") String origen) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origen));
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", cors);
        return fuente;
    }

    /** 401 en JSON; se conserva el header WWW-Authenticate estándar (RFC 6750). */
    private static AuthenticationEntryPoint entradaNoAutenticado() {
        BearerTokenAuthenticationEntryPoint estandar = new BearerTokenAuthenticationEntryPoint();
        return (request, response, excepcion) -> {
            estandar.commence(request, response, excepcion);
            // Mensaje genérico: no se explica por qué falló el token para no ayudar a un atacante.
            escribirJson(response, 401, "No autenticado: falta el token de acceso o no es válido");
        };
    }

    /** 403 en JSON: el token es válido pero no tiene el scope o rol requerido. */
    private static AccessDeniedHandler accesoDenegado() {
        BearerTokenAccessDeniedHandler estandar = new BearerTokenAccessDeniedHandler();
        return (request, response, excepcion) -> {
            estandar.handle(request, response, excepcion);
            escribirJson(response, 403, "Acceso denegado: no tiene permisos para este recurso");
        };
    }

    private static void escribirJson(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + estado + ",\"error\":\"" + mensaje + "\"}");
    }
}
