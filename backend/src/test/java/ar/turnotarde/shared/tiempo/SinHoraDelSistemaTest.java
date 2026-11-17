package ar.turnotarde.shared.tiempo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Guardia de la build: el código de producción no puede leer la hora sin el Clock inyectado. */
class SinHoraDelSistemaTest {

    private static final DescribedPredicate<JavaMethodCall> NOW_SIN_ARGUMENTOS = DescribedPredicate.describe(
            "now() de java.time sin argumentos",
            llamada -> llamada.getName().equals("now")
                    && llamada.getTarget().getRawParameterTypes().isEmpty()
                    && llamada.getTargetOwner().getPackageName().startsWith("java.time"));

    private static final DescribedPredicate<JavaMethodCall> CURRENT_TIME_MILLIS = DescribedPredicate.describe(
            "System.currentTimeMillis()",
            llamada -> llamada.getTargetOwner().isEquivalentTo(System.class)
                    && llamada.getName().equals("currentTimeMillis"));

    private static final DescribedPredicate<JavaConstructorCall> NEW_DATE_SIN_ARGUMENTOS = DescribedPredicate.describe(
            "new java.util.Date()",
            llamada -> llamada.getTargetOwner().isEquivalentTo(java.util.Date.class)
                    && llamada.getTarget().getRawParameterTypes().isEmpty());

    private static ArchRule reglaNow() {
        return noClasses().should().callMethodWhere(NOW_SIN_ARGUMENTOS);
    }

    private static ArchRule reglaCurrentTimeMillis() {
        return noClasses().should().callMethodWhere(CURRENT_TIME_MILLIS);
    }

    private static ArchRule reglaNewDate() {
        return noClasses().should().callConstructorWhere(NEW_DATE_SIN_ARGUMENTOS);
    }

    private static final JavaClasses PRODUCCION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ar.turnotarde");

    @Test
    void produccionNoLeeLaHoraDelSistema() {
        assertThat(PRODUCCION.contain(ZonaNegocio.class)).isTrue();

        reglaNow().check(PRODUCCION);
        reglaCurrentTimeMillis().check(PRODUCCION);
        reglaNewDate().check(PRODUCCION);
    }

    @Test
    void laGuardiaDetectaNowSinReloj() {
        JavaClasses infractor = new ClassFileImporter().importClasses(InfractorNow.class);

        assertThatThrownBy(() -> reglaNow().check(infractor))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("InfractorNow");
    }

    @Test
    void laGuardiaDetectaCurrentTimeMillisYNewDate() {
        JavaClasses infractor = new ClassFileImporter().importClasses(InfractorMillis.class);

        assertThatThrownBy(() -> reglaCurrentTimeMillis().check(infractor)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> reglaNewDate().check(infractor)).isInstanceOf(AssertionError.class);
    }

    @Test
    void laGuardiaPermiteNowConRelojExplicito() {
        JavaClasses correcto = new ClassFileImporter().importClasses(ConReloj.class);

        reglaNow().check(correcto);
    }

    static class InfractorNow {
        Instant ahora() {
            return Instant.now();
        }
    }

    static class InfractorMillis {
        long millis() {
            return System.currentTimeMillis();
        }

        java.util.Date fecha() {
            return new java.util.Date();
        }
    }

    static class ConReloj {
        Instant ahora(java.time.Clock reloj) {
            return Instant.now(reloj);
        }
    }
}
