package ar.pruebas.mapeoroto;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * Aplicación mínima de prueba, fuera del paquete base {@code ar.turnotarde} para que ningún otro IT
 * escanee su entidad: mapea una tabla que no existe y por eso no debe poder arrancar.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = EntidadSinTabla.class)
public class AplicacionMapeoRoto {
}
