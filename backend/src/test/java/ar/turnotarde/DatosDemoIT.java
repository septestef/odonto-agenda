package ar.turnotarde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.agenda.BloqueoRepository;
import ar.turnotarde.agenda.DisponibilidadService;
import ar.turnotarde.agenda.HorarioAtencionRepository;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.shared.config.AppProperties;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.soporte.AplicacionDemo;
import ar.turnotarde.turno.TurnoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Capability datos-demo: perfil demo en el esquema aparte demo_seed (no toca public). */
@ExtendWith(OutputCaptureExtension.class)
class DatosDemoIT {

    private static final String S = AplicacionDemo.ESQUEMA + ".";
    private static final String ZONA = "America/Argentina/Buenos_Aires";

    private final AplicacionDemo demo = new AplicacionDemo();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @BeforeEach
    void esquemaLimpio() {
        demo.reiniciarEsquema();
    }

    /** Contraseña de prueba generada en cada corrida: no hay ningún valor fijo en el repo. */
    private static String contrasenaDePrueba() {
        return "t-" + UUID.randomUUID();
    }

    private static String mensajeCompleto(Throwable error) {
        StringBuilder mensajes = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            mensajes.append(t.getMessage()).append('\n');
        }
        return mensajes.toString();
    }

    private static long contar(JdbcTemplate jdbc, String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    // ---- contraseña ausente (4.1) ----

    @Test
    void sinDemoPasswordLaAplicacionNoArrancaYNombraLaVariable() {
        assertThatThrownBy(() -> demo.arrancar(""))
                .satisfies(error -> {
                    String mensaje = mensajeCompleto(error);
                    assertThat(mensaje).contains("DEMO_PASSWORD");
                    assertThat(mensaje).doesNotContainIgnoringCase("password=").doesNotContain("$2a$");
                });
    }

    @Test
    void conDemoPasswordEnBlancoTampocoArranca() {
        assertThatThrownBy(() -> demo.arrancar("   "))
                .satisfies(error -> assertThat(mensajeCompleto(error)).contains("DEMO_PASSWORD"));
    }

    // ---- datos del seed (4.2) ----

    @Test
    void cargaElOdontologoDemoConSusFranjasYPrestaciones() {
        try (ConfigurableApplicationContext ctx = demo.arrancar(contrasenaDePrueba())) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

            assertThat(contar(jdbc, "select count(*) from " + S + "odontologo")).isEqualTo(1);
            assertThat(jdbc.queryForObject("select email from " + S + "odontologo where slug = 'demo'", String.class))
                    .isEqualTo("demo@example.com");
            assertThat(contar(jdbc, "select count(*) from " + S + "horario_atencion")).isEqualTo(10);
            // lunes a viernes, dos franjas por día: 9-13 y 16-20
            List<Map<String, Object>> franjas = jdbc.queryForList(
                    "select dia_semana, to_char(hora_inicio, 'HH24:MI') as ini, to_char(hora_fin, 'HH24:MI') as fin "
                            + "from " + S + "horario_atencion order by dia_semana, hora_inicio");
            for (int i = 0; i < 10; i++) {
                assertThat(((Number) franjas.get(i).get("dia_semana")).intValue()).isEqualTo(i / 2 + 1);
                assertThat(franjas.get(i).get("ini")).isEqualTo(i % 2 == 0 ? "09:00" : "16:00");
                assertThat(franjas.get(i).get("fin")).isEqualTo(i % 2 == 0 ? "13:00" : "20:00");
            }
            Map<String, Integer> prestaciones = new HashMap<>();
            jdbc.query("select nombre, duracion_minutos, activa from " + S + "prestacion", rs -> {
                assertThat(rs.getBoolean("activa")).isTrue();
                prestaciones.put(rs.getString("nombre"), rs.getInt("duracion_minutos"));
            });
            assertThat(prestaciones).containsOnly(
                    Map.entry("Consulta", 30),
                    Map.entry("Limpieza", 45),
                    Map.entry("Extracción", 60),
                    Map.entry("Arreglo de caries", 45));
        }
    }

    @Test
    void lasTablasDePublicNoSeTocan() {
        try (ConfigurableApplicationContext ctx = demo.arrancar(contrasenaDePrueba())) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

            assertThat(contar(jdbc, "select count(*) from public.odontologo where slug = 'demo'")).isZero();
            assertThat(contar(jdbc, "select count(*) from " + S + "flyway_schema_history where version = '900'"))
                    .isEqualTo(1);
        }
    }

    @Test
    void laAgendaDeLaSemanaDeLaDemoEstaDentroDeLasFranjasYSinSuperposiciones() {
        try (ConfigurableApplicationContext ctx = demo.arrancar(contrasenaDePrueba())) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            String local = "(t.inicio at time zone '" + ZONA + "')";
            String localFin = "(t.fin at time zone '" + ZONA + "')";

            assertThat(contar(jdbc, "select count(*) from " + S + "turno")).isEqualTo(11);
            assertThat(contar(jdbc, "select count(*) from " + S + "turno where estado = 'CANCELADO'")).isEqualTo(1);
            assertThat(contar(jdbc, "select count(*) from " + S + "turno where estado = 'RESERVADO'")).isEqualTo(10);
            assertThat(jdbc.queryForObject("select cancelado_por from " + S + "turno where estado = 'CANCELADO'",
                    String.class)).isEqualTo("PACIENTE");
            // todos entre el lunes 23 y el viernes 27 de noviembre de 2026 (hora del consultorio)
            assertThat(contar(jdbc, "select count(*) from " + S + "turno t where " + local
                    + "::date not between date '2026-11-23' and date '2026-11-27'")).isZero();
            // todos dentro de alguna franja de atención de ese día
            assertThat(contar(jdbc, "select count(*) from " + S + "turno t where not exists (select 1 from "
                    + S + "horario_atencion h where h.odontologo_id = t.odontologo_id "
                    + "and h.dia_semana = extract(isodow from " + local + ") "
                    + "and h.hora_inicio <= " + local + "::time and " + localFin + "::time <= h.hora_fin)"))
                    .isZero();
            // jueves 26 casi libre, viernes 27 libre (para reservar en vivo)
            assertThat(contar(jdbc, "select count(*) from " + S + "turno t where " + local
                    + "::date = date '2026-11-27'")).isZero();
            assertThat(contar(jdbc, "select count(*) from " + S + "turno t where " + local
                    + "::date = date '2026-11-26'")).isLessThanOrEqualTo(1);
            // el miércoles de la demo hay dos turnos activos contiguos (09:00-09:30 y 09:30-10:15)
            assertThat(contar(jdbc, "select count(*) from " + S + "turno a join " + S + "turno b "
                    + "on a.fin = b.inicio and a.estado = 'RESERVADO' and b.estado = 'RESERVADO' "
                    + "where (a.inicio at time zone '" + ZONA + "')::date = date '2026-11-25'")).isEqualTo(1);
            // los tokens de los turnos de ejemplo son hashes que nadie puede reproducir
            assertThat(contar(jdbc, "select count(distinct token_hash) from " + S + "turno "
                    + "where token_hash ~ '^[0-9a-f]{64}$'")).isEqualTo(11);
        }
    }

    // ---- agenda del día de la demo (C-16, V901) ----

    @Test
    void elDiaDeLaDemoTieneTurnosDeMananaYTardeConTodasLasPrestacionesYAlgunoManual() {
        try (ConfigurableApplicationContext ctx = demo.arrancar(contrasenaDePrueba())) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            String delDia = " from " + S + "turno t join " + S + "prestacion p on p.id = t.prestacion_id "
                    + "where t.estado = 'RESERVADO' and (t.inicio at time zone '" + ZONA + "')::date = date '2026-11-25'";
            String horaLocal = "(t.inicio at time zone '" + ZONA + "')::time";

            assertThat(contar(jdbc, "select count(*) from " + S + "flyway_schema_history where version = '901'"))
                    .isEqualTo(1);
            assertThat(contar(jdbc, "select count(*)" + delDia)).isEqualTo(6);
            assertThat(contar(jdbc, "select count(*)" + delDia + " and " + horaLocal + " < time '13:00'"))
                    .isEqualTo(4);
            assertThat(contar(jdbc, "select count(*)" + delDia + " and " + horaLocal + " >= time '16:00'"))
                    .isEqualTo(2);
            assertThat(contar(jdbc, "select count(distinct p.nombre)" + delDia)).isEqualTo(4);
            assertThat(contar(jdbc, "select count(*)" + delDia + " and t.origen = 'MANUAL'")).isEqualTo(2);
            // ningún par de turnos activos se superpone (la exclusion constraint lo garantiza; esto documenta el seed)
            assertThat(contar(jdbc, "select count(*) from " + S + "turno a join " + S + "turno b on a.id < b.id "
                    + "and a.estado = 'RESERVADO' and b.estado = 'RESERVADO' "
                    + "and a.inicio < b.fin and b.inicio < a.fin")).isZero();
        }
    }

    @Test
    void conElRelojDelDiaDeLaDemoQuedanHuecosParaReservarEnVivoYParaElAltaManual() {
        try (ConfigurableApplicationContext ctx = demo.arrancar(contrasenaDePrueba())) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            AppProperties propiedades = ctx.getBean(AppProperties.class);
            // miércoles 25/11/2026 a las 09:00 del consultorio, cuando arranca la demo
            Clock relojDemo = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC);
            DisponibilidadService disponibilidad = new DisponibilidadService(
                    ctx.getBean(HorarioAtencionRepository.class), ctx.getBean(BloqueoRepository.class),
                    ctx.getBean(TurnoRepository.class), new ZonaNegocio(relojDemo, propiedades), propiedades);
            Long odontologo = jdbc.queryForObject("select id from " + S + "odontologo where slug = 'demo'", Long.class);
            Long idConsulta = jdbc.queryForObject("select id from " + S + "prestacion where nombre = 'Consulta'",
                    Long.class);
            Prestacion consulta = ctx.getBean(PrestacionRepository.class).findById(idConsulta).orElseThrow();
            ZoneId zona = ZoneId.of(ZONA);

            List<LocalTime> online26 = horas(disponibilidad.horariosLibres(odontologo, consulta,
                    LocalDate.parse("2026-11-26")), zona);
            assertThat(online26).contains(LocalTime.of(9, 0), LocalTime.of(16, 0)).doesNotContain(LocalTime.of(17, 0));
            assertThat(horas(disponibilidad.horariosLibres(odontologo, consulta, LocalDate.parse("2026-11-27")), zona))
                    .contains(LocalTime.of(9, 0), LocalTime.of(19, 30));
            // el mismo día no se reserva online (24 h), pero el odontólogo sí puede cargar un turno manual
            assertThat(disponibilidad.horariosLibres(odontologo, consulta, LocalDate.parse("2026-11-25"))).isEmpty();
            assertThat(horas(disponibilidad.horariosLibresManual(odontologo, consulta,
                    LocalDate.parse("2026-11-25")), zona))
                    .contains(LocalTime.of(11, 0), LocalTime.of(16, 0))
                    .doesNotContain(LocalTime.of(9, 0), LocalTime.of(10, 30), LocalTime.of(12, 0),
                            LocalTime.of(16, 30), LocalTime.of(18, 0));
        }
    }

    private static List<LocalTime> horas(List<Instant> inicios, ZoneId zona) {
        return inicios.stream().map(i -> i.atZone(zona).toLocalTime()).toList();
    }

    @Test
    void ningunPacienteSuperaDosTurnosFuturosYSusDatosSonFicticios() {
        try (ConfigurableApplicationContext ctx = demo.arrancar(contrasenaDePrueba())) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

            assertThat(contar(jdbc, "select count(*) from " + S + "paciente")).isEqualTo(6);
            assertThat(contar(jdbc, "select count(*) from " + S + "paciente where email not like '%@example.com'"))
                    .isZero();
            assertThat(contar(jdbc, "select count(*) from (select paciente_id from " + S + "turno "
                    + "where estado = 'RESERVADO' group by paciente_id having count(*) > 2) x")).isZero();
        }
    }

    @Test
    void unSegundoArranqueNoDuplicaNingunRegistro() {
        String clave = contrasenaDePrueba();
        try (ConfigurableApplicationContext primero = demo.arrancar(clave)) {
            assertThat(contar(primero.getBean(JdbcTemplate.class), "select count(*) from " + S + "turno"))
                    .isEqualTo(11);
        }

        try (ConfigurableApplicationContext segundo = demo.arrancar(clave)) {
            JdbcTemplate jdbc = segundo.getBean(JdbcTemplate.class);
            assertThat(contar(jdbc, "select count(*) from " + S + "odontologo")).isEqualTo(1);
            assertThat(contar(jdbc, "select count(*) from " + S + "horario_atencion")).isEqualTo(10);
            assertThat(contar(jdbc, "select count(*) from " + S + "prestacion")).isEqualTo(4);
            assertThat(contar(jdbc, "select count(*) from " + S + "paciente")).isEqualTo(6);
            assertThat(contar(jdbc, "select count(*) from " + S + "turno")).isEqualTo(11);
            assertThat(contar(jdbc, "select count(*) from " + S + "flyway_schema_history where version = '900'"))
                    .isEqualTo(1);
            assertThat(contar(jdbc, "select count(*) from " + S + "flyway_schema_history where version = '901'"))
                    .isEqualTo(1);
        }
    }

    // ---- contraseña del odontólogo demo (4.3) ----

    private String hashDelDemo(ConfigurableApplicationContext ctx) {
        return ctx.getBean(JdbcTemplate.class)
                .queryForObject("select password_hash from " + S + "odontologo where slug = 'demo'", String.class);
    }

    @Test
    void elHashGuardadoVerificaLaContrasenaDelEntornoYNingunaOtra(CapturedOutput salida) {
        String clave = contrasenaDePrueba();
        try (ConfigurableApplicationContext ctx = demo.arrancar(clave)) {
            String hash = hashDelDemo(ctx);

            assertThat(hash).startsWith("$2").hasSize(60);
            assertThat(bcrypt.matches(clave, hash)).isTrue();
            assertThat(bcrypt.matches(contrasenaDePrueba(), hash)).isFalse();
        }
        assertThat(salida.getAll()).contains("Contraseña de demo aplicada").doesNotContain(clave);
    }

    @Test
    void alReiniciarConOtraContrasenaElHashPasaAVerificarLaNueva() {
        String anterior = contrasenaDePrueba();
        String nueva = contrasenaDePrueba();
        try (ConfigurableApplicationContext ctx = demo.arrancar(anterior)) {
            assertThat(bcrypt.matches(anterior, hashDelDemo(ctx))).isTrue();
        }

        try (ConfigurableApplicationContext ctx = demo.arrancar(nueva)) {
            String hash = hashDelDemo(ctx);
            assertThat(bcrypt.matches(nueva, hash)).isTrue();
            assertThat(bcrypt.matches(anterior, hash)).isFalse();
        }
    }

    @Test
    void reiniciarConLaMismaContrasenaNoRegrabaElHash() {
        String clave = contrasenaDePrueba();
        String primero;
        try (ConfigurableApplicationContext ctx = demo.arrancar(clave)) {
            primero = hashDelDemo(ctx);
        }

        try (ConfigurableApplicationContext ctx = demo.arrancar(clave)) {
            assertThat(hashDelDemo(ctx)).isEqualTo(primero);
        }
    }

    @Test
    void antesDeAplicarLaContrasenaElHashDelSeedNoVerificaNada() {
        demo.migrarSinArrancar();

        String hashSeed = demo.hashDelDemoSinArrancar();

        assertThat(hashSeed).doesNotStartWith("$2");
        assertThat(bcrypt.matches("", hashSeed)).isFalse();
        assertThat(bcrypt.matches(hashSeed, hashSeed)).isFalse();
        assertThat(bcrypt.matches(contrasenaDePrueba(), hashSeed)).isFalse();
    }
}
