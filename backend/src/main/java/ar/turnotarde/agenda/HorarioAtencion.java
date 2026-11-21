package ar.turnotarde.agenda;

import ar.turnotarde.shared.persistencia.EntidadAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

/** Franja semanal de atención (hora local del consultorio; día ISO, 1 = lunes). */
@Entity
@Table(name = "horario_atencion")
public class HorarioAtencion extends EntidadAuditada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "odontologo_id", nullable = false)
    private Long odontologoId;

    @Column(name = "dia_semana", nullable = false)
    private short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    protected HorarioAtencion() {
    }

    public HorarioAtencion(Long odontologoId, short diaSemana, LocalTime horaInicio, LocalTime horaFin) {
        this.odontologoId = odontologoId;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    public Long getId() {
        return id;
    }

    public Long getOdontologoId() {
        return odontologoId;
    }

    public short getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }
}
