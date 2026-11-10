package com.libreria.servidor.repository;

import com.libreria.servidor.model.EjemplarConLibro;
import com.libreria.servidor.model.EstadoEjemplar;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;

/** Consultas de ejemplares escritas por nosotros (agregaciones de MongoDB). */
public interface EjemplarRepositoryCustom {

    /**
     * CONSULTA PERSONALIZADA 1: ejemplares unidos con su libro ($lookup ejemplares.libroId ->
     * libros._id). Devuelve TODOS los atributos del ejemplar más isbn, título y autor del libro.
     * Filtros opcionales: estado y libro; paginada en MongoDB.
     */
    Page<EjemplarConLibro> ejemplaresConLibro(EstadoEjemplar estado, ObjectId libroId, Pageable pageable);

    /** Cuántos ejemplares hay en cada estado ($group). Si libroId no es null, solo de ese libro. */
    Map<EstadoEjemplar, Long> contarPorEstado(ObjectId libroId);

    /** Suma de precioReposicion de todos los ejemplares ($group + $sum). */
    double sumarPrecioReposicion();

    /** Cuántos ejemplares tiene cada libro de la lista ($match $in + $group). */
    Map<ObjectId, Long> contarPorLibros(Collection<ObjectId> librosIds);
}
