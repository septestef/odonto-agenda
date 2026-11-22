package ar.turnotarde.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** D6: BCrypt pelado, sin el prefijo {@code {bcrypt}} del {@code DelegatingPasswordEncoder}. */
class CodificacionContrasenaTest {

    private final PasswordEncoder codificador = new ConfiguracionSeguridad().passwordEncoder();

    @Test
    void elHashEmpiezaCon2YNoLlevaPrefijoDeDelegado() {
        String hash = codificador.encode("ClaveSegura123!");

        assertThat(hash).startsWith("$2").doesNotStartWith("{").doesNotContain("{bcrypt}");
        assertThat(hash).hasSize(60);
    }

    @Test
    void verificaContraElMismoCodificadorSinPrefijo() {
        String hash = codificador.encode("ClaveSegura123!");

        assertThat(codificador.matches("ClaveSegura123!", hash)).isTrue();
        assertThat(codificador.matches("OtraClave123!", hash)).isFalse();
        assertThat(codificador.upgradeEncoding(hash)).isFalse();
    }
}
