package ar.turnotarde.publico;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Protege también respuestas que no llegan al controlador, sin tocar las rutas compartidas con C-07. */
@Component
class HeadersGestionTurno extends OncePerRequestFilter {
    private static final String PUBLICO = "/api/public/turnos/";
    private static final PathPatternParser PATHS = PathPatternParser.defaultInstance;
    private static final List<PathPattern> GESTION = List.of(
            PATHS.parse(PUBLICO + "{segmento}"), PATHS.parse(PUBLICO + "{segmento}/cancelar"),
            PATHS.parse(PUBLICO + "{segmento}/reprogramar"), PATHS.parse("/turno/{segmento}"));
    private static final List<PathPattern> C07 = List.of(
            PATHS.parse(PUBLICO + "disponibilidad"), PATHS.parse(PUBLICO + "turnos"));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var pathPrevia = request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
        var path = ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
        ServletRequestPathUtils.setParsedRequestPath((RequestPath) pathPrevia, request);
        if (GESTION.stream().anyMatch(patron -> patron.matches(path))
                && C07.stream().noneMatch(patron -> patron.matches(path))) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
        }
        chain.doFilter(request, response);
    }
}
