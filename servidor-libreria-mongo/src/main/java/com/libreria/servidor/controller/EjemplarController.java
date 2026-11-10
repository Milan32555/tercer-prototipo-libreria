package com.libreria.servidor.controller;

import com.libreria.servidor.dto.EjemplarInput;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EjemplarConLibro;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.model.Pagina;
import com.libreria.servidor.servicio.ServicioEjemplar;
import com.libreria.servidor.servicio.ServicioLibro;
import com.libreria.servidor.servicio.ServicioRevisiones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller GraphQL de Ejemplar. El cliente envía y recibe el ISBN del libro; la llave foránea
 * real (libroId, un ObjectId) la manejan el servicio y estos campos calculados.
 */
@Controller
public class EjemplarController {

    private static final Logger log = LoggerFactory.getLogger(EjemplarController.class);

    private final ServicioEjemplar servicioEjemplar;
    private final ServicioLibro servicioLibro;
    private final ServicioRevisiones revisiones;

    public EjemplarController(ServicioEjemplar servicioEjemplar, ServicioLibro servicioLibro,
                              ServicioRevisiones revisiones) {
        this.servicioEjemplar = servicioEjemplar;
        this.servicioLibro = servicioLibro;
        this.revisiones = revisiones;
    }

    // ---------- Consultas ----------

    @QueryMapping
    public Pagina<Ejemplar> ejemplares(@Argument int pagina, @Argument int tamanio) {
        log.info("GraphQL ejemplares(pagina={}, tamanio={})", pagina, tamanio);
        return servicioEjemplar.listar(pagina, tamanio);
    }

    @QueryMapping
    public Pagina<EjemplarConLibro> ejemplaresConLibro(@Argument EstadoEjemplar estado, @Argument String isbn,
                                                       @Argument int pagina, @Argument int tamanio) {
        log.info("GraphQL ejemplaresConLibro(estado={}, isbn={}, pagina={}, tamanio={})", estado, isbn, pagina, tamanio);
        return servicioEjemplar.listarConLibro(estado, isbn, pagina, tamanio);
    }

    @QueryMapping
    public Optional<Ejemplar> ejemplarPorCodigo(@Argument String codigo) {
        log.info("GraphQL ejemplarPorCodigo(codigo={})", codigo);
        return servicioEjemplar.buscarPorCodigo(codigo);
    }

    @QueryMapping
    public List<Ejemplar> ejemplaresPorLibroYUbicacion(@Argument String isbn, @Argument String ubicacion) {
        log.info("GraphQL ejemplaresPorLibroYUbicacion(isbn={}, ubicacion={})", isbn, ubicacion);
        return servicioEjemplar.buscarPorLibroYUbicacion(isbn, ubicacion);
    }

    @QueryMapping
    public long revisionEjemplares() {
        return revisiones.valor("ejemplares");
    }

    // ---------- Mutaciones ----------

    @MutationMapping
    public Ejemplar crearEjemplar(@Argument EjemplarInput input) {
        log.info("GraphQL crearEjemplar(codigo={}, isbnLibro={})", input.getCodigo(), input.getIsbnLibro());
        return servicioEjemplar.crear(input);
    }

    @MutationMapping
    public Ejemplar actualizarEjemplar(@Argument EjemplarInput input) {
        log.info("GraphQL actualizarEjemplar(codigo={})", input.getCodigo());
        return servicioEjemplar.actualizar(input);
    }

    @MutationMapping
    public Ejemplar eliminarEjemplar(@Argument String codigo) {
        log.info("GraphQL eliminarEjemplar(codigo={})", codigo);
        return servicioEjemplar.eliminar(codigo);
    }

    // ---------- Campos calculados de Ejemplar ----------

    /** Ejemplar.libroId: el ObjectId de la llave foránea, como texto hexadecimal. */
    @SchemaMapping(typeName = "Ejemplar", field = "libroId")
    public String libroId(Ejemplar ejemplar) {
        return ejemplar.getLibroId().toHexString();
    }

    /** Ejemplar.libro de todos los ejemplares de la respuesta con una sola consulta a "libros". */
    @BatchMapping(typeName = "Ejemplar", field = "libro")
    public Map<Ejemplar, Libro> libro(List<Ejemplar> ejemplares) {
        Map<String, Libro> libros = servicioLibro.porIds(
                ejemplares.stream().map(e -> e.getLibroId().toHexString()).distinct().toList());
        Map<Ejemplar, Libro> resultado = new LinkedHashMap<>();
        for (Ejemplar ejemplar : ejemplares) {
            resultado.put(ejemplar, libros.get(ejemplar.getLibroId().toHexString()));
        }
        return resultado;
    }

    /** Ejemplar.isbnLibro: el ISBN del libro al que apunta libroId. */
    @BatchMapping(typeName = "Ejemplar", field = "isbnLibro")
    public Map<Ejemplar, String> isbnLibro(List<Ejemplar> ejemplares) {
        Map<String, Libro> libros = servicioLibro.porIds(
                ejemplares.stream().map(e -> e.getLibroId().toHexString()).distinct().toList());
        Map<Ejemplar, String> resultado = new LinkedHashMap<>();
        for (Ejemplar ejemplar : ejemplares) {
            Libro libro = libros.get(ejemplar.getLibroId().toHexString());
            resultado.put(ejemplar, libro == null ? "" : libro.getIsbn());
        }
        return resultado;
    }
}
