package ar.turnotarde.notificacion;

import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;

class InsercionNotificacionImpl implements InsercionNotificacion {
    private final DataSource fuente;
    private final Clock reloj;

    InsercionNotificacionImpl(DataSource fuente, Clock reloj) {
        this.fuente = fuente;
        this.reloj = reloj;
    }

    @Override
    public void insertarSinDiagnosticoJdbc(Notificacion aviso) {
        // La conexión pertenece a REQUIRES_NEW. Hibernate y JdbcTemplate no ven los parámetros
        // ni el detalle del error de PostgreSQL, que puede incluir la fila rechazada.
        var conexion = DataSourceUtils.getConnection(fuente);
        try (var sql = conexion.prepareStatement("""
                INSERT INTO notificacion (turno_id, tipo, destinatario, enviado_en, estado, error,
                                          creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            var fechaAuditoria = reloj.instant().atOffset(ZoneOffset.UTC);
            sql.setLong(1, aviso.getTurno().getId());
            sql.setString(2, aviso.getTipo().name());
            sql.setString(3, aviso.getDestinatario());
            sql.setObject(4, aviso.getEnviadoEn().atOffset(ZoneOffset.UTC));
            sql.setString(5, aviso.getEstado().name());
            sql.setString(6, aviso.getError());
            sql.setObject(7, fechaAuditoria);
            sql.setObject(8, fechaAuditoria);
            sql.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("REGISTRO_FALLIDO");
        } finally {
            DataSourceUtils.releaseConnection(conexion, fuente);
        }
    }
}
