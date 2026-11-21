package ar.turnotarde.auth;

import ar.turnotarde.shared.seguridad.ErrorApiAccessDeniedHandler;
import ar.turnotarde.shared.seguridad.ErrorApiAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Única cadena de seguridad (D1). Todo lo que no sea público exige sesión de odontólogo; la SPA y sus
 * recursos se sirven sin sesión. Los rechazos salen en el formato uniforme de la API (D6).
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    /** BCrypt "pelado", sin {@code DelegatingPasswordEncoder} ni prefijo (contrato D6 de C-02). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Proveedor único de credenciales (D6). {@code hideUserNotFoundExceptions = false} deja llegar el
     * {@code UsernameNotFoundException} al servicio: así puede correr BCrypt contra un hash ficticio e
     * igualar tiempos entre un email inexistente y uno con contraseña incorrecta (D8).
     */
    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService usuarios,
            PasswordEncoder codificador) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(usuarios);
        proveedor.setPasswordEncoder(codificador);
        proveedor.setHideUserNotFoundExceptions(false);
        return proveedor;
    }

    @Bean
    AuthenticationManager authenticationManager(DaoAuthenticationProvider proveedor) {
        return new ProviderManager(proveedor);
    }

    /** Rotación del id de sesión al autenticar: protección contra fijación (D3). */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    /** Persistencia explícita del contexto en la sesión HTTP (D3). */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http,
            ErrorApiAuthenticationEntryPoint entryPoint,
            ErrorApiAccessDeniedHandler accesoDenegado) throws Exception {
        http
                .csrf(csrf -> csrf.spa()
                        .ignoringRequestMatchers("/api/public/**", "/api/auth/login"))
                .sessionManagement(sesion -> sesion
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionAuthenticationStrategy(sessionAuthenticationStrategy()))
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/**").hasRole("ODONTOLOGO")
                        .anyRequest().permitAll())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accesoDenegado))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
