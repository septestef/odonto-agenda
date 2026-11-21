package ar.turnotarde.soporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Sesiones y tokens CSRF listos para MockMvc; reutilizable por C-05, C-12 y C-17. */
public final class AutenticacionDePrueba {

    private static final ObjectMapper JSON = new ObjectMapper();

    public record SesionDePrueba(MockHttpSession sesion, Cookie csrfCookie) {

        public String csrf() {
            return csrfCookie.getValue();
        }
    }

    private AutenticacionDePrueba() {
    }

    /** Sesión con un principal {@code ROLE_ODONTOLOGO}, para tests que no necesitan el login real. */
    public static MockHttpSession sesionConRol() {
        User usuario = new User("demo", "sin-usar", List.of(new SimpleGrantedAuthority("ROLE_ODONTOLOGO")));
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        return sesion;
    }

    /** Login real contra el endpoint: devuelve la sesión y la cookie CSRF que el frontend reenviaría. */
    public static SesionDePrueba login(MockMvc mvc, String email, String password) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return new SesionDePrueba((MockHttpSession) resultado.getRequest().getSession(false),
                cookieCsrf(resultado.getResponse()));
    }

    /** BCrypt de fuerza 4: suficiente para probar el contrato sin pagar la fuerza 10 en cada test. */
    public static String hash(String password) {
        return new BCryptPasswordEncoder(4).encode(password);
    }

    public static Cookie cookieCsrf(org.springframework.mock.web.MockHttpServletResponse respuesta) {
        Cookie cookie = respuesta.getCookie("XSRF-TOKEN");
        if (cookie == null) {
            throw new IllegalStateException("La respuesta no incluyó la cookie XSRF-TOKEN");
        }
        return cookie;
    }
}
