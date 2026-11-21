package ar.turnotarde.publico;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SolicitudReservaTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDADOR = FABRICA.getValidator();
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-11-27T09:00:00-03:00");

    @AfterAll
    static void cerrar() {
        FABRICA.close();
    }

    private static SolicitudReserva solicitud(String dni, String nombre, String apellido, String telefono,
            String email) {
        return new SolicitudReserva(1L, INICIO, dni, nombre, apellido, telefono, email);
    }

    private static SolicitudReserva valida() {
        return solicitud("12345678", "Luis", "Gómez", "+5493511234567", "luis@example.com");
    }

    private static List<String> camposInvalidos(SolicitudReserva s) {
        Set<ConstraintViolation<SolicitudReserva>> violaciones = VALIDADOR.validate(s);
        return violaciones.stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
    }

    private static String mensajeDe(SolicitudReserva s, String campo) {
        return VALIDADOR.validate(s).stream().filter(v -> v.getPropertyPath().toString().equals(campo))
                .min(Comparator.comparing(ConstraintViolation::getMessage)).orElseThrow().getMessage();
    }

    @Test
    void unaSolicitudCompletaYBienFormadaNoTieneViolaciones() {
        assertThat(camposInvalidos(valida())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"luis@example.com", "a.b+c@sub.dominio.com.ar", "x@y.io"})
    void emailsComunesSonValidos(String email) {
        assertThat(camposInvalidos(solicitud("12345678", "Luis", "Gómez", "+5493511234567", email))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"luis@", "a@b", "a@b..c", "a b@c.com", "@example.com", "a@@example.com"})
    void emailsMalFormadosSeRechazan(String email) {
        assertThat(camposInvalidos(solicitud("12345678", "Luis", "Gómez", "+5493511234567", email)))
                .containsExactly("email");
    }

    @Test
    void unEmailDeMasDe255CaracteresSeRechazaConUnSoloDetalle() {
        String email = "a".repeat(60) + "@" + "b".repeat(200) + ".com";

        assertThat(email).hasSizeGreaterThan(255);
        assertThat(camposInvalidos(solicitud("12345678", "Luis", "Gómez", "+5493511234567", email)))
                .containsExactly("email");
    }

    @Test
    void unEmailDe255CaracteresEsValido() {
        String email = "a".repeat(60) + "@" + "b".repeat(190) + ".com";

        assertThat(email).hasSize(255);
        assertThat(camposInvalidos(solicitud("12345678", "Luis", "Gómez", "+5493511234567", email))).isEmpty();
    }

    @Test
    void unEmailEnormeYMalFormadoSeRechazaSinConsumirCpu() {
        // Entrada que hacía retroceder cuadráticamente a la regex anterior (ReDoS en un endpoint público).
        String email = "a@" + "a.".repeat(50_000) + " ";

        List<String> invalidos = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(1),
                () -> camposInvalidos(solicitud("12345678", "Luis", "Gómez", "+5493511234567", email)));

        assertThat(invalidos).containsExactly("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "12345678"})
    void dniDe7u8DigitosEsValido(String dni) {
        assertThat(camposInvalidos(solicitud(dni, "Luis", "Gómez", "+5493511234567", "luis@example.com"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12.345.678", "123456", "123456789", "1234567a", " 12345678", "12345678 "})
    void dniInvalidoSeInformaUnaSolaVezPorElCampoDni(String dni) {
        assertThat(camposInvalidos(solicitud(dni, "Luis", "Gómez", "+5493511234567", "luis@example.com")))
                .containsExactly("dni");
    }

    @Test
    void elMensajeDelDniEstaEnVoseoYExplicaElFormato() {
        assertThat(mensajeDe(solicitud("12.345.678", "Luis", "Gómez", "+5493511234567", "luis@example.com"), "dni"))
                .isEqualTo("Ingresá tu DNI sin puntos, 7 u 8 dígitos");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+5493511234567", "+12345678", "+123456789012345"})
    void telefonoE164EsValido(String telefono) {
        assertThat(camposInvalidos(solicitud("12345678", "Luis", "Gómez", telefono, "luis@example.com"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"3511234567", "+0123456789", "+1234567", "+1234567890123456", "+54 9 351 1234567",
            "5493511234567"})
    void telefonoQueNoEsE164SeRechaza(String telefono) {
        SolicitudReserva s = solicitud("12345678", "Luis", "Gómez", telefono, "luis@example.com");

        assertThat(camposInvalidos(s)).containsExactly("telefono");
    }

    @Test
    void elMensajeDelTelefonoDaUnEjemplo() {
        assertThat(mensajeDe(solicitud("12345678", "Luis", "Gómez", "3511234567", "luis@example.com"), "telefono"))
                .isEqualTo("Ingresá el teléfono con código de país, por ejemplo +5493511234567");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"luis@", "a@b", "@example.com", "luis example@x.com", "luis@@example.com", "luis"})
    void emailInvalidoSeRechazaUnaSolaVezPorElCampo(String email) {
        SolicitudReserva s = solicitud("12345678", "Luis", "Gómez", "+5493511234567", email);

        assertThat(camposInvalidos(s)).containsExactly("email");
    }

    @Test
    void emailDemasiadoLargoSeRechaza() {
        String largo = "a".repeat(250) + "@x.com";

        assertThat(camposInvalidos(solicitud("12345678", "Luis", "Gómez", "+5493511234567", largo)))
                .contains("email");
    }

    @Test
    void emailInvalidoYNombreVacioSeInformanJuntos() {
        assertThat(camposInvalidos(solicitud("12345678", "", "Gómez", "+5493511234567", "luis@")))
                .containsExactly("email", "nombre");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nombreYApellidoVaciosSeRechazan(String vacio) {
        assertThat(camposInvalidos(solicitud("12345678", vacio, vacio, "+5493511234567", "luis@example.com")))
                .containsExactly("apellido", "nombre");
    }

    @Test
    void nombreYApellidoDeMasDe100CaracteresSeRechazanPeroElLimiteExactoNo() {
        assertThat(camposInvalidos(solicitud("12345678", "a".repeat(100), "b".repeat(100), "+5493511234567",
                "luis@example.com"))).isEmpty();
        assertThat(camposInvalidos(solicitud("12345678", "a".repeat(101), "b".repeat(101), "+5493511234567",
                "luis@example.com"))).containsExactly("apellido", "nombre");
    }

    @Test
    void prestacionEInicioSonObligatoriosYLaPrestacionPositiva() {
        assertThat(camposInvalidos(new SolicitudReserva(null, null, "12345678", "Luis", "Gómez", "+5493511234567",
                "luis@example.com"))).containsExactly("inicio", "prestacionId");
        assertThat(camposInvalidos(new SolicitudReserva(0L, INICIO, "12345678", "Luis", "Gómez", "+5493511234567",
                "luis@example.com"))).containsExactly("prestacionId");
        assertThat(camposInvalidos(new SolicitudReserva(-3L, INICIO, "12345678", "Luis", "Gómez", "+5493511234567",
                "luis@example.com"))).containsExactly("prestacionId");
    }
}
