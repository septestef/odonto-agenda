package ar.turnotarde.notificacion;

import ar.turnotarde.turno.eventos.TurnoCancelado;
import ar.turnotarde.turno.eventos.TurnoReprogramado;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
class NotificacionService {
    private static final Logger LOG = LoggerFactory.getLogger(NotificacionService.class);
    private final Executor ejecutor;
    private final ProcesarAviso procesador;

    NotificacionService(@Qualifier("ejecutorNotificaciones") Executor ejecutor, ProcesarAviso procesador) {
        this.ejecutor = ejecutor;
        this.procesador = procesador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    void reservado(TurnoReservado evento) {
        despachar(() -> procesador.procesar(() -> AvisoCorreo.desde(evento)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    void cancelado(TurnoCancelado evento) {
        despachar(() -> procesador.procesar(() -> AvisoCorreo.desde(evento)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    void reprogramado(TurnoReprogramado evento) {
        despachar(() -> procesador.procesar(() -> AvisoCorreo.desde(evento)));
    }

    private void despachar(Runnable tarea) {
        try {
            ejecutor.execute(tarea);
        } catch (RejectedExecutionException ex) {
            LOG.warn("DESPACHO_RECHAZADO");
        }
    }
}
