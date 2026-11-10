package com.libreria.servidor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Grupo de hilos propio para las tareas concurrentes (los hilos se llaman tarea-1, tarea-2...). */
@Configuration
public class EjecutorTareasConfig {

    @Bean(name = "ejecutorTareas")
    public ThreadPoolTaskExecutor ejecutorTareas() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(3);
        ejecutor.setMaxPoolSize(6);
        ejecutor.setQueueCapacity(100);
        ejecutor.setThreadNamePrefix("tarea-");
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.initialize();
        return ejecutor;
    }
}
