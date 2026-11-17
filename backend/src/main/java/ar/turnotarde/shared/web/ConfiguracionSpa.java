package ar.turnotarde.shared.web;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Sirve la SPA desde el mismo origen que la API. Si el recurso pedido no existe y la ruta no es de la API,
 * devuelve {@code index.html} para que el router del frontend resuelva la ruta.
 */
@Configuration
public class ConfiguracionSpa implements WebMvcConfigurer {

    private static final String INDEX = "index.html";

    private final String ubicacion;

    public ConfiguracionSpa(@Value("${app.frontend.ubicacion:classpath:/static/}") String ubicacion) {
        this.ubicacion = ubicacion.endsWith("/") ? ubicacion : ubicacion + "/";
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/" + INDEX);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(ubicacion)
                .resourceChain(true)
                .addResolver(new FallbackSpaResolver());
    }

    /** Devuelve el archivo si existe; si no, {@code index.html}, salvo para {@code api/**} (404 del advice). */
    static final class FallbackSpaResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String rutaRecurso, Resource ubicacionBase) throws IOException {
            Resource pedido = ubicacionBase.createRelative(rutaRecurso);
            if (pedido.exists() && pedido.isReadable()) {
                return pedido;
            }
            if (rutaRecurso.equals("api") || rutaRecurso.startsWith("api/")) {
                return null;
            }
            Resource index = ubicacionBase.createRelative(INDEX);
            return index.exists() && index.isReadable() ? index : null;
        }
    }
}
