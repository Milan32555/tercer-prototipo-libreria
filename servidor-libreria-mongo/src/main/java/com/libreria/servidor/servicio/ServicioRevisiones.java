package com.libreria.servidor.servicio;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Número de revisión de cada colección, guardado en MongoDB (colección "revisiones",
 * un documento por colección: { _id: "libros", valor: 15 }). Aumenta con $inc cada vez que la
 * colección cambia, así que lo comparten todos los clientes y sobrevive a reinicios del servidor.
 * El Observer de los clientes lo consulta para saber si debe volver a cargar su grilla.
 */
@Service
public class ServicioRevisiones {

    public static final String COLECCION = "revisiones";

    private static final Logger log = LoggerFactory.getLogger(ServicioRevisiones.class);

    private final MongoTemplate mongo;

    public ServicioRevisiones(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Suma 1 a la revisión de la colección (upsert: crea el contador si no existe). */
    public void incrementar(String coleccion) {
        try {
            mongo.upsert(Query.query(Criteria.where("_id").is(coleccion)), new Update().inc("valor", 1), COLECCION);
        } catch (DataAccessException e) {
            log.error("No se pudo aumentar la revisión de {}", coleccion, e);
        }
    }

    public long valor(String coleccion) {
        try {
            Document contador = mongo.findById(coleccion, Document.class, COLECCION);
            return contador == null ? 0 : ((Number) contador.get("valor")).longValue();
        } catch (DataAccessException e) {
            log.error("No se pudo leer la revisión de {}", coleccion, e);
            return -1;
        }
    }
}
