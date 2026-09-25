package cl.duoc.catalogo.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba las reglas propias sobre el token: validadores de claims y conversión a authorities. */
class SeguridadConfigTest {

    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Prueba";
    private static final String CLIENTE = "cliente-front";

    private final OAuth2TokenValidator<Jwt> validador = SeguridadConfig.validadorToken(ISSUER, CLIENTE);

    @Test
    void aceptaAccessTokenDelClienteCorrecto() {
        assertThat(validador.validate(token(b -> { })).hasErrors()).isFalse();
    }

    @Test
    void rechazaIdToken() {
        assertThat(validador.validate(token(b -> b.claim("token_use", "id"))).hasErrors()).isTrue();
    }

    @Test
    void rechazaTokenDeOtroCliente() {
        assertThat(validador.validate(token(b -> b.claim("client_id", "otra-app"))).hasErrors()).isTrue();
    }

    @Test
    void rechazaOtroIssuer() {
        assertThat(validador.validate(token(b -> b.issuer("https://evil.example.com"))).hasErrors()).isTrue();
    }

    @Test
    void rechazaTokenExpirado() {
        Jwt expirado = token(b -> b.issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600)));
        assertThat(validador.validate(expirado).hasErrors()).isTrue();
    }

    @Test
    void convierteScopesYGruposEnAuthorities() {
        Jwt jwt = token(b -> b.claim("scope", "pedidos360-api/read pedidos360-api/write")
                .claim("cognito:groups", List.of("ADMIN")));

        JwtAuthenticationToken auth =
                (JwtAuthenticationToken) SeguridadConfig.convertidorAutenticacion().convert(jwt);

        assertThat(auth.getName()).isEqualTo("ana");
        assertThat(scopesYRoles(auth))
                .containsExactlyInAnyOrder(SeguridadConfig.SCOPE_READ, SeguridadConfig.SCOPE_WRITE, "ROLE_ADMIN");
    }

    @Test
    void sinGruposNoAgregaRoles() {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) SeguridadConfig.convertidorAutenticacion()
                .convert(token(b -> b.claim("scope", "pedidos360-api/read")));

        assertThat(scopesYRoles(auth)).containsExactly(SeguridadConfig.SCOPE_READ);
    }

    /** Spring Security 7 agrega además FACTOR_BEARER (factor de autenticación); aquí no interesa. */
    private static List<String> scopesYRoles(JwtAuthenticationToken auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("SCOPE_") || a.startsWith("ROLE_"))
                .toList();
    }

    private static Jwt token(Consumer<Jwt.Builder> ajuste) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject("uuid-123")
                .claim("username", "ana")
                .claim("token_use", "access")
                .claim("client_id", CLIENTE)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600));
        ajuste.accept(b);
        return b.build();
    }
}
