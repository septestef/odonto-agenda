package ar.turnotarde.notificacion;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class ConfiguracionDespacho {
    @Bean(defaultCandidate = false)
    ThreadPoolTaskExecutor ejecutorNotificaciones() {
        var ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(2);
        ejecutor.setQueueCapacity(32);
        ejecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ejecutor.setThreadNamePrefix("notificaciones-");
        return ejecutor;
    }
}
