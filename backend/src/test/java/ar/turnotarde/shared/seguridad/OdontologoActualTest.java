package ar.turnotarde.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

/** RN-SE-01: el dueño de la operación sale siempre del principal de la sesión. */
class OdontologoActualTest {

    private final OdontologoActual actual = new OdontologoActual();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void devuelveElIdYElPrincipalDelOdontologoAutenticado() {
        autenticar(new OdontologoAutenticado(42L, "demo@example.com", "hash", true));

        assertThat(actual.id()).isEqualTo(42L);
        assertThat(actual.autenticado().getUsername()).isEqualTo("demo@example.com");
    }

    @Test
    void sinAutenticacionLanza401NoAutenticado() {
        assertThatThrownBy(() -> actual.id())
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> {
                    ReglaNegocioException regla = (ReglaNegocioException) error;
                    assertThat(regla.codigo()).isEqualTo(CodigosError.NO_AUTENTICADO);
                    assertThat(regla.estado()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    void conUnPrincipalQueNoEsOdontologoLanza401EnVezDe500() {
        User otro = new User("otro", "clave", List.of(new SimpleGrantedAuthority("ROLE_ODONTOLOGO")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(otro, null, otro.getAuthorities()));

        assertThatThrownBy(() -> actual.id())
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).codigo())
                        .isEqualTo(CodigosError.NO_AUTENTICADO));
    }

    private static void autenticar(OdontologoAutenticado principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }
}
