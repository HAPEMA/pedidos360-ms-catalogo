package cl.duoc.catalogo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class MsCatalogoApplicationTests {

    /** También prueba que la app arranca sin contactar a Cognito (JWKS perezoso). */
    @Test
    void contextLoads() {
    }
}
