package ar.turnotarde.prestacion.dto;

import ar.turnotarde.prestacion.Prestacion;

/** Prestación expuesta a la configuración; incluye las dadas de baja para poder mostrar su estado. */
public record PrestacionDto(Long id, String nombre, int duracionMinutos, boolean activa) {

    public static PrestacionDto desde(Prestacion prestacion) {
        return new PrestacionDto(prestacion.getId(), prestacion.getNombre(),
                prestacion.getDuracionMinutos(), prestacion.isActiva());
    }
}
