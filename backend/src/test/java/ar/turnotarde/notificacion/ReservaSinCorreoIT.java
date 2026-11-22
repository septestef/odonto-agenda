package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
@DirtiesContext
@TestPropertySource(properties = {"MAIL_HOST=smtp.example.test", "MAIL_PORT=inválido",
        "MAIL_USERNAME=origen@example.test", "MAIL_PASSWORD=clave-centinela", "MAIL_FROM_NAME=Prueba"})
class ReservaSinCorreoIT extends IntegracionTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired SmtpDePrueba smtp;
    @Autowired RelojDePrueba reloj;
    @Autowired @Qualifier("ejecutorNotificaciones") ThreadPoolTaskExecutor ejecutor;

    @AfterEach
    void limpiar() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void configuracionInvalidaNoBloquea201YSoloAuditaConfigSmtp() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        smtp.limpiar();
        reloj.fijar(Instant.parse("2026-11-25T12:00:00Z"));
        var fixtures = new FixturesJdbc(jdbc);
        long odontologo = fixtures.odontologo("sin-correo");
        long prestacion = fixtures.prestacion(odontologo, "Consulta", 30);
        fixtures.horario(odontologo, 5, "09:00", "13:00");
        var ocupados = new CountDownLatch(2);
        var liberar = new CountDownLatch(1);
        Runnable ocupar = () -> { ocupados.countDown(); esperar(liberar); };
        try (var solicitudes = Executors.newSingleThreadExecutor()) {
            try {
                ejecutor.execute(ocupar);
                ejecutor.execute(ocupar);
                esperar(ocupados);
                solicitudes.submit(() -> mvc.perform(post("/api/public/sin-correo/turnos")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"prestacionId":%d,"inicio":"2026-11-27T09:00:00-03:00","dni":"13572469",
                                 "email":"paciente@example.test","nombre":"Paciente","apellido":"Prueba",
                                 "telefono":"+5493510000000"}
                                """.formatted(prestacion))).andExpect(status().isCreated())).get(5, TimeUnit.SECONDS);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Long.class)).isZero();
            } finally {
                liberar.countDown();
                drenar();
            }
        }
        assertThat(smtp.intentos()).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM turno", String.class)).isEqualTo("RESERVADO");
        assertThat(jdbc.queryForMap("SELECT estado, error FROM notificacion"))
                .containsEntry("estado", "ERROR").containsEntry("error", "CONFIG_SMTP");
    }

    private void drenar() {
        var barrera = new CountDownLatch(2);
        Runnable fin = () -> { barrera.countDown(); esperar(barrera); };
        ejecutor.execute(fin);
        ejecutor.execute(fin);
        esperar(barrera);
    }

    private static void esperar(CountDownLatch latch) {
        try {
            assertThat(latch.await(8, TimeUnit.SECONDS)).as("barrera de prueba").isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}

@ActiveProfiles(value = "dev", inheritProfiles = false)
@Import(ReservaSinCorreoDevIT.DobleDev.class)
@TestPropertySource(properties = "MAIL_PORT=")
class ReservaSinCorreoDevIT extends ReservaSinCorreoIT {
    @DynamicPropertySource
    static void usarBaseDePrueba(DynamicPropertyRegistry propiedades) {
        var entorno = new StandardEnvironment();
        entorno.setActiveProfiles("test");
        ConfigDataEnvironmentPostProcessor.applyTo(entorno);
        for (String campo : new String[]{"url", "username", "password"}) {
            String propiedad = "spring.datasource." + campo;
            propiedades.add(propiedad, () -> entorno.getRequiredProperty(propiedad));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DobleDev {
        @Bean
        @Primary
        SmtpDePrueba smtpDevSinRed() {
            return new SmtpDePrueba();
        }
    }
}
