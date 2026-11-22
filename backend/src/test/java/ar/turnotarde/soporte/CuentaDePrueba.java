package ar.turnotarde.soporte;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Crea un odontólogo con login real para los ITs de configuración (C-05). */
public final class CuentaDePrueba {

    public static final String CLAVE = "ClaveSegura123!";

    public record Cuenta(long id, String email, String slug, AutenticacionDePrueba.SesionDePrueba sesion) {
    }

    private CuentaDePrueba() {
    }

    public static Cuenta crear(MockMvc mvc, JdbcTemplate jdbc) throws Exception {
        return crear(mvc, jdbc, "cfg");
    }

    public static Cuenta crear(MockMvc mvc, JdbcTemplate jdbc, String prefijoSlug) throws Exception {
        String slug = prefijoSlug + "-" + UUID.randomUUID();
        String email = "u-" + UUID.randomUUID() + "@example.com";
        long id = new FixturesJdbc(jdbc).odontologo(slug, email, AutenticacionDePrueba.hash(CLAVE), true);
        return new Cuenta(id, email, slug, AutenticacionDePrueba.login(mvc, email, CLAVE));
    }
}
