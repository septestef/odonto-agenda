package ar.turnotarde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Escenarios de la capability modelo-datos: lo que la base hace cumplir por sí misma. */
class RestriccionesEsquemaIT extends IntegracionTest {

    private static final Instant INICIO = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-25T12:30:00Z");

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private FixturesJdbc fx;

    @BeforeEach
    void limpiar() {
        jdbc = new JdbcTemplate(dataSource);
        fx = new FixturesJdbc(jdbc);
        LimpiezaBase.limpiar(jdbc);
    }

    // ---- tablas y columnas ----

    private static final Map<String, List<String>> COLUMNAS = Map.of(
            "odontologo", List.of("id", "nombre", "apellido", "email", "password_hash", "telefono",
                    "direccion_consultorio", "slug", "activo"),
            "horario_atencion", List.of("id", "odontologo_id", "dia_semana", "hora_inicio", "hora_fin"),
            "bloqueo", List.of("id", "odontologo_id", "inicio", "fin", "motivo"),
            "prestacion", List.of("id", "odontologo_id", "nombre", "duracion_minutos", "activa"),
            "paciente", List.of("id", "odontologo_id", "dni", "nombre", "apellido", "telefono", "email"),
            "turno", List.of("id", "odontologo_id", "paciente_id", "prestacion_id", "inicio", "fin", "estado",
                    "origen", "cancelado_por", "cancelado_en", "reprogramado_desde_id", "token_hash",
                    "recordatorio_enviado_en"),
            "notificacion", List.of("id", "turno_id", "tipo", "destinatario", "enviado_en", "estado", "error"));

    private List<String> columnasDe(String tabla) {
        return jdbc.queryForList("select column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name = ?", String.class, tabla);
    }

    @Test
    void existenLas7TablasConSusColumnasYLaAuditoriaNoNula() {
        assertThat(COLUMNAS).hasSize(7);
        COLUMNAS.forEach((tabla, columnas) -> {
            List<String> reales = columnasDe(tabla);
            assertThat(reales).as("columnas de %s", tabla)
                    .containsAll(columnas)
                    .contains("creado_en", "actualizado_en")
                    .hasSize(columnas.size() + 2);
            List<String> nulables = jdbc.queryForList("select column_name from information_schema.columns "
                    + "where table_schema = 'public' and table_name = ? "
                    + "and column_name in ('creado_en','actualizado_en') and is_nullable = 'YES'",
                    String.class, tabla);
            assertThat(nulables).as("auditoría nulable en %s", tabla).isEmpty();
        });
    }

    @Test
    void losInstantesSonTimestamptzYLasFranjasHoraLocal() {
        assertThat(tipo("turno", "inicio")).isEqualTo("timestamp with time zone");
        assertThat(tipo("turno", "fin")).isEqualTo("timestamp with time zone");
        assertThat(tipo("bloqueo", "inicio")).isEqualTo("timestamp with time zone");
        assertThat(tipo("turno", "creado_en")).isEqualTo("timestamp with time zone");
        assertThat(tipo("horario_atencion", "hora_inicio")).isEqualTo("time without time zone");
        assertThat(tipo("horario_atencion", "hora_fin")).isEqualTo("time without time zone");
    }

    private String tipo(String tabla, String columna) {
        return jdbc.queryForObject("select data_type from information_schema.columns "
                + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, tabla, columna);
    }

    @Test
    void lasColumnasDeAuditoriaNoTienenDefault() {
        Integer conDefault = jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema = 'public' and column_name in ('creado_en','actualizado_en') "
                + "and column_default is not null", Integer.class);
        assertThat(conDefault).isZero();
    }

    // ---- claves foráneas ----

    @Test
    void rechazaReferenciasAUnOdontologoInexistente() {
        long inexistente = 999_999L;
        assertRechaza(() -> fx.prestacion(inexistente, "Consulta", 30));
        assertRechaza(() -> fx.paciente(inexistente, "12345678"));
        assertRechaza(() -> fx.bloqueo(inexistente, INICIO, FIN));
        assertRechaza(() -> fx.horario(inexistente, 1, "09:00", "13:00"));
        long od = fx.odontologo("od-fk");
        long pac = fx.paciente(od, "12345678");
        long pre = fx.prestacion(od, "Consulta", 30);
        assertRechaza(() -> fx.turno(inexistente, pac, pre, INICIO, FIN, "RESERVADO"));
    }

    @Test
    void rechazaTurnoConPacienteOPrestacionInexistentesYNotificacionSinTurno() {
        long od = fx.odontologo("od-fk2");
        long pac = fx.paciente(od, "12345678");
        long pre = fx.prestacion(od, "Consulta", 30);
        assertRechaza(() -> fx.turno(od, 999_999L, pre, INICIO, FIN, "RESERVADO"));
        assertRechaza(() -> fx.turno(od, pac, 999_999L, INICIO, FIN, "RESERVADO"));
        assertRechaza(() -> fx.notificacion(999_999L, "CONFIRMACION", "ENVIADO"));
    }

    @Test
    void aceptaUnTurnoYSuNotificacionConReferenciasValidas() {
        long od = fx.odontologo("od-ok");
        long pac = fx.paciente(od, "12345678");
        long pre = fx.prestacion(od, "Consulta", 30);
        long turno = fx.turno(od, pac, pre, INICIO, FIN, "RESERVADO");
        assertThatCode(() -> fx.notificacion(turno, "CONFIRMACION", "ENVIADO")).doesNotThrowAnyException();
    }

    // ---- agenda ----

    @ParameterizedTest
    @ValueSource(ints = {0, -15, 20, 7, 50})
    void rechazaDuracionesDePrestacionInvalidas(int duracion) {
        long od = fx.odontologo("od-dur");
        assertRechaza(() -> fx.prestacion(od, "Prestación", duracion));
    }

    @ParameterizedTest
    @ValueSource(ints = {15, 30, 45, 60, 90})
    void aceptaDuracionesMultiplosPositivosDe15(int duracion) {
        long od = fx.odontologo("od-dur-ok");
        assertThatCode(() -> fx.prestacion(od, "Prestación", duracion)).doesNotThrowAnyException();
    }

    @Test
    void rechazaFranjaInvertidaIgualODiaFueraDeRango() {
        long od = fx.odontologo("od-hor");
        assertRechaza(() -> fx.horario(od, 1, "13:00", "09:00"));
        assertRechaza(() -> fx.horario(od, 1, "09:00", "09:00"));
        assertRechaza(() -> fx.horario(od, 0, "09:00", "13:00"));
        assertRechaza(() -> fx.horario(od, 8, "09:00", "13:00"));
        assertThatCode(() -> fx.horario(od, 1, "09:00", "13:00")).doesNotThrowAnyException();
        assertThatCode(() -> fx.horario(od, 7, "16:00", "20:00")).doesNotThrowAnyException();
    }

    @Test
    void rechazaBloqueoVacioOInvertido() {
        long od = fx.odontologo("od-blo");
        assertRechaza(() -> fx.bloqueo(od, FIN, INICIO));
        assertRechaza(() -> fx.bloqueo(od, INICIO, INICIO));
        assertThatCode(() -> fx.bloqueo(od, INICIO, FIN)).doesNotThrowAnyException();
    }

    @Test
    void laPrestacionEsUnicaPorOdontologoPeroSePuedeRepetirEnOtro() {
        long a = fx.odontologo("od-a");
        long b = fx.odontologo("od-b");
        fx.prestacion(a, "Consulta", 30);
        assertRechaza(() -> fx.prestacion(a, "Consulta", 45));
        assertThatCode(() -> fx.prestacion(b, "Consulta", 30)).doesNotThrowAnyException();
    }

    // ---- identidad del odontólogo ----

    @ParameterizedTest
    @ValueSource(strings = {"Dr Pérez", "Demo", "con_guion_bajo", "con espacio", ""})
    void rechazaSlugConFormatoInvalido(String slug) {
        assertRechaza(() -> fx.odontologo(slug, "x" + Math.abs(slug.hashCode()) + "@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo", "dr-perez", "a1-b2"})
    void aceptaSlugConFormatoValido(String slug) {
        assertThatCode(() -> fx.odontologo(slug)).doesNotThrowAnyException();
    }

    @Test
    void emailYSlugSonUnicos() {
        fx.odontologo("uno", "mismo@example.com");
        assertRechaza(() -> fx.odontologo("dos", "mismo@example.com"));
        assertRechaza(() -> fx.odontologo("uno", "otro@example.com"));
        assertThatCode(() -> fx.odontologo("dos", "otro@example.com")).doesNotThrowAnyException();
    }

    // ---- paciente ----

    @Test
    void elDniEsUnicoPorOdontologoPeroSePuedeRepetirEnOtro() {
        long a = fx.odontologo("od-pa");
        long b = fx.odontologo("od-pb");
        fx.paciente(a, "12345678");
        assertRechaza(() -> fx.paciente(a, "12345678"));
        assertThatCode(() -> fx.paciente(b, "12345678")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12A45678", "123456", "123456789", "", "12 45678"})
    void rechazaDniConFormatoInvalido(String dni) {
        long od = fx.odontologo("od-dni");
        assertRechaza(() -> fx.paciente(od, dni));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "12345678"})
    void aceptaDniDe7u8Digitos(String dni) {
        long od = fx.odontologo("od-dni-ok");
        assertThatCode(() -> fx.paciente(od, dni)).doesNotThrowAnyException();
    }

    // ---- valores cerrados ----

    @Test
    void rechazaEstadosOrigenYCanceladoPorDesconocidos() {
        long od = fx.odontologo("od-est");
        long pac = fx.paciente(od, "12345678");
        long pre = fx.prestacion(od, "Consulta", 30);
        assertRechaza(() -> fx.turno(od, pac, pre, INICIO, FIN, "PENDIENTE"));
        int hora = 0;
        for (String estado : List.of("RESERVADO", "CANCELADO", "ATENDIDO", "AUSENTE")) {
            Instant desde = INICIO.plusSeconds(3600L * (++hora));
            assertThatCode(() -> fx.turno(od, pac, pre, desde, desde.plusSeconds(1800), estado))
                    .doesNotThrowAnyException();
        }
        assertRechaza(() -> jdbc.update("update turno set origen = 'TELEFONO'"));
        assertRechaza(() -> jdbc.update("update turno set cancelado_por = 'SISTEMA'"));
        assertThatCode(() -> jdbc.update("update turno set cancelado_por = 'PACIENTE' where estado = 'CANCELADO'"))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaTipoYEstadoDeNotificacionDesconocidos() {
        long od = fx.odontologo("od-not");
        long turno = fx.turno(od, fx.paciente(od, "12345678"), fx.prestacion(od, "Consulta", 30),
                INICIO, FIN, "RESERVADO");
        assertRechaza(() -> fx.notificacion(turno, "SPAM", "ENVIADO"));
        assertRechaza(() -> fx.notificacion(turno, "CONFIRMACION", "PENDIENTE"));
        for (String tipo : List.of("CONFIRMACION", "CANCELACION", "REPROGRAMACION", "RECORDATORIO")) {
            assertThatCode(() -> fx.notificacion(turno, tipo, "ERROR")).doesNotThrowAnyException();
        }
    }

    @Test
    void rechazaTurnoCuyoFinNoEsPosteriorAlInicio() {
        long od = fx.odontologo("od-fin");
        long pac = fx.paciente(od, "12345678");
        long pre = fx.prestacion(od, "Consulta", 30);
        assertRechaza(() -> fx.turno(od, pac, pre, INICIO, INICIO, "RESERVADO"));
        assertRechaza(() -> fx.turno(od, pac, pre, FIN, INICIO, "RESERVADO"));
        assertThatCode(() -> fx.turno(od, pac, pre, INICIO, FIN, "RESERVADO")).doesNotThrowAnyException();
    }

    // ---- token ----

    @Test
    void elTokenHashEsUnicoYDe64Caracteres() {
        long od = fx.odontologo("od-tok");
        long pac = fx.paciente(od, "12345678");
        long pre = fx.prestacion(od, "Consulta", 30);
        String hash = FixturesJdbc.tokenHashAleatorio();
        fx.turno(od, pac, pre, INICIO, FIN, "RESERVADO", hash);
        Instant otroInicio = INICIO.plusSeconds(7200);
        assertRechaza(() -> fx.turno(od, pac, pre, otroInicio, otroInicio.plusSeconds(1800), "RESERVADO", hash));
        assertRechaza(() -> fx.turno(od, pac, pre, otroInicio, otroInicio.plusSeconds(1800), "RESERVADO",
                "x".repeat(65)));
    }

    @Test
    void laUnicaColumnaDeTokenEsTokenHash() {
        List<String> relacionadas = jdbc.queryForList("select column_name from information_schema.columns "
                + "where table_schema = 'public' and column_name ilike '%token%'", String.class);
        assertThat(relacionadas).containsExactly("token_hash");
        assertThat(columnasDe("turno")).contains("token_hash").doesNotContain("token");
    }

    private static void assertRechaza(Runnable operacion) {
        assertThatThrownBy(operacion::run).isInstanceOf(DataIntegrityViolationException.class);
    }
}
