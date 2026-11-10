package com.libreria.servidor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Colección A (maestro): un documento de la colección "libros".
 * Un libro tiene 0..* ejemplares (colección "ejemplares", que lo referencian por libroId).
 * Se construye con el patrón Builder de Lombok: {@code Libro.builder().isbn(..).titulo(..).build()}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("libros")
@CompoundIndex(name = "genero_precio", def = "{'genero': 1, 'precio': 1}")
public class Libro {

    /** Llave primaria: el _id (ObjectId) que asigna MongoDB. */
    @Id
    private String id;

    /** Obligatorio y único (el índice único resuelve dos altas simultáneas del mismo ISBN). */
    @Indexed(unique = true)
    private String isbn;

    /** Obligatorio. */
    private String titulo;

    /** Obligatorio. */
    @Indexed
    private String autor;

    /** Obligatorio, mayor que cero. */
    @Indexed
    private double precio;

    /** Obligatorio, mayor que cero. */
    private int numeroPaginas;

    /** Obligatoria, no futura. */
    private LocalDateTime fechaPublicacion;

    /** Obligatorio. */
    @Indexed
    private Genero genero;

    /** Opcional. */
    private String editorial;

    /** Opcional, máximo 500 caracteres. */
    private String sinopsis;

    /** Concurrencia optimista: Mongo solo guarda si nadie cambió el documento desde que se leyó. */
    @Version
    private Long version;
}
