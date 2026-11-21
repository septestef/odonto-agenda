package ar.turnotarde.shared.seguridad;

import ar.turnotarde.odontologo.Odontologo;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal que queda en la sesión (D9): el id del odontólogo viaja con la autenticación para que
 * ningún cambio posterior tenga que volver a buscarlo ni aceptar un id del pedido.
 */
public final class OdontologoAutenticado implements UserDetails {

    private static final String ROL = "ROLE_ODONTOLOGO";

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final boolean activo;

    public OdontologoAutenticado(Long id, String email, String passwordHash, boolean activo) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.activo = activo;
    }

    /** Único punto de conversión de la entidad al principal de seguridad. */
    public static OdontologoAutenticado desde(Odontologo odontologo) {
        return new OdontologoAutenticado(odontologo.getId(), odontologo.getEmail(),
                odontologo.getPasswordHash(), odontologo.isActivo());
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROL));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
