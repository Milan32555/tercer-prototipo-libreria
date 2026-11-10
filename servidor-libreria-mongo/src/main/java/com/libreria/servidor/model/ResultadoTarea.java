package com.libreria.servidor.model;

/** Cómo terminó una de las 3 tareas concurrentes del resumen (en qué hilo y cuánto tardó). */
public record ResultadoTarea(String nombre, String hilo, long duracionMs, boolean exitosa, String mensaje) {
}
