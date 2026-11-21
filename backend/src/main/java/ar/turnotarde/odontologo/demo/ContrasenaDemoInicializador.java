package ar.turnotarde.odontologo.demo;

import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.odontologo.OdontologoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda el hash BCrypt de {@code DEMO_PASSWORD} en el odontólogo {@code demo} al arrancar con el perfil
 * {@code demo} (D7). El seed {@code V900} lo deja con un valor que no verifica ninguna contraseña; cambiar la
 * variable y reiniciar actualiza el hash. Nunca loguea la contraseña. Hash BCrypt "pelado", sin prefijo
 * {@code {bcrypt}} (contrato con C-03).
 */
@Component
@Profile("demo")
public class ContrasenaDemoInicializador implements ApplicationRunner {

    static final String SLUG_DEMO = "demo";

    private static final Logger LOG = LoggerFactory.getLogger(ContrasenaDemoInicializador.class);

    private final OdontologoRepository odontologos;
    private final PropiedadesDemo propiedades;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public ContrasenaDemoInicializador(OdontologoRepository odontologos, PropiedadesDemo propiedades) {
        this.odontologos = odontologos;
        this.propiedades = propiedades;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Odontologo demo = odontologos.findBySlug(SLUG_DEMO).orElseThrow(() -> new IllegalStateException(
                "No existe el odontólogo '" + SLUG_DEMO + "': falta aplicar el seed V900 (perfil demo)."));
        if (!bcrypt.matches(propiedades.password(), demo.getPasswordHash())) {
            demo.cambiarPasswordHash(bcrypt.encode(propiedades.password()));
            odontologos.save(demo);
            LOG.info("Contraseña de demo aplicada al odontólogo '{}'.", SLUG_DEMO);
        } else {
            LOG.info("Contraseña de demo ya vigente para el odontólogo '{}'.", SLUG_DEMO);
        }
    }
}
