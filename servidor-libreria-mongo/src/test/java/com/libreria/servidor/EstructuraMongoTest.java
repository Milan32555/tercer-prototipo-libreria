package com.libreria.servidor;

import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.servicio.ServicioEjemplar;
import com.libreria.servidor.servicio.ServicioLibro;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la base NO relacional: las dos colecciones, sus índices y que ejemplares.libroId es un
 * ObjectId que apunta a libros._id.
 */
class EstructuraMongoTest extends PruebaBase {

    @Autowired
    ServicioLibro servicioLibro;

    @Autowired
    ServicioEjemplar servicioEjemplar;

    @Test
    void existenLasDosColeccionesConSusIndices() {
        servicioLibro.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        servicioEjemplar.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));

        assertThat(mongo.getCollectionNames()).contains("libros", "ejemplares");

        Map<String, Document> librosIdx = indices("libros");
        assertThat(librosIdx.get("isbn").getBoolean("unique", false)).isTrue();
        assertThat(librosIdx).containsKeys("autor", "precio", "genero", "genero_precio");

        Map<String, Document> ejemplaresIdx = indices("ejemplares");
        assertThat(ejemplaresIdx.get("codigo").getBoolean("unique", false)).isTrue();
        assertThat(ejemplaresIdx).containsKeys("libroId", "estado", "libro_ubicacion");
        assertThat(ejemplaresIdx.get("libro_ubicacion").get("key", Document.class).keySet())
                .containsExactly("libroId", "ubicacion");
    }

    @Test
    void libroIdEsUnObjectIdQueApuntaAlIdDelLibro() {
        Libro libro = servicioLibro.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        servicioEjemplar.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));

        Document ejemplar = mongo.getCollection("ejemplares").find().first();
        Document libroGuardado = mongo.getCollection("libros").find().first();

        assertThat(ejemplar).isNotNull();
        assertThat(libroGuardado).isNotNull();
        assertThat(ejemplar.get("libroId")).isInstanceOf(ObjectId.class);
        assertThat(libroGuardado.get("_id")).isInstanceOf(ObjectId.class);
        assertThat(ejemplar.get("libroId")).isEqualTo(libroGuardado.get("_id"));
        assertThat(libroGuardado.getObjectId("_id").toHexString()).isEqualTo(libro.getId());
        assertThat(libroGuardado.get("fechaPublicacion")).isInstanceOf(java.util.Date.class);
        assertThat(libroGuardado.get("version")).isEqualTo(0L);
    }

    private Map<String, Document> indices(String coleccion) {
        List<Document> lista = mongo.getCollection(coleccion).listIndexes().into(new ArrayList<>());
        return lista.stream().collect(Collectors.toMap(d -> d.getString("name"), d -> d));
    }
}
