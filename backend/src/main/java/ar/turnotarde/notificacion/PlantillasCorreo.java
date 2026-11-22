package ar.turnotarde.notificacion;

import ar.turnotarde.shared.config.AppProperties;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.eventos.ResumenTurno;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
class PlantillasCorreo {

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu 'a las' HH:mm", Locale.forLanguageTag("es-AR"));
    private static final Pattern CAMPO = Pattern.compile("\\{\\{([a-z]+)}}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]+");
    private final ZonaNegocio zona;
    private final URI base;

    PlantillasCorreo(ZonaNegocio zona, AppProperties propiedades) {
        this.zona = zona;
        this.base = propiedades.baseUrl();
    }

    String renderizar(TipoNotificacion tipo, ResumenTurno turno, String token) {
        var archivo = switch (tipo) {
            case CONFIRMACION -> "confirmacion";
            case CANCELACION -> "cancelacion";
            case REPROGRAMACION -> "reprogramacion";
            case RECORDATORIO -> throw new IllegalStateException("RENDER");
        };
        String link = tipo == TipoNotificacion.CANCELACION ? "" : link(token);
        String direccion = turno.direccionConsultorio();
        var campos = Map.of(
                "fecha", escapar(FECHA_HORA.format(zona.aLocal(turno.inicio()))),
                "prestacion", escapar(turno.prestacionNombre()),
                "direccion", direccion == null || direccion.isBlank()
                        ? "" : "<p>Dirección: " + escapar(direccion) + "</p>",
                "link", escapar(link));
        try {
            String html = new ClassPathResource("templates/mail/" + archivo + ".html")
                    .getContentAsString(StandardCharsets.UTF_8);
            // En una pasada: no reinterpretar delimitadores que estuvieran en el snapshot.
            return CAMPO.matcher(html).replaceAll(campo -> Matcher.quoteReplacement(campos.get(campo.group(1))));
        } catch (IOException ex) {
            throw new IllegalStateException("RENDER");
        }
    }

    private String link(String token) {
        String scheme = base.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || base.getHost() == null || base.getRawUserInfo() != null || base.getRawQuery() != null
                || base.getRawFragment() != null || base.getPort() > 65535 || base.getPort() == 0
                || token == null || !TOKEN.matcher(token).matches()) {
            throw new UrlCorreoInvalida();
        }
        return base.toASCIIString().replaceFirst("/+$", "") + "/turno/" + token;
    }

    private static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto, StandardCharsets.UTF_8.name());
    }

    static final class UrlCorreoInvalida extends IllegalArgumentException {
        UrlCorreoInvalida() {
            super("URL_INVALIDA");
        }
    }
}
