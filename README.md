# pedidos360-ms-catalogo

Microservicio de **catálogo** del sistema Pedidos360 (evaluación EP1, DSY1107 Desarrollo Cloud Native I, DuocUC).

Pedidos360 es una tienda de fauna: el usuario busca una especie por nombre, ve su ficha y hace un pedido. Este servicio:

1. **Busca animales** en la [Animals API de API Ninjas](https://api-ninjas.com/api/animals) y devuelve fichas normalizadas en español. Actúa como **BFF** (Backend for Frontend): la API key de API Ninjas vive solo en el servidor y nunca llega al navegador.
2. **Mantiene el catálogo** de especies a la venta en PostgreSQL: una copia de la ficha más **precio** (CLP, entero) y **stock**.

Repositorios del sistema: `pedidos360-front` (Angular), `pedidos360-ms-catalogo` (este) y `pedidos360-ms-pedidos`.

## Arquitectura

```
Navegador (Angular)
   │  OAuth2 Authorization Code + PKCE  ──►  Amazon Cognito (User Pool)
   │  Authorization: Bearer <access token>
   ▼
AWS API Gateway (REST API)
   │  Cognito Authorizer + scopes por método
   │  Integración HTTP proxy
   ▼
EC2 (Amazon Linux 2023, Corretto 17, systemd)
   ms-catalogo  ──►  API Ninjas (X-Api-Key, solo desde el servidor)
   │
   ▼
RDS PostgreSQL
```

Capas (paquete base `cl.duoc.catalogo`):

| Paquete      | Responsabilidad |
|--------------|-----------------|
| `controller` | Endpoints REST (`AnimalController`, `EspecieController`). |
| `service`    | Reglas de negocio, transacciones y mapeo entidad → DTO. |
| `repository` | Spring Data JPA. |
| `model`      | Entidad `Especie` (tabla `especie` + `especie_ubicacion`). |
| `dto`        | Records de entrada/salida (`FichaResponse`, `EspecieResponse`, `EspecieRequest`, `ErrorResponse`). |
| `ninja`      | Cliente `RestClient` de API Ninjas. |
| `config`     | Seguridad (Resource Server, CORS) y `RestClient.Builder` con timeouts. |
| `web`        | `@RestControllerAdvice`: todos los errores en JSON. |

Stack: Java 17, Spring Boot 4.1.1 (Spring Security 7, Jackson 3), Maven Wrapper, PostgreSQL / H2.

## Endpoints

Todas las respuestas son JSON, también los errores: `{"status": n, "error": "mensaje"}` (la validación agrega `"detalles"` por campo).

| Método y ruta                        | Autorización                         | Respuestas |
|--------------------------------------|--------------------------------------|------------|
| `GET /api/animales/buscar?nombre=`   | scope `pedidos360-api/read`          | 200 lista de `FichaResponse` (vacía si no hay resultados) · 400 si `nombre` tiene menos de 2 caracteres · 502 si API Ninjas falla |
| `GET /api/especies`                  | scope `pedidos360-api/read`          | 200 |
| `GET /api/especies/{id}`             | scope `pedidos360-api/read`          | 200 · 404 |
| `POST /api/especies`                 | scope `pedidos360-api/write` **y** grupo `ADMIN` | 201 · 400 validación · 409 si `nombreApi` ya existe (sin distinguir mayúsculas) · 422 si API Ninjas no tiene una ficha con ese nombre exacto · 502 si API Ninjas falla |
| `GET /actuator/health`               | público                              | 200 |

Además, cualquier ruta sin token responde **401** y con token pero sin permisos **403**.

### Ejemplos

`GET /api/animales/buscar?nombre=lion`

```json
[
  {
    "nombre": "Lion",
    "nombreCientifico": "Panthera leo",
    "habitat": "Open woodland, scrub, grassland",
    "dieta": "Carnivore",
    "velocidadMaxima": "50 mph",
    "esperanzaVida": "8 - 15 years",
    "ubicaciones": ["Africa", "Asia"],
    "especieId": 1,
    "enVenta": true
  }
]
```

`POST /api/especies`

```json
{ "nombreApi": "lion", "nombreEs": "León", "precio": 1500000, "stock": 3 }
```

Responde 201 con `Location: /api/especies/{id}` y un `EspecieResponse`. La ficha (nombre científico, hábitat, dieta, etc.) se copia desde API Ninjas y `nombreApi` se guarda con el `name` exacto que devuelve API Ninjas (`"Lion"`), no con lo que escribió el administrador.

Error de validación:

```json
{ "status": 400, "error": "La solicitud tiene campos inválidos",
  "detalles": { "nombreEs": "es obligatorio", "precio": "debe ser mayor que 0" } }
```

## Variables de entorno

| Variable            | Obligatoria | Ejemplo / valor por defecto | Uso |
|---------------------|-------------|-----------------------------|-----|
| `PORT`              | no  | `8081` | Puerto HTTP. |
| `DB_URL`            | sí  | `jdbc:postgresql://<host-rds>:5432/catalogo` | JDBC de RDS. |
| `DB_USER`           | sí  | `catalogo_app` | Usuario de la BD. |
| `DB_PASS`           | sí  | — | Clave de la BD. |
| `NINJA_API_KEY`     | sí  | — | API key de API Ninjas (secreto). |
| `NINJA_BASE_URL`    | no  | `https://api.api-ninjas.com/v1` | URL base de API Ninjas. |
| `COGNITO_POOL_ID`   | sí  | `us-east-1_XXXXXXXXX` | User Pool; forma el issuer `https://cognito-idp.us-east-1.amazonaws.com/<pool>`. |
| `COGNITO_CLIENT_ID` | sí  | — | App client del front; se exige en el claim `client_id`. |
| `FRONT_ORIGIN`      | no  | `http://localhost:4200` | Origen exacto permitido por CORS. |

Ver [`deploy/ms-catalogo.env.example`](deploy/ms-catalogo.env.example). Ningún secreto se versiona (`.env` y `*.env` están en `.gitignore`).

## Correr en local

Requisitos: JDK 17 o superior. El perfil `local` usa H2 en memoria y valores por defecto para Cognito, así que arranca sin variables:

```bash
# Con una API key real para que /api/animales/buscar funcione
export NINJA_API_KEY=tu-api-key
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

```bash
curl http://localhost:8081/actuator/health          # 200 {"status":"UP",...}
curl -i http://localhost:8081/api/especies          # 401 (falta token)
```

Para llamar a `/api/**` hace falta un access token real de Cognito (obtenido por el front). En local se puede apuntar a un pool real con `COGNITO_POOL_ID` y `COGNITO_CLIENT_ID`.

## Tests

```bash
./mvnw clean package
```

Ningún test sale a internet: API Ninjas se simula con `MockRestServiceServer` o `@MockitoBean`, y los tokens con `jwt()` de `spring-security-test`.

| Clase | Qué prueba |
|-------|------------|
| `MsCatalogoApplicationTests` | El contexto arranca con perfil `local` (y sin contactar a Cognito). |
| `NinjaClientTest` | Mapeo de campos, campos faltantes, lista vacía, header `X-Api-Key`, error de API Ninjas → `NinjaNoDisponibleException` sin filtrar la key. |
| `SeguridadConfigTest` | Validadores del token (issuer, expiración, `token_use`, `client_id`) y conversión scope → `SCOPE_`, `cognito:groups` → `ROLE_`, principal = `username`. |
| `ControladoresSeguridadTest` | Matriz de seguridad y respuestas HTTP de todos los endpoints (401, 403, 200, 201, 400, 404, 409, 422, 502, CORS). |

## Construir y desplegar en EC2

1. Construir el jar ejecutable:
   ```bash
   ./mvnw clean package
   # target/ms-catalogo.jar
   ```
2. En la instancia (Amazon Linux 2023):
   ```bash
   sudo dnf install -y java-17-amazon-corretto-headless
   sudo mkdir -p /opt/pedidos360 /etc/pedidos360
   ```
3. Copiar el jar y la configuración:
   ```bash
   scp target/ms-catalogo.jar ec2-user@<ec2>:/tmp/
   scp deploy/ms-catalogo.service ec2-user@<ec2>:/tmp/
   # en la instancia
   sudo mv /tmp/ms-catalogo.jar /opt/pedidos360/ms-catalogo.jar
   sudo mv /tmp/ms-catalogo.service /etc/systemd/system/ms-catalogo.service
   sudo vi /etc/pedidos360/ms-catalogo.env        # a partir de deploy/ms-catalogo.env.example
   sudo chmod 600 /etc/pedidos360/ms-catalogo.env  # systemd lo lee como root
   ```
4. Iniciar y habilitar el servicio:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now ms-catalogo
   sudo systemctl status ms-catalogo
   journalctl -u ms-catalogo -f
   curl http://localhost:8081/actuator/health
   ```
5. Actualizar una versión: reemplazar el jar y `sudo systemctl restart ms-catalogo`.

`ddl-auto=update` crea las tablas `especie` y `especie_ubicacion` en el primer arranque.

## Seguridad

El servicio es un **OAuth2 Resource Server**. Aunque API Gateway ya valida el token con el Cognito Authorizer, el servicio lo **vuelve a validar** (Defense in Depth): si alguien llega directo al EC2 saltándose el Gateway, igual necesita un access token válido.

- **Firma**: `NimbusJwtDecoder` con el JWKS de Cognito (`{issuer}/.well-known/jwks.json`). Se carga perezosamente: la app arranca sin llamar a Cognito.
- **Claims validados**: firma, `exp`/`nbf`, `iss` = nuestro User Pool, `token_use` = `access` (rechaza id tokens) y `client_id` = nuestro app client (el access token de Cognito no trae `aud`; `client_id` cumple ese papel).
- **Authorities**: `scope` → `SCOPE_xxx`; `cognito:groups` → `ROLE_xxx`. Principal = claim `username`.
- **Reglas** (en orden): dispatch `ERROR` permitido (para que un 404 no salga como 403), `OPTIONS /**` permitido (preflight sin token), `/actuator/health` público, `GET /api/**` requiere scope read, `POST /api/especies` requiere scope write **y** rol ADMIN, **todo lo demás se deniega**.
- **Stateless** y **CSRF deshabilitado**: no hay sesión ni cookies; el token viaja en el header `Authorization`.
- **CORS** con origen exacto (`FRONT_ORIGIN`). Es necesario porque la integración proxy de API Gateway no agrega headers CORS a la respuesta real.
- **401/403 en JSON** con mensaje genérico y el header estándar `WWW-Authenticate`.
- **Errores**: nunca se propaga el mensaje de API Ninjas ni la API key; los 5xx no muestran stack trace.
- **Location relativa** en el 201 para no revelar el host interno del EC2.

### Matriz de seguridad esperada

| Petición                         | Sin token | Token sin scopes | read | write (sin ADMIN) | read + ADMIN | write + ADMIN |
|----------------------------------|-----------|------------------|------|-------------------|--------------|---------------|
| `GET /actuator/health`           | 200 | 200 | 200 | 200 | 200 | 200 |
| `OPTIONS /api/**` (preflight del front) | 200 | 200 | 200 | 200 | 200 | 200 |
| `GET /api/animales/buscar`       | 401 | 403 | 200 | 403 | 200 | 403¹ |
| `GET /api/especies`              | 401 | 403 | 200 | 403 | 200 | 403¹ |
| `GET /api/especies/{id}`         | 401 | 403 | 200/404 | 403 | 200/404 | 403¹ |
| `POST /api/especies`             | 401 | 403 | 403 | 403 | 403 | 201 |
| `DELETE`/`PUT` o ruta no declarada | 401 | 403 | 403 | 403 | 403 | 403 |
| Id token (en vez de access token) | 401 | | | | | |
| Token de otro app client / otro pool / expirado | 401 | | | | | |

¹ Los GET exigen el scope read; en la práctica el administrador tiene read y write.

## Limitaciones conocidas

- **Búsqueda en inglés**: API Ninjas solo reconoce nombres en inglés (`lion`, no `león`). `nombreEs` es un dato del catálogo, no sirve para buscar.
- **Plan gratuito de API Ninjas**: máximo 10 resultados por búsqueda y cuota mensual limitada; si se agota, la búsqueda responde 502.
- **Coincidencia parcial**: `lion` también devuelve `Sea Lion`, `Mountain Lion`, etc. Por eso al publicar se exige el nombre exacto (422 si no está).
- **Fichas no se actualizan**: la copia de la ficha se toma al publicar; si API Ninjas cambia, el catálogo no se entera.
- **El stock no se descuenta desde ms-pedidos**: no hay comunicación entre microservicios; el stock es informativo.
- **Endpoint de metadatos OAuth** (`/.well-known/oauth-protected-resource`, RFC 9728): Spring Security 7 lo publica automáticamente fuera de las reglas de autorización; solo expone la URL del recurso y los métodos aceptados, sin datos sensibles.
