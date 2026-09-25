package cl.duoc.catalogo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * issuer: https://cognito-idp.{region}.amazonaws.com/{userPoolId}
 * clientId: app client de Cognito; cumple el papel de "audience" (el access token no trae aud).
 */
@ConfigurationProperties(prefix = "app.security")
public record SeguridadProperties(String issuer, String clientId) {
}
