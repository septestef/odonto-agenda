package ar.turnotarde.odontologo;

import ar.turnotarde.shared.persistencia.EntidadAuditada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Odontólogo, dueño de una agenda. {@code passwordHash} es BCrypt sin prefijo (contrato con C-03). */
@Entity
@Table(name = "odontologo")
public class Odontologo extends EntidadAuditada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 30)
    private String telefono;

    @Column(name = "direccion_consultorio", length = 255)
    private String direccionConsultorio;

    @Column(nullable = false, length = 60)
    private String slug;

    @Column(nullable = false)
    private boolean activo = true;

    protected Odontologo() {
    }

    public Odontologo(String nombre, String apellido, String email, String passwordHash, String slug) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.email = email;
        this.passwordHash = passwordHash;
        this.slug = slug;
    }

    public void cambiarPasswordHash(String nuevoHash) {
        this.passwordHash = nuevoHash;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getDireccionConsultorio() {
        return direccionConsultorio;
    }

    public String getSlug() {
        return slug;
    }

    public boolean isActivo() {
        return activo;
    }
}
