package com.libreria.servidor.repository;

import com.libreria.servidor.model.Libro;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de la colección "libros" (Spring Data MongoDB). Los métodos con nombre derivado los
 * implementa Spring Data; la consulta de precio y género está en {@link LibroRepositoryCustomImpl}.
 */
public interface LibroRepository extends MongoRepository<Libro, String>, LibroRepositoryCustom {

    Optional<Libro> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    /**
     * Búsqueda por título exacto sin distinguir mayúsculas ni tildes: la collation "es" con
     * strength 1 compara "maria" igual que "María".
     */
    @Query(value = "{ 'titulo': ?0 }", collation = "{ 'locale': 'es', 'strength': 1 }", sort = "{ 'isbn': 1 }")
    List<Libro> findByTituloIgnoreCase(String titulo);
}
