package com.libreria.servidor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Colección B (detalle): un documento de la colección "ejemplares".
 * Cada ejemplar pertenece a exactamente un libro: libroId guarda el ObjectId del _id del libro
 * (llave foránea). Se construye con {@code Ejemplar.builder()...build()} (patrón Builder).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("ejemplares")
@CompoundIndex(name = "libro_ubicacion", def = "{'libroId': 1, 'ubicacion': 1}")
public class Ejemplar {

    /** Llave primaria: el _id (ObjectId) que asigna MongoDB. */
    @Id
    private String id;

    /** Obligatorio y único, formato EJ-000. */
    @Indexed(unique = true)
    private String codigo;

    /** Obligatorio. Llave foránea: ObjectId del documento del libro en la colección "libros". */
    @Indexed
    private ObjectId libroId;

    /** Obligatorio. */
    @Indexed
    private EstadoEjemplar estado;

    /** Obligatoria. */
    private String ubicacion;

    /** Obligatoria, no futura. */
    private LocalDateTime fechaIngreso;

    /** Obligatorio, mayor que cero. */
    private double precioReposicion;

    /** Obligatorio, no negativo. El servidor le suma 1 cada vez que el ejemplar pasa a PRESTADO. */
    private int numeroPrestamos;

    /** Opcional. */
    private String observaciones;

    /** Concurrencia optimista. */
    @Version
    private Long version;
}
