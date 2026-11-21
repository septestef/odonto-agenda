package ar.turnotarde.prestacion;

import ar.turnotarde.shared.persistencia.EntidadAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tipo de atención que ofrece un odontólogo (duración múltiplo de 15 minutos, RN-AG-03). */
@Entity
@Table(name = "prestacion")
public class Prestacion extends EntidadAuditada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "odontologo_id", nullable = false)
    private Long odontologoId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "duracion_minutos", nullable = false)
    private short duracionMinutos;

    @Column(nullable = false)
    private boolean activa = true;

    protected Prestacion() {
    }

    public Prestacion(Long odontologoId, String nombre, short duracionMinutos) {
        this.odontologoId = odontologoId;
        this.nombre = nombre;
        this.duracionMinutos = duracionMinutos;
    }

    /** Baja lógica (RN-AG-04): los turnos existentes siguen referenciándola. */
    public void desactivar() {
        this.activa = false;
    }

    public Long getId() {
        return id;
    }

    public Long getOdontologoId() {
        return odontologoId;
    }

    public String getNombre() {
        return nombre;
    }

    public short getDuracionMinutos() {
        return duracionMinutos;
    }

    public boolean isActiva() {
        return activa;
    }
}
