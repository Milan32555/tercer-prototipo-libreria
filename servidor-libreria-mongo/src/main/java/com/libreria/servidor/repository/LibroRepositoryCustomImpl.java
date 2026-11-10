package com.libreria.servidor.repository;

import com.libreria.servidor.model.Genero;
import com.libreria.servidor.model.Libro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

/** Implementación de {@link LibroRepositoryCustom} con MongoTemplate y Criteria. */
public class LibroRepositoryCustomImpl implements LibroRepositoryCustom {

    private final MongoTemplate mongo;

    public LibroRepositoryCustomImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Page<Libro> buscarPorPrecioYGenero(Double precioMin, Double precioMax, Genero genero, Pageable pageable) {
        Criteria criterio = new Criteria();
        if (precioMin != null || precioMax != null) {
            Criteria precio = Criteria.where("precio");
            if (precioMin != null) {
                precio = precio.gte(precioMin);
            }
            if (precioMax != null) {
                precio = precio.lte(precioMax);
            }
            criterio = precio;
        }
        if (genero != null) {
            criterio = criterio.and("genero").is(genero.name());
        }

        Query filtro = Query.query(criterio);
        long total = mongo.count(filtro, Libro.class);
        List<Libro> pagina = mongo.find(Query.query(criterio).with(pageable), Libro.class);
        return new PageImpl<>(pagina, pageable, total);
    }
}
