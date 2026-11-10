package com.libreria.servidor.model;

/** Cuántos ejemplares hay en un estado y qué porcentaje (0 a 100) representan. */
public record ConteoEstado(EstadoEjemplar estado, int cantidad, double porcentaje) {
}
