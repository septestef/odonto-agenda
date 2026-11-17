package ar.turnotarde.shared.tiempo;

import ar.turnotarde.shared.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/**
 * Interpreta y muestra fechas en la zona horaria del negocio (APP_TIMEZONE).
 * Internamente todo es UTC: la zona se usa solo en los bordes.
 */
@Component
public class ZonaNegocio {

    private final Clock reloj;
    private final ZoneId zona;

    public ZonaNegocio(Clock reloj, AppProperties propiedades) {
        this.reloj = reloj;
        this.zona = propiedades.timezone();
    }

    public ZoneId zona() {
        return zona;
    }

    /** Instante actual según el reloj inyectado. */
    public Instant ahora() {
        return reloj.instant();
    }

    /** Fecha de hoy en la zona del negocio. */
    public LocalDate hoy() {
        return LocalDate.ofInstant(reloj.instant(), zona);
    }

    public ZonedDateTime aLocal(Instant instante) {
        return instante.atZone(zona);
    }

    public Instant aInstante(LocalDate fecha, LocalTime hora) {
        return ZonedDateTime.of(fecha, hora, zona).toInstant();
    }
}
