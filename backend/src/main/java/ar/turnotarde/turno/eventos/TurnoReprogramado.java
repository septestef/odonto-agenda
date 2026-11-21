package ar.turnotarde.turno.eventos;

/** Se publica al reprogramar (C-09). {@code token} es el token nuevo en claro; {@link #toString()} lo oculta. */
public record TurnoReprogramado(ResumenTurno anterior, ResumenTurno nuevo, String token) {

    @Override
    public String toString() {
        return "TurnoReprogramado[anterior=" + anterior + ", nuevo=" + nuevo + ", token=***]";
    }
}
