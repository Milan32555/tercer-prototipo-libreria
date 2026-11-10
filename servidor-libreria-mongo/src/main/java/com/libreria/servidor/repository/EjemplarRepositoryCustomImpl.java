package com.libreria.servidor.repository;

import com.libreria.servidor.model.EjemplarConLibro;
import com.libreria.servidor.model.EstadoEjemplar;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Implementación de {@link EjemplarRepositoryCustom} con el framework de agregación de MongoDB. */
public class EjemplarRepositoryCustomImpl implements EjemplarRepositoryCustom {

    private static final String EJEMPLARES = "ejemplares";

    private final MongoTemplate mongo;

    public EjemplarRepositoryCustomImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Page<EjemplarConLibro> ejemplaresConLibro(EstadoEjemplar estado, ObjectId libroId, Pageable pageable) {
        Document filtro = new Document();
        if (estado != null) {
            filtro.append("estado", estado.name());
        }
        if (libroId != null) {
            filtro.append("libroId", libroId);
        }

        // El $lookup se hace después de paginar: solo se unen los ejemplares de la página pedida.
        List<AggregationOperation> etapas = List.of(
                etapa("$match", filtro),
                etapa("$sort", new Document("codigo", 1)),
                etapa("$skip", pageable.getOffset()),
                etapa("$limit", pageable.getPageSize()),
                etapa("$lookup", new Document("from", "libros")
                        .append("localField", "libroId")
                        .append("foreignField", "_id")
                        .append("as", "libro")),
                etapa("$unwind", new Document("path", "$libro").append("preserveNullAndEmptyArrays", true)),
                etapa("$project", new Document("_id", 1)
                        .append("codigo", 1)
                        .append("estado", 1)
                        .append("ubicacion", 1)
                        .append("fechaIngreso", 1)
                        .append("precioReposicion", 1)
                        .append("numeroPrestamos", 1)
                        .append("observaciones", 1)
                        .append("version", 1)
                        .append("libroId", new Document("$toString", "$libroId"))
                        .append("isbnLibro", "$libro.isbn")
                        .append("tituloLibro", "$libro.titulo")
                        .append("autorLibro", "$libro.autor")));

        List<EjemplarConLibro> pagina = mongo
                .aggregate(Aggregation.newAggregation(etapas), EJEMPLARES, EjemplarConLibro.class)
                .getMappedResults();
        long total = mongo.getCollection(EJEMPLARES).countDocuments(filtro);
        return new PageImpl<>(pagina, pageable, total);
    }

    @Override
    public Map<EstadoEjemplar, Long> contarPorEstado(ObjectId libroId) {
        Document filtro = libroId == null ? new Document() : new Document("libroId", libroId);
        List<AggregationOperation> etapas = List.of(
                etapa("$match", filtro),
                etapa("$group", new Document("_id", "$estado").append("cantidad", new Document("$sum", 1))));

        Map<EstadoEjemplar, Long> conteo = new EnumMap<>(EstadoEjemplar.class);
        for (EstadoEjemplar estado : EstadoEjemplar.values()) {
            conteo.put(estado, 0L);
        }
        for (Document fila : mongo.aggregate(Aggregation.newAggregation(etapas), EJEMPLARES, Document.class)) {
            conteo.put(EstadoEjemplar.valueOf(fila.getString("_id")), ((Number) fila.get("cantidad")).longValue());
        }
        return conteo;
    }

    @Override
    public double sumarPrecioReposicion() {
        List<AggregationOperation> etapas = List.of(
                etapa("$group", new Document("_id", null).append("total", new Document("$sum", "$precioReposicion"))));
        Document fila = mongo.aggregate(Aggregation.newAggregation(etapas), EJEMPLARES, Document.class)
                .getUniqueMappedResult();
        return fila == null ? 0 : ((Number) fila.get("total")).doubleValue();
    }

    @Override
    public Map<ObjectId, Long> contarPorLibros(Collection<ObjectId> librosIds) {
        Map<ObjectId, Long> conteo = new HashMap<>();
        if (librosIds.isEmpty()) {
            return conteo;
        }
        List<AggregationOperation> etapas = List.of(
                etapa("$match", new Document("libroId", new Document("$in", new ArrayList<>(librosIds)))),
                etapa("$group", new Document("_id", "$libroId").append("cantidad", new Document("$sum", 1))));
        for (Document fila : mongo.aggregate(Aggregation.newAggregation(etapas), EJEMPLARES, Document.class)) {
            conteo.put(fila.getObjectId("_id"), ((Number) fila.get("cantidad")).longValue());
        }
        return conteo;
    }

    /** Una etapa de la tubería de agregación escrita tal como se escribe en mongosh. */
    private static AggregationOperation etapa(String operador, Object valor) {
        return contexto -> new Document(operador, valor);
    }
}
