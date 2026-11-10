package com.libreria.servidor.servicio;

import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.LibreriaException.Tipo;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Genero;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.model.ResultadoCarga;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Genera muchos datos para las pruebas de carga (1.000 libros y 5.000 ejemplares por defecto).
 * Inserta por lotes con insertMany, directamente en las colecciones. Los libros generados tienen
 * ISBN 979-1xxxxxxxxx y los ejemplares códigos EJ-1xxxxx, así se distinguen de los reales.
 * Solo se puede usar en desarrollo (perfil "carga").
 */
@Service
public class GeneradorDatosCarga {

    private static final Logger log = LoggerFactory.getLogger(GeneradorDatosCarga.class);
    private static final String PREFIJO_ISBN = "979-1";
    private static final int BASE_CODIGO = 100_000;
    private static final int LOTE = 1_000;

    private final MongoTemplate mongo;
    private final ServicioRevisiones revisiones;

    public GeneradorDatosCarga(MongoTemplate mongo, ServicioRevisiones revisiones) {
        this.mongo = mongo;
        this.revisiones = revisiones;
    }

    public ResultadoCarga generar(int cantidadLibros, int cantidadEjemplares) {
        if (cantidadLibros < 1 || cantidadLibros > 50_000 || cantidadEjemplares < 0 || cantidadEjemplares > 200_000) {
            throw new LibreriaException(Tipo.VALIDACION,
                    "Se pueden generar entre 1 y 50.000 libros y entre 0 y 200.000 ejemplares.");
        }
        long inicio = System.nanoTime();
        log.info("Generando datos de carga: {} libros y {} ejemplares", cantidadLibros, cantidadEjemplares);
        try {
            long librosPrevios = mongo.count(Query.query(Criteria.where("isbn").regex("^" + PREFIJO_ISBN)), Libro.class);
            long ejemplaresPrevios = mongo.count(Query.query(Criteria.where("codigo").regex("^EJ-1\\d{5}$")),
                    Ejemplar.class);
            if (BASE_CODIGO + ejemplaresPrevios + cantidadEjemplares > 999_999) {
                throw new LibreriaException(Tipo.VALIDACION, "Ya hay demasiados ejemplares de carga generados.");
            }

            List<ObjectId> librosIds = new ArrayList<>(cantidadLibros);
            List<Document> lote = new ArrayList<>(LOTE);
            for (int i = 0; i < cantidadLibros; i++) {
                long n = librosPrevios + i;
                ObjectId id = new ObjectId();
                librosIds.add(id);
                Libro libro = Libro.builder()
                        .id(id.toHexString())
                        .isbn(PREFIJO_ISBN + String.format("%09d", n))
                        .titulo("Libro de carga " + (n + 1))
                        .autor("Autor de carga " + (n % 50 + 1))
                        .precio(20_000 + (n * 137) % 180_000)
                        .numeroPaginas(80 + (int) (n % 900))
                        .fechaPublicacion(LocalDateTime.of(1950 + (int) (n % 75), 1 + (int) (n % 12), 1 + (int) (n % 28), 0, 0))
                        .genero(Genero.values()[(int) (n % Genero.values().length)])
                        .editorial(n % 3 == 0 ? "Editorial " + (n % 10 + 1) : null)
                        .sinopsis(n % 5 == 0 ? "Sinopsis generada para la prueba de carga." : null)
                        .version(0L)
                        .build();
                lote.add(documento(libro));
                if (lote.size() == LOTE) {
                    mongo.getCollection("libros").insertMany(lote);
                    lote.clear();
                }
            }
            if (!lote.isEmpty()) {
                mongo.getCollection("libros").insertMany(lote);
                lote.clear();
            }

            for (int j = 0; j < cantidadEjemplares; j++) {
                long n = ejemplaresPrevios + j;
                Ejemplar ejemplar = Ejemplar.builder()
                        .id(new ObjectId().toHexString())
                        .codigo("EJ-" + (BASE_CODIGO + n))
                        .libroId(librosIds.get(j % cantidadLibros))
                        .estado(EstadoEjemplar.values()[(int) (n % EstadoEjemplar.values().length)])
                        .ubicacion("Estante " + (char) ('A' + n % 6) + (n % 9 + 1))
                        .fechaIngreso(LocalDateTime.of(2020 + (int) (n % 6), 1 + (int) (n % 12), 1 + (int) (n % 28), 9, 0))
                        .precioReposicion(15_000 + (n * 91) % 150_000)
                        .numeroPrestamos((int) (n % 20))
                        .observaciones(n % 7 == 0 ? "Generado para la prueba de carga" : null)
                        .version(0L)
                        .build();
                lote.add(documento(ejemplar));
                if (lote.size() == LOTE) {
                    mongo.getCollection("ejemplares").insertMany(lote);
                    lote.clear();
                }
            }
            if (!lote.isEmpty()) {
                mongo.getCollection("ejemplares").insertMany(lote);
            }

            revisiones.incrementar("libros");
            revisiones.incrementar("ejemplares");
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            log.info("Datos de carga generados en {} ms", ms);
            return new ResultadoCarga(cantidadLibros, cantidadEjemplares, ms);
        } catch (LibreriaException e) {
            log.warn("Generación de datos de carga rechazada: {}", e.getMessage());
            throw e;
        } catch (DataAccessException | com.mongodb.MongoException e) {
            log.error("Error de MongoDB generando los datos de carga", e);
            throw new LibreriaException(Tipo.ERROR_INTERNO, "No se pudieron generar los datos de carga.", e);
        }
    }

    /** Convierte el objeto en el documento BSON tal como lo guardaría Spring Data. */
    private Document documento(Object objeto) {
        Document documento = new Document();
        mongo.getConverter().write(objeto, documento);
        documento.remove("_class");
        return documento;
    }
}
