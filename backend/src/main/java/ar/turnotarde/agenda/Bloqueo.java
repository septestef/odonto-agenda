package ar.turnotarde.agenda;

import ar.turnotarde.shared.persistencia.EntidadAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Intervalo en el que el odontólogo no atiende (vacaciones, congreso, etc.). */
@Entity
@Table(name = "bloqueo")
public class Bloqueo extends EntidadAuditada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "odontologo_id", nullable = false)
    private Long odontologoId;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Column(length = 255)
    private String motivo;

    protected Bloqueo() {
    }

    public Bloqueo(Long odontologoId, Instant inicio, Instant fin, String motivo) {
        this.odontologoId = odontologoId;
        this.inicio = inicio;
        this.fin = fin;
        this.motivo = motivo;
    }

    public Long getId() {
        return id;
    }

    public Long getOdontologoId() {
        return odontologoId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public String getMotivo() {
        return motivo;
    }
}
