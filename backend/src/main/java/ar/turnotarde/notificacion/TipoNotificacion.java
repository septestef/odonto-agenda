package ar.turnotarde.notificacion;

/** Se persiste como texto ({@code @Enumerated(STRING)}) con CHECK en la base. */
public enum TipoNotificacion {
    CONFIRMACION, CANCELACION, REPROGRAMACION, RECORDATORIO
}
