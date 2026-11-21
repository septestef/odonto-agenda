package ar.turnotarde.publico;

import java.time.LocalDate;
import java.util.List;

/** Perfil público del odontólogo: sin email, teléfono ni id (D3). {@code direccionConsultorio} puede ser nula. */
public record PerfilPublico(String slug, String nombre, String apellido, String direccionConsultorio,
        String zonaHoraria, LocalDate fechaMinima, LocalDate fechaMaxima, List<PrestacionPublica> prestaciones) {
}
