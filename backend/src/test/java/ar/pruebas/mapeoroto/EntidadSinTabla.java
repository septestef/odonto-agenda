package ar.pruebas.mapeoroto;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entidad cuyo mapeo no coincide con el esquema migrado. */
@Entity
@Table(name = "tabla_que_no_existe")
public class EntidadSinTabla {

    @Id
    private Long id;

    public Long getId() {
        return id;
    }
}
