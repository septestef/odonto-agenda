package ar.turnotarde.paciente;

import ar.turnotarde.shared.persistencia.EntidadAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Paciente de un odontólogo (no es global): único por odontólogo y DNI (RN-PA-01). */
@Entity
@Table(name = "paciente")
public class Paciente extends EntidadAuditada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "odontologo_id", nullable = false)
    private Long odontologoId;

    @Column(nullable = false, length = 10)
    private String dni;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Column(nullable = false, length = 255)
    private String email;

    protected Paciente() {
    }

    public Paciente(Long odontologoId, String dni, String nombre, String apellido, String telefono, String email) {
        this.odontologoId = odontologoId;
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.telefono = telefono;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public Long getOdontologoId() {
        return odontologoId;
    }

    public String getDni() {
        return dni;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmail() {
        return email;
    }
}
