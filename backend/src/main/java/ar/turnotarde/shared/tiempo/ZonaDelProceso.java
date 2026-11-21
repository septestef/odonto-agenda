package ar.turnotarde.shared.tiempo;

import java.util.TimeZone;

/**
 * Fija la zona horaria por defecto del proceso en UTC. Con {@code hibernate.jdbc.time_zone=UTC} y un proceso en
 * otra zona (la de Windows en una notebook argentina), las columnas {@code time} de las franjas se desplazaban
 * por el offset (09:00 se leía 06:00). El negocio trabaja en UTC internamente (D de C-01): la zona del negocio
 * ({@code APP_TIMEZONE}) solo se usa en los bordes con {@link ZonaNegocio}.
 */
public final class ZonaDelProceso {

    private ZonaDelProceso() {
    }

    public static void fijarUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
