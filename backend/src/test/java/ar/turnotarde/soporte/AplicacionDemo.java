package ar.turnotarde.soporte;

import ar.turnotarde.TurnoTardeApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;

/**
 * Levanta la aplicación con los perfiles {@code test} + {@code demo} en un esquema aparte
 * ({@code demo_seed}) de la base de test, para no ensuciar {@code public} ni dejar {@code V900}
 * en el historial de Flyway que usan los demás ITs (D9, R4).
 *
 * <p>{@code public} sigue en el search_path porque ahí viven los operadores de {@code btree_gist}.
 */
public final class AplicacionDemo {

    public static final String ESQUEMA = "demo_seed";

    private final StandardEnvironment entorno = new StandardEnvironment();

    public AplicacionDemo() {
        entorno.setActiveProfiles("test");
        ConfigDataEnvironmentPostProcessor.applyTo(entorno);
    }

    private String urlBase() {
        return entorno.getRequiredProperty("spring.datasource.url");
    }

    private Connection conexion() throws SQLException {
        return DriverManager.getConnection(urlBase(), entorno.getProperty("spring.datasource.username"),
                entorno.getProperty("spring.datasource.password"));
    }

    /** Borra y recrea el esquema vacío. */
    public void reiniciarEsquema() {
        try (Connection c = conexion(); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
            s.execute("CREATE SCHEMA " + ESQUEMA);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo reiniciar el esquema " + ESQUEMA, e);
        }
    }

    /** Solo aplica las migraciones (con el seed), sin arrancar la aplicación ni el inicializador de contraseña. */
    public void migrarSinArrancar() {
        String url = urlBase() + (urlBase().contains("?") ? "&" : "?") + "currentSchema=" + ESQUEMA + ",public";
        Flyway.configure()
                .dataSource(url, entorno.getProperty("spring.datasource.username"),
                        entorno.getProperty("spring.datasource.password"))
                .schemas(ESQUEMA)
                .defaultSchema(ESQUEMA)
                .locations("classpath:db/migration", "classpath:db/demo")
                .load()
                .migrate();
    }

    /** Lee el password_hash del odontólogo demo directo de la base, sin pasar por la aplicación. */
    public String hashDelDemoSinArrancar() {
        try (Connection c = conexion(); Statement s = c.createStatement();
                var rs = s.executeQuery("select password_hash from " + ESQUEMA + ".odontologo where slug = 'demo'")) {
            rs.next();
            return rs.getString(1);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer el hash del odontólogo demo", e);
        }
    }

    /** Arranca la aplicación con el perfil demo; {@code password} puede ser vacía para probar el fallo. */
    public ConfigurableApplicationContext arrancar(String password) {
        String url = urlBase() + (urlBase().contains("?") ? "&" : "?") + "currentSchema=" + ESQUEMA + ",public";
        return new SpringApplicationBuilder(TurnoTardeApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test", "demo")
                .run("--spring.datasource.url=" + url,
                        "--spring.flyway.schemas=" + ESQUEMA,
                        "--spring.flyway.default-schema=" + ESQUEMA,
                        "--spring.jpa.properties.hibernate.default_schema=" + ESQUEMA,
                        "--app.demo.password=" + password,
                        "--MAIL_HOST=smtp.example.test",
                        "--MAIL_PORT=587",
                        "--MAIL_USERNAME=demo@example.test",
                        "--MAIL_PASSWORD=centinela-sin-red",
                        "--MAIL_FROM_NAME=OdontoAgenda de prueba");
    }
}
