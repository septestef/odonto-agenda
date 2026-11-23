package ar.turnotarde.turno;

import ar.turnotarde.paciente.Paciente;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.shared.persistencia.EntidadAuditada;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Turno. La garantía de no superposición es la exclusion constraint {@code turno_sin_solapamiento}
 * de la base (RN-TU-01), no código Java. Solo guarda el hash del token de gestión (RN-SE-02).
 */
@Entity
@Table(name = "turno")
public class Turno extends EntidadAuditada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "odontologo_id", nullable = false)
    private Long odontologoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false)
    private Prestacion prestacion;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTurno estado = EstadoTurno.RESERVADO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenTurno origen;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelado_por", length = 20)
    private CanceladoPor canceladoPor;

    @Column(name = "cancelado_en")
    private Instant canceladoEn;

    @Column(name = "reprogramado_desde_id")
    private Long reprogramadoDesdeId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "email_gestion", length = 255, updatable = false)
    private String emailGestion;

    @Column(name = "recordatorio_enviado_en")
    private Instant recordatorioEnviadoEn;

    protected Turno() {
    }

    public Turno(Long odontologoId, Paciente paciente, Prestacion prestacion, Instant inicio, Instant fin,
            OrigenTurno origen, String tokenHash) {
        this(odontologoId, paciente, prestacion, inicio, fin, origen, tokenHash, null);
    }

    Turno(Long odontologoId, Paciente paciente, Prestacion prestacion, Instant inicio, Instant fin,
            OrigenTurno origen, String tokenHash, String emailGestion) {
        this.odontologoId = odontologoId;
        this.paciente = paciente;
        this.prestacion = prestacion;
        this.inicio = inicio;
        this.fin = fin;
        this.origen = origen;
        this.tokenHash = tokenHash;
        this.emailGestion = emailGestion;
    }

    public Long getId() {
        return id;
    }

    public Long getOdontologoId() {
        return odontologoId;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public Prestacion getPrestacion() {
        return prestacion;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public OrigenTurno getOrigen() {
        return origen;
    }

    public CanceladoPor getCanceladoPor() {
        return canceladoPor;
    }

    public Instant getCanceladoEn() {
        return canceladoEn;
    }

    public Long getReprogramadoDesdeId() {
        return reprogramadoDesdeId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    void cancelarPorPaciente(Instant ahora) {
        estado = EstadoTurno.CANCELADO;
        canceladoPor = CanceladoPor.PACIENTE;
        canceladoEn = ahora;
    }

    void cancelarPorOdontologo(Instant ahora) {
        estado = EstadoTurno.CANCELADO;
        canceladoPor = CanceladoPor.ODONTOLOGO;
        canceladoEn = ahora;
    }

    Turno sucesor(Instant nuevoInicio, Instant nuevoFin, String nuevoHash) {
        var nuevo = new Turno(odontologoId, paciente, prestacion, nuevoInicio, nuevoFin, origen,
                nuevoHash, emailGestion);
        nuevo.reprogramadoDesdeId = id;
        return nuevo;
    }

    String getEmailGestion() {
        return emailGestion;
    }

    public Instant getRecordatorioEnviadoEn() {
        return recordatorioEnviadoEn;
    }
}
