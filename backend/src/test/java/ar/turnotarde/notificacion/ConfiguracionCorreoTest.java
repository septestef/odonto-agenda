package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class ConfiguracionCorreoTest {
    private static final List<String> VALORES = List.of("MAIL_HOST=smtp.example.test", "MAIL_PORT=587",
            "MAIL_USERNAME=origen@example.test", "MAIL_PASSWORD=secreto-centinela", "MAIL_FROM_NAME=Clínica Prueba");

    static Stream<Arguments> configuracionesInvalidas() {
        return Stream.of(
                Arguments.of("MAIL_HOST", "centinela://invalido"),
                Arguments.of("MAIL_PORT", "centinela-puerto"),
                Arguments.of("MAIL_USERNAME", "centinela@"),
                Arguments.of("MAIL_PASSWORD", "centinela\n"),
                Arguments.of("MAIL_FROM_NAME", "centinela\rBcc"));
    }

    @ParameterizedTest
    @MethodSource("configuracionesInvalidas")
    void demoFallaConVariableFaltanteOInvalidaSinMostrarValores(String variable, String invalido) {
        for (String valor : new String[]{null, "", invalido}) {
            contexto("demo").withPropertyValues(VALORES.stream().filter(kv -> !kv.startsWith(variable + "="))
                            .toArray(String[]::new))
                    .withInitializer(ctx -> {
                        var fuentes = ctx.getEnvironment().getPropertySources();
                        fuentes.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                        fuentes.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                        if (valor != null) {
                            fuentes.addFirst(new MapPropertySource("smtp-prueba", Map.of(variable, valor)));
                        }
                    })
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        assertThat(ctx.getStartupFailure())
                                .hasRootCauseMessage("Configuración SMTP inválida: " + variable);
                        var traza = org.assertj.core.util.Throwables.getStackTrace(ctx.getStartupFailure());
                        assertThat(traza).contains(variable).doesNotContain("secreto-centinela", "origen@example.test");
                        if (valor != null && !valor.isEmpty()) {
                            assertThat(traza).doesNotContain(valor);
                        }
                    });
        }
    }

    @Test
    void configuracionIncompletaNoFrenaDevNiTest() {
        for (String perfil : List.of("dev", "test")) {
            contexto(perfil).withPropertyValues(sustituir("MAIL_PORT", "centinela")).run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx.getBean(CableadoSmtp.class).valida()).isFalse();
            });
        }
    }

    @Test
    void smtpExigeStarttlsHostVerificadoYSusTresTimeouts() {
        contexto("demo").withPropertyValues(VALORES.toArray(String[]::new)).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var sender = ctx.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "true")
                    .containsEntry("mail.smtp.starttls.enable", "true")
                    .containsEntry("mail.smtp.starttls.required", "true")
                    .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                    .containsEntry("mail.smtp.connectiontimeout", "5000")
                    .containsEntry("mail.smtp.timeout", "5000")
                    .containsEntry("mail.smtp.writetimeout", "5000")
                    .containsEntry("mail.debug", "false");
        });
    }

    static String[] sustituir(String variable, String valor) {
        return VALORES.stream().map(kv -> kv.startsWith(variable + "=") ? variable + "=" + valor : kv)
                .toArray(String[]::new);
    }

    private ApplicationContextRunner contexto(String perfil) {
        return new ApplicationContextRunner().withPropertyValues("spring.profiles.active=" + perfil)
                .withUserConfiguration(ConfiguracionCorreo.class, SmtpDePrueba.class);
    }
}
