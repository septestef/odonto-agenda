package ar.turnotarde.odontologo.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.odontologo.OdontologoRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ContrasenaDemoInicializadorTest {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final OdontologoRepository repositorio = mock(OdontologoRepository.class);

    private static String clave() {
        return "t-" + UUID.randomUUID();
    }

    private ContrasenaDemoInicializador inicializador(String clave) {
        return new ContrasenaDemoInicializador(repositorio, new PropiedadesDemo(clave), bcrypt);
    }

    private static Odontologo demoConHash(String hash) {
        return new Odontologo("Demo", "Odontóloga", "demo@example.com", hash, "demo");
    }

    @Test
    void guardaElHashBcryptCuandoElActualNoVerificaLaContrasena() {
        String clave = clave();
        Odontologo demo = demoConHash("!PENDIENTE-DEMO_PASSWORD");
        when(repositorio.findBySlug("demo")).thenReturn(Optional.of(demo));

        inicializador(clave).run(null);

        assertThat(bcrypt.matches(clave, demo.getPasswordHash())).isTrue();
        verify(repositorio).save(demo);
    }

    @Test
    void reemplazaElHashSiCambioLaContrasena() {
        String anterior = clave();
        String nueva = clave();
        Odontologo demo = demoConHash(bcrypt.encode(anterior));
        when(repositorio.findBySlug("demo")).thenReturn(Optional.of(demo));

        inicializador(nueva).run(null);

        assertThat(bcrypt.matches(nueva, demo.getPasswordHash())).isTrue();
        assertThat(bcrypt.matches(anterior, demo.getPasswordHash())).isFalse();
    }

    @Test
    void noRegrabaSiElHashYaVerificaLaContrasena() {
        String clave = clave();
        String hash = bcrypt.encode(clave);
        Odontologo demo = demoConHash(hash);
        when(repositorio.findBySlug("demo")).thenReturn(Optional.of(demo));

        inicializador(clave).run(null);

        assertThat(demo.getPasswordHash()).isEqualTo(hash);
        verify(repositorio, never()).save(any());
    }

    @Test
    void fallaConUnMensajeClaroSiNoExisteElOdontologoDemo() {
        when(repositorio.findBySlug("demo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inicializador(clave()).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo")
                .hasMessageContaining("V900");
    }
}
