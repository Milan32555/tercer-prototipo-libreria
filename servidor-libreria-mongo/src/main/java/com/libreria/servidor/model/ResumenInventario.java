package com.libreria.servidor.model;

import java.util.List;

/** Resumen del inventario para la ventana principal; lo calculan 3 tareas en paralelo. */
public record ResumenInventario(int totalLibros, int totalEjemplares, List<ConteoEstado> ejemplaresPorEstado,
                                double valorInventario, long duracionMs, List<ResultadoTarea> tareas) {
}
