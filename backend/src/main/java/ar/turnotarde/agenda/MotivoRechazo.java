package ar.turnotarde.agenda;

/** Por qué un inicio no es reservable, en el orden en que se evalúa (define qué código ve el paciente). */
public enum MotivoRechazo {
    NINGUNO, ANTICIPACION, HORIZONTE, FUERA_DE_AGENDA, OCUPADO
}
