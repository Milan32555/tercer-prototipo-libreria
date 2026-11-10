package com.libreria.servidor.model;

import java.util.List;

/** Consulta maestro + detalle: el libro, sus ejemplares y los conteos por estado. */
public record DetalleLibro(Libro libro, List<Ejemplar> ejemplares, List<ConteoEstado> conteoPorEstado,
                           int totalEjemplares, int totalPrestamos, double valorReposicion) {
}
