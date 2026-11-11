package com.libreria.servidor;

import com.libreria.servidor.dto.EjemplarInput;
import com.libreria.servidor.dto.LibroInput;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Genero;
import com.libreria.servidor.servicio.ServicioRevisiones;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

/**
 * Base de las pruebas: arranca el servidor completo contra MongoDB real, en la base de pruebas
 * libreria_test, y la deja vacía antes de cada prueba (se borran los documentos, no los índices).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PruebaBase {

    public static final String ISBN_CIEN = "1001";
    public static final String ISBN_MARIA = "1004";

    @Autowired
    protected MongoTemplate mongo;

    @BeforeEach
    void limpiarBase() {
        mongo.getCollection("libros").deleteMany(new org.bson.Document());
        mongo.getCollection("ejemplares").deleteMany(new org.bson.Document());
        mongo.getCollection(ServicioRevisiones.COLECCION).deleteMany(new org.bson.Document());
    }

    protected static LibroInput libro(String isbn, String titulo) {
        return LibroInput.builder()
                .isbn(isbn)
                .titulo(titulo)
                .autor("Gabriel García Márquez")
                .precio(65000)
                .numeroPaginas(496)
                .fechaPublicacion(LocalDateTime.of(1967, 5, 30, 0, 0))
                .genero(Genero.FICCION)
                .editorial("Sudamericana")
                .build();
    }

    protected static EjemplarInput ejemplar(String codigo, String isbnLibro, EstadoEjemplar estado, String ubicacion) {
        return EjemplarInput.builder()
                .codigo(codigo)
                .isbnLibro(isbnLibro)
                .estado(estado)
                .ubicacion(ubicacion)
                .fechaIngreso(LocalDateTime.of(2024, 2, 10, 9, 0))
                .precioReposicion(65000)
                .numeroPrestamos(4)
                .build();
    }
}
