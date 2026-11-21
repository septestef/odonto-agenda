package ar.turnotarde.notificacion;

import ar.turnotarde.shared.persistencia.EntidadAuditada;
import ar.turnotarde.turno.Turno;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Registro de un email enviado (o fallido) para un turno; evita duplicar recordatorios. */
@Entity
@Table(name = "notificacion")
public class Notificacion extends EntidadAuditada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Column(nullable = false, length = 255)
    private String destinatario;

    @Column(name = "enviado_en", nullable = false)
    private Instant enviadoEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoNotificacion estado;

    @Column(columnDefinition = "text")
    private String error;

    protected Notificacion() {
    }

    public Notificacion(Turno turno, TipoNotificacion tipo, String destinatario, Instant enviadoEn,
            EstadoNotificacion estado, String error) {
        this.turno = turno;
        this.tipo = tipo;
        this.destinatario = destinatario;
        this.enviadoEn = enviadoEn;
        this.estado = estado;
        this.error = error;
    }

    public Long getId() {
        return id;
    }

    public Turno getTurno() {
        return turno;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public EstadoNotificacion getEstado() {
        return estado;
    }

    public String getError() {
        return error;
    }
}
