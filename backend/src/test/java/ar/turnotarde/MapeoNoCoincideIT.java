package ar.turnotarde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.pruebas.mapeoroto.AplicacionMapeoRoto;
import ar.turnotarde.soporte.IntegracionTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Escenario "Mapeo que no coincide" de configuracion-entorno (pendiente de C-01): con
 * {@code ddl-auto=validate}, una entidad sin tabla en el esquema migrado impide el arranque
 * y el error nombra la tabla.
 */
class MapeoNoCoincideIT extends IntegracionTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void laAplicacionNoArrancaSiUnaEntidadNoTieneTabla() {
        SpringApplicationBuilder aplicacion = new SpringApplicationBuilder(AplicacionMapeoRoto.class)
                .web(WebApplicationType.NONE)
                .profiles("test");

        assertThatThrownBy(aplicacion::run)
                .satisfies(error -> {
                    Throwable causa = error;
                    while (causa != null && !(causa instanceof SchemaManagementException)) {
                        causa = causa.getCause();
                    }
                    assertThat(causa).as("SchemaManagementException en la cadena de causas")
                            .isInstanceOf(SchemaManagementException.class);
                    assertThat(causa.getMessage()).contains("tabla_que_no_existe");
                });
    }

    @Test
    void laAplicacionRealNoEscaneaLaEntidadRota() {
        assertThat(entityManager.getMetamodel().getEntities())
                .extracting(EntityType::getName)
                .contains("Turno", "Prestacion")
                .doesNotContain("EntidadSinTabla");
    }
}
