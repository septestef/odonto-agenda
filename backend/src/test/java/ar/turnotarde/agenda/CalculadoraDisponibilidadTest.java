package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Escenarios de {@code specs/disponibilidad-turnos}. Reloj: 2026-11-25T12:00:00Z (09:00 local), horizonte 60 días. */
class CalculadoraDisponibilidadTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 25);
    private static final Duration ANTICIPACION = Duration.ofHours(24);
    private static final int MAX_DIAS = 60;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static Instant local(String fechaHora) {
        return LocalDateTime.parse(fechaHora).atZone(BA).toInstant();
    }

    private static Franja franja(String inicio, String fin) {
        return new Franja(LocalTime.parse(inicio), LocalTime.parse(fin));
    }

    private static Intervalo intervalo(String inicio, String fin) {
        return new Intervalo(local(inicio), local(fin));
    }

    private static AgendaDelDia agenda(String fecha, List<Franja> franjas, List<Intervalo> bloqueos,
            List<Intervalo> turnos) {
        return new AgendaDelDia(LocalDate.parse(fecha), BA, franjas, bloqueos, turnos);
    }

    private static AgendaDelDia agenda(String fecha, Franja... franjas) {
        return agenda(fecha, List.of(franjas), List.of(), List.of());
    }

    private static List<String> libres(AgendaDelDia agenda, int duracionMinutos) {
        return CalculadoraDisponibilidad
                .horariosLibres(agenda, Duration.ofMinutes(duracionMinutos), AHORA, ANTICIPACION, HOY, MAX_DIAS)
                .stream().map(i -> i.atZone(agenda.zona()).format(HORA)).toList();
    }

    private static MotivoRechazo evaluar(String inicio, AgendaDelDia agenda, int duracionMinutos) {
        return CalculadoraDisponibilidad.evaluar(local(inicio), agenda, Duration.ofMinutes(duracionMinutos), AHORA,
                ANTICIPACION, HOY, MAX_DIAS);
    }

    // ---- Inicios cada 15 minutos dentro de la franja

    @Test
    void ofreceInicioCada15MinutosHastaQueElTurnoTerminaALaHoraDeCierre() {
        assertThat(libres(agenda("2026-11-27", franja("09:00", "10:00")), 30))
                .containsExactly("09:00", "09:15", "09:30");
    }

    @Test
    void unTurnoQueTerminaExactoAlCierreSeOfreceYUnoQueSePasaUnMinutoNo() {
        assertThat(libres(agenda("2026-11-27", franja("09:00", "10:00")), 60)).containsExactly("09:00");
        assertThat(libres(agenda("2026-11-27", franja("09:00", "10:00")), 75)).isEmpty();
    }

    @Test
    void variasFranjasDelMismoDiaOfrecenInicioEnCadaUna() {
        assertThat(libres(agenda("2026-11-27", franja("09:00", "10:00"), franja("16:00", "17:00")), 60))
                .containsExactly("09:00", "16:00");
    }

    @Test
    void diaSinFranjasNoOfreceNada() {
        assertThat(libres(agenda("2026-11-28"), 30)).isEmpty();
    }

    @Test
    void laListaEstaOrdenadaYSinDuplicadosAunConFranjasDesordenadas() {
        List<Instant> resultado = CalculadoraDisponibilidad.horariosLibres(
                agenda("2026-11-27", franja("16:00", "17:00"), franja("09:00", "10:00")),
                Duration.ofMinutes(30), AHORA, ANTICIPACION, HOY, MAX_DIAS);

        assertThat(resultado).isSorted().doesNotHaveDuplicates().hasSize(6);
    }

    // ---- Anticipación mínima

    @Test
    void borde24HorasExactasSeOfreceYLosInstantesAnterioresNo() {
        assertThat(libres(agenda("2026-11-26", franja("08:00", "10:00")), 30))
                .containsExactly("09:00", "09:15", "09:30")
                .doesNotContain("08:00", "08:15", "08:30", "08:45");
    }

    @Test
    void fechaPasadaOHoyNoOfreceNada() {
        assertThat(libres(agenda("2026-11-24", franja("08:00", "18:00")), 30)).isEmpty();
        assertThat(libres(agenda("2026-11-25", franja("08:00", "18:00")), 30)).isEmpty();
    }

    @Test
    void anticipacionCeroPermiteReservarDesdeAhoraMismo() {
        List<Instant> resultado = CalculadoraDisponibilidad.horariosLibres(
                agenda("2026-11-25", franja("09:00", "10:00")), Duration.ofMinutes(30), AHORA, Duration.ZERO, HOY,
                MAX_DIAS);

        assertThat(resultado).containsExactly(local("2026-11-25T09:00"), local("2026-11-25T09:15"),
                local("2026-11-25T09:30"));
    }

    // ---- Horizonte

    @Test
    void ultimoDiaDelHorizonteSeOfreceCompleto() {
        assertThat(libres(agenda("2027-01-24", franja("09:00", "10:00")), 30))
                .containsExactly("09:00", "09:15", "09:30");
    }

    @Test
    void unDiaDespuesDelHorizonteNoOfreceNada() {
        assertThat(libres(agenda("2027-01-25", franja("09:00", "10:00")), 30)).isEmpty();
    }

    // ---- Bloqueos

    @Test
    void bloqueoEnMedioDeLaFranjaExcluyeSoloLosInicioQueLoCruzan() {
        AgendaDelDia agenda = agenda("2026-11-27", List.of(franja("09:00", "12:00")),
                List.of(intervalo("2026-11-27T10:00", "2026-11-27T11:00")), List.of());

        assertThat(libres(agenda, 45)).containsExactly("09:00", "09:15", "11:00", "11:15");
    }

    @Test
    void bloqueoDeVariosDiasVaciaElDiaIntermedio() {
        AgendaDelDia agenda = agenda("2026-11-27", List.of(franja("09:00", "18:00")),
                List.of(intervalo("2026-11-26T18:00", "2026-11-28T08:00")), List.of());

        assertThat(libres(agenda, 30)).isEmpty();
    }

    @Test
    void bloqueoQueTerminaJustoAntesDeLaFranjaNoLaAfecta() {
        AgendaDelDia agenda = agenda("2026-11-27", List.of(franja("09:00", "10:00")),
                List.of(intervalo("2026-11-27T08:00", "2026-11-27T09:00")), List.of());

        assertThat(libres(agenda, 30)).containsExactly("09:00", "09:15", "09:30");
    }

    // ---- Turnos

    @Test
    void turnoReservadoOcupaSuHorarioYLosContiguosSeOfrecen() {
        AgendaDelDia agenda = agenda("2026-11-27", List.of(franja("09:00", "11:00")), List.of(),
                List.of(intervalo("2026-11-27T09:30", "2026-11-27T10:00")));

        assertThat(libres(agenda, 30)).containsExactly("09:00", "10:00", "10:15", "10:30");
    }

    @Test
    void sinTurnosElHorarioOcupadoPorOtroDiaNoInterfiere() {
        AgendaDelDia agenda = agenda("2026-11-27", List.of(franja("09:00", "10:00")), List.of(),
                List.of(intervalo("2026-11-26T09:00", "2026-11-26T10:00")));

        assertThat(libres(agenda, 30)).contains("09:00");
    }

    // ---- evaluar: un solo predicado y orden de motivos

    @Test
    void evaluarDevuelveNingunoParaUnInicioOfrecido() {
        assertThat(evaluar("2026-11-27T09:00", agenda("2026-11-27", franja("09:00", "13:00")), 30))
                .isEqualTo(MotivoRechazo.NINGUNO);
    }

    @Test
    void evaluarAnticipacionEsElPrimerMotivoAunqueFalleTodoLoDemas() {
        // 2026-11-26 08:59 es 23 h 59 min después de ahora; el día no tiene franjas y hay un turno encima.
        AgendaDelDia agenda = agenda("2026-11-26", List.of(), List.of(),
                List.of(intervalo("2026-11-26T08:00", "2026-11-26T10:00")));

        assertThat(evaluar("2026-11-26T08:59", agenda, 30)).isEqualTo(MotivoRechazo.ANTICIPACION);
        assertThat(evaluar("2026-11-20T09:00", agenda("2026-11-20", franja("09:00", "13:00")), 30))
                .isEqualTo(MotivoRechazo.ANTICIPACION);
    }

    @Test
    void evaluarElBordeExactoDe24HorasNoEsAnticipacion() {
        assertThat(evaluar("2026-11-26T09:00", agenda("2026-11-26", franja("08:00", "10:00")), 30))
                .isEqualTo(MotivoRechazo.NINGUNO);
    }

    @Test
    void evaluarHorizonteGanaSobreFueraDeAgenda() {
        assertThat(evaluar("2027-01-25T09:00", agenda("2027-01-25"), 30)).isEqualTo(MotivoRechazo.HORIZONTE);
        assertThat(evaluar("2027-01-24T09:00", agenda("2027-01-24", franja("09:00", "13:00")), 30))
                .isEqualTo(MotivoRechazo.NINGUNO);
    }

    @Test
    void evaluarFueraDeAgendaPorFranjaPasoCierreYBloqueo() {
        AgendaDelDia agenda = agenda("2026-11-27", List.of(franja("09:00", "13:00")),
                List.of(intervalo("2026-11-27T11:00", "2026-11-27T12:00")), List.of());

        assertThat(evaluar("2026-11-27T12:45", agenda, 30)).as("terminaría 13:15").isEqualTo(MotivoRechazo.FUERA_DE_AGENDA);
        assertThat(evaluar("2026-11-27T09:10", agenda, 30)).as("fuera de paso").isEqualTo(MotivoRechazo.FUERA_DE_AGENDA);
        assertThat(evaluar("2026-11-27T08:45", agenda, 30)).as("antes de la franja").isEqualTo(MotivoRechazo.FUERA_DE_AGENDA);
        assertThat(evaluar("2026-11-27T10:45", agenda, 30)).as("cruza el bloqueo").isEqualTo(MotivoRechazo.FUERA_DE_AGENDA);
        assertThat(evaluar("2026-11-27T10:30", agenda, 30)).as("termina justo al bloqueo").isEqualTo(MotivoRechazo.NINGUNO);
    }

    @Test
    void evaluarDeUnaFechaDistintaALaDeLaAgendaEsFueraDeAgenda() {
        assertThat(evaluar("2026-11-28T09:00", agenda("2026-11-27", franja("09:00", "13:00")), 30))
                .isEqualTo(MotivoRechazo.FUERA_DE_AGENDA);
    }

    @Test
    void evaluarOcupadoPorTurnoYContiguoNoEsOcupado() {
        AgendaDelDia agenda = agenda("2026-11-27", List.of(franja("09:00", "13:00")), List.of(),
                List.of(intervalo("2026-11-27T09:00", "2026-11-27T09:30")));

        assertThat(evaluar("2026-11-27T09:15", agenda, 30)).isEqualTo(MotivoRechazo.OCUPADO);
        assertThat(evaluar("2026-11-27T09:00", agenda, 30)).isEqualTo(MotivoRechazo.OCUPADO);
        assertThat(evaluar("2026-11-27T09:30", agenda, 30)).isEqualTo(MotivoRechazo.NINGUNO);
    }

    @Test
    void evaluarFueraDeAgendaGanaSobreOcupado() {
        AgendaDelDia agenda = agenda("2026-11-27", List.of(franja("09:00", "13:00")),
                List.of(intervalo("2026-11-27T09:00", "2026-11-27T09:30")),
                List.of(intervalo("2026-11-27T09:00", "2026-11-27T09:30")));

        assertThat(evaluar("2026-11-27T09:00", agenda, 30)).isEqualTo(MotivoRechazo.FUERA_DE_AGENDA);
    }

    @Test
    void todoInicioOfrecidoPorHorariosLibresEvaluaNingunoYElRestoNo() {
        AgendaDelDia agenda = agenda("2026-11-27", List.of(franja("09:00", "12:00")),
                List.of(intervalo("2026-11-27T10:00", "2026-11-27T10:30")),
                List.of(intervalo("2026-11-27T11:00", "2026-11-27T11:15")));
        Duration duracion = Duration.ofMinutes(45);
        List<Instant> libres = CalculadoraDisponibilidad.horariosLibres(agenda, duracion, AHORA, ANTICIPACION, HOY,
                MAX_DIAS);

        for (int minuto = 8 * 60; minuto <= 13 * 60; minuto += 5) {
            Instant candidato = agenda.fecha().atTime(minuto / 60, minuto % 60).atZone(BA).toInstant();
            boolean ofrecido = CalculadoraDisponibilidad.evaluar(candidato, agenda, duracion, AHORA, ANTICIPACION,
                    HOY, MAX_DIAS) == MotivoRechazo.NINGUNO;
            assertThat(ofrecido).as("candidato %s", candidato).isEqualTo(libres.contains(candidato));
        }
        assertThat(libres).isNotEmpty();
    }

    // ---- Cambio de hora (la zona del negocio hoy no lo tiene, pero el cálculo no lo asume)

    @Test
    void conSaltoDeHoraNoHayDuplicadosYCadaTurnoTerminaDentroDeLaFranja() {
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        LocalDate fecha = LocalDate.of(2027, 3, 28);
        AgendaDelDia agenda = new AgendaDelDia(fecha, madrid, List.of(franja("01:00", "04:00")), List.of(), List.of());
        Instant ahora = Instant.parse("2027-03-01T00:00:00Z");

        List<Instant> resultado = CalculadoraDisponibilidad.horariosLibres(agenda, Duration.ofMinutes(30), ahora,
                ANTICIPACION, LocalDate.of(2027, 3, 1), MAX_DIAS);

        Instant cierre = fecha.atTime(4, 0).atZone(madrid).toInstant();
        assertThat(resultado).isSorted().doesNotHaveDuplicates().isNotEmpty();
        assertThat(resultado).allSatisfy(inicio -> assertThat(inicio.plus(Duration.ofMinutes(30)))
                .isBeforeOrEqualTo(cierre));
        assertThat(resultado.stream().map(i -> i.atZone(madrid).format(HORA)).toList())
                .containsExactly("01:00", "01:15", "01:30", "01:45", "03:00", "03:15", "03:30");
    }
}
