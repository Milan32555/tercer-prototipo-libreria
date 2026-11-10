package com.libreria.servidor.repository;

import com.libreria.servidor.model.Ejemplar;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de la colección "ejemplares". Las agregaciones ($lookup y conteos) están en
 * {@link EjemplarRepositoryCustomImpl}.
 */
public interface EjemplarRepository extends MongoRepository<Ejemplar, String>, EjemplarRepositoryCustom {

    Optional<Ejemplar> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    long countByLibroId(ObjectId libroId);

    List<Ejemplar> findByLibroId(ObjectId libroId, Sort sort);

    /**
     * Búsqueda individual por ISBN del libro (ya traducido a libroId) + ubicación, sin distinguir
     * mayúsculas ni tildes. Usa el índice compuesto (libroId, ubicacion).
     */
    @Query(value = "{ 'libroId': ?0, 'ubicacion': ?1 }", collation = "{ 'locale': 'es', 'strength': 1 }",
            sort = "{ 'codigo': 1 }")
    List<Ejemplar> findByLibroIdAndUbicacion(ObjectId libroId, String ubicacion);
}
