package ar.turnotarde.auth;

import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.shared.seguridad.OdontologoAutenticado;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Fuente de usuarios del `DaoAuthenticationProvider`: el principal lleva el id de la sesión (D9). */
@Service
public class OdontologoServicioDetalles implements UserDetailsService {

    private final OdontologoRepository odontologos;

    public OdontologoServicioDetalles(OdontologoRepository odontologos) {
        this.odontologos = odontologos;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return odontologos.findByEmailIgnoreCase(email.strip())
                .map(OdontologoAutenticado::desde)
                .orElseThrow(() -> new UsernameNotFoundException("Odontólogo no encontrado"));
    }
}
