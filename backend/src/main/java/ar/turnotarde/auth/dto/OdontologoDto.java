package ar.turnotarde.auth.dto;

import ar.turnotarde.odontologo.Odontologo;

/** Lo mínimo que necesita el frontend para el guard y el encabezado; nunca expone el hash. */
public record OdontologoDto(Long id, String nombre, String apellido, String email, String slug) {

    public static OdontologoDto desde(Odontologo odontologo) {
        return new OdontologoDto(odontologo.getId(), odontologo.getNombre(), odontologo.getApellido(),
                odontologo.getEmail(), odontologo.getSlug());
    }
}
