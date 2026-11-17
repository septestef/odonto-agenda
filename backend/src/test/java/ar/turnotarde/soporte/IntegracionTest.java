package ar.turnotarde.soporte;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base de los tests de integración: perfil {@code test} contra PostgreSQL local.
 * Antes de levantar el contexto verifica que la base de test responda y, si no,
 * falla con un mensaje que nombra la base y el script que la crea.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegracionTest {

    @BeforeAll
    static void verificarBaseDeTest() {
        StandardEnvironment entorno = new StandardEnvironment();
        entorno.setActiveProfiles("test");
        ConfigDataEnvironmentPostProcessor.applyTo(entorno);

        String url = entorno.getRequiredProperty("spring.datasource.url");
        String usuario = entorno.getProperty("spring.datasource.username");
        String clave = entorno.getProperty("spring.datasource.password");
        try (Connection conexion = DriverManager.getConnection(url, usuario, clave)) {
            conexion.isValid(2);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo conectar a la base de test '%s' (%s) con el usuario '%s'. "
                            .formatted(nombreDeBase(url), url, usuario)
                            + "Creala con: psql -U postgres -f backend/scripts/crear-bases-locales.sql "
                            + "(psql está en C:/Program Files/PostgreSQL/18/bin) y revisá DB_PASSWORD en el .env. "
                            + "Causa: " + e.getMessage(),
                    e);
        }
    }

    private static String nombreDeBase(String url) {
        String sinParametros = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        return sinParametros.substring(sinParametros.lastIndexOf('/') + 1);
    }
}
