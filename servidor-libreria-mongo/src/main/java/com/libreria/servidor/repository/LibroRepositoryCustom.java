package com.libreria.servidor.repository;

import com.libreria.servidor.model.Genero;
import com.libreria.servidor.model.Libro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Consultas de libros escritas por nosotros (no derivadas del nombre del método). */
public interface LibroRepositoryCustom {

    /**
     * CONSULTA PERSONALIZADA 2: libros con precio entre min y max y, si se indica, de un género.
     * El filtro y la paginación los hace MongoDB (Criteria + skip/limit).
     */
    Page<Libro> buscarPorPrecioYGenero(Double precioMin, Double precioMax, Genero genero, Pageable pageable);
}
