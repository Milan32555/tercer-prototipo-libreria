package com.libreria.servidor.controller;

import com.libreria.servidor.dto.LibroInput;
import com.libreria.servidor.model.DetalleLibro;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.Genero;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.model.Pagina;
import com.libreria.servidor.servicio.ServicioLibro;
import com.libreria.servidor.servicio.ServicioRevisiones;
import org.bson.types.ObjectId;
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
 * Controller GraphQL (capa Controlador de MVC) de Libro: traduce entre GraphQL y ServicioLibro.
 * No tiene reglas de negocio.
 */
@Controller
public class LibroController {

    private static final Logger log = LoggerFactory.getLogger(LibroController.class);

    private final ServicioLibro servicioLibro;
    private final ServicioRevisiones revisiones;

    public LibroController(ServicioLibro servicioLibro, ServicioRevisiones revisiones) {
        this.servicioLibro = servicioLibro;
        this.revisiones = revisiones;
    }

    // ---------- Consultas ----------

    @QueryMapping
    public Pagina<Libro> libros(@Argument int pagina, @Argument int tamanio) {
        log.info("GraphQL libros(pagina={}, tamanio={})", pagina, tamanio);
        return servicioLibro.listar(pagina, tamanio);
    }

    @QueryMapping
    public Pagina<Libro> librosPorPrecioYGenero(@Argument Double precioMin, @Argument Double precioMax,
                                               @Argument Genero genero, @Argument int pagina, @Argument int tamanio) {
        log.info("GraphQL librosPorPrecioYGenero(min={}, max={}, genero={}, pagina={}, tamanio={})",
                precioMin, precioMax, genero, pagina, tamanio);
        return servicioLibro.listarPorPrecioYGenero(precioMin, precioMax, genero, pagina, tamanio);
    }

    @QueryMapping
    public Optional<Libro> libroPorIsbn(@Argument String isbn) {
        log.info("GraphQL libroPorIsbn(isbn={})", isbn);
        return servicioLibro.buscarPorIsbn(isbn);
    }

    @QueryMapping
    public Optional<Libro> libroPorTitulo(@Argument String titulo) {
        log.info("GraphQL libroPorTitulo(titulo={})", titulo);
        return servicioLibro.buscarPorTitulo(titulo);
    }

    @QueryMapping
    public Optional<DetalleLibro> detalleLibro(@Argument String isbn) {
        log.info("GraphQL detalleLibro(isbn={})", isbn);
        return servicioLibro.detalle(isbn);
    }

    @QueryMapping
    public long revisionLibros() {
        return revisiones.valor("libros");
    }

    // ---------- Mutaciones ----------

    @MutationMapping
    public Libro crearLibro(@Argument LibroInput input) {
        log.info("GraphQL crearLibro(isbn={})", input.getIsbn());
        return servicioLibro.crear(input);
    }

    @MutationMapping
    public Libro actualizarLibro(@Argument LibroInput input) {
        log.info("GraphQL actualizarLibro(isbn={})", input.getIsbn());
        return servicioLibro.actualizar(input);
    }

    @MutationMapping
    public Libro eliminarLibro(@Argument String isbn) {
        log.info("GraphQL eliminarLibro(isbn={})", isbn);
        return servicioLibro.eliminar(isbn);
    }

    // ---------- Campos calculados de Libro ----------

    /** Libro.ejemplares: los ejemplares cuyo libroId es el _id de este libro. */
    @SchemaMapping(typeName = "Libro", field = "ejemplares")
    public List<Ejemplar> ejemplares(Libro libro) {
        return servicioLibro.ejemplaresDe(libro);
    }

    /** Libro.cantidadEjemplares para toda una página de libros con una sola agregación. */
    @BatchMapping(typeName = "Libro", field = "cantidadEjemplares")
    public Map<Libro, Integer> cantidadEjemplares(List<Libro> libros) {
        Map<ObjectId, Long> conteo = servicioLibro.contarEjemplares(
                libros.stream().map(l -> new ObjectId(l.getId())).toList());
        Map<Libro, Integer> resultado = new LinkedHashMap<>();
        for (Libro libro : libros) {
            resultado.put(libro, conteo.getOrDefault(new ObjectId(libro.getId()), 0L).intValue());
        }
        return resultado;
    }
}
