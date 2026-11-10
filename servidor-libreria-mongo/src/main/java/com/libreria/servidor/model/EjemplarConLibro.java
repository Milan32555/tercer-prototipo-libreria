package com.libreria.servidor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Fila de la grilla de Listar Ejemplares: TODOS los atributos del ejemplar más el ISBN, el título
 * y el autor de su libro. La arma la agregación $lookup de
 * {@link com.libreria.servidor.repository.EjemplarRepositoryCustomImpl#ejemplaresConLibro}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EjemplarConLibro {
    private String id;
    private String codigo;
    private String libroId;
    private String isbnLibro;
    private String tituloLibro;
    private String autorLibro;
    private EstadoEjemplar estado;
    private String ubicacion;
    private LocalDateTime fechaIngreso;
    private double precioReposicion;
    private int numeroPrestamos;
    private String observaciones;
    private Long version;
}
