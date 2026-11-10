package com.libreria.servidor.servicio;

import com.libreria.servidor.dto.LibroInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.LibreriaException.Tipo;
import com.libreria.servidor.model.ConteoEstado;
import com.libreria.servidor.model.DetalleLibro;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Genero;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.model.Pagina;
import com.libreria.servidor.repository.EjemplarRepository;
import com.libreria.servidor.repository.LibroRepository;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lógica de negocio del CRUD de Libro (capa Modelo de MVC). Todos los datos están en MongoDB:
 * no hay listas en memoria. Registro (logs): info = operación y resultado; warn = rechazo de
 * negocio (no existe, duplicado, regla, conflicto de versión); error = falla inesperada de Mongo.
 * Las validaciones de los datos (Bean Validation) se aplican al llamar con {@code @Valid}.
 */
@Service
@Validated
public class ServicioLibro {

    private static final Logger log = LoggerFactory.getLogger(ServicioLibro.class);
    private static final Sort ORDEN = Sort.by("titulo").and(Sort.by("isbn"));

    private final LibroRepository libros;
    private final EjemplarRepository ejemplares;

    public ServicioLibro(LibroRepository libros, EjemplarRepository ejemplares) {
        this.libros = libros;
        this.ejemplares = ejemplares;
    }

    // ------------------------------------------------------------------ Listar

    /** Listar todos los libros (paginado, ordenados por título). */
    public Pagina<Libro> listar(int pagina, int tamanio) {
        try {
            Pageable pageable = Paginacion.crear(pagina, tamanio, ORDEN);
            Pagina<Libro> resultado = Pagina.de(libros.findAll(pageable));
            log.info("Listar libros: página {} con {} de {} libros", pagina, resultado.contenido().size(),
                    resultado.totalElementos());
            return resultado;
        } catch (LibreriaException e) {
            log.warn("Listar libros rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("listar los libros", e);
        }
    }

    /** Listar por filtro (consulta personalizada 2): rango de precio y género, filtrado en MongoDB. */
    public Pagina<Libro> listarPorPrecioYGenero(Double precioMin, Double precioMax, Genero genero,
                                                int pagina, int tamanio) {
        try {
            if ((precioMin != null && precioMin < 0) || (precioMax != null && precioMax < 0)) {
                throw new LibreriaException(Tipo.VALIDACION, "Los precios del filtro no pueden ser negativos.");
            }
            if (precioMin != null && precioMax != null && precioMin > precioMax) {
                throw new LibreriaException(Tipo.VALIDACION, "El precio mínimo no puede ser mayor que el máximo.");
            }
            Pageable pageable = Paginacion.crear(pagina, tamanio, ORDEN);
            Pagina<Libro> resultado = Pagina.de(libros.buscarPorPrecioYGenero(precioMin, precioMax, genero, pageable));
            log.info("Libros por precio [{} - {}] y género {}: {} encontrados", precioMin, precioMax, genero,
                    resultado.totalElementos());
            return resultado;
        } catch (LibreriaException e) {
            log.warn("Filtro de libros rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("filtrar los libros", e);
        }
    }

    // ------------------------------------------------------------------ Consultar

    /** Búsqueda individual, criterio 1: ISBN. */
    public Optional<Libro> buscarPorIsbn(String isbn) {
        String buscado = Paginacion.limpiar(isbn);
        if (buscado == null) {
            log.warn("Consultar libro por ISBN: no se indicó el ISBN");
            return Optional.empty();
        }
        try {
            Optional<Libro> libro = libros.findByIsbn(buscado);
            registrarBusqueda("ISBN", buscado, libro.isPresent());
            return libro;
        } catch (DataAccessException e) {
            throw errorBaseDatos("buscar el libro", e);
        }
    }

    /** Búsqueda individual, criterio 2: título exacto, sin distinguir mayúsculas ni tildes. */
    public Optional<Libro> buscarPorTitulo(String titulo) {
        String buscado = Paginacion.limpiar(titulo);
        if (buscado == null) {
            log.warn("Consultar libro por título: no se indicó el título");
            return Optional.empty();
        }
        try {
            Optional<Libro> libro = libros.findByTituloIgnoreCase(buscado).stream().findFirst();
            registrarBusqueda("título", buscado, libro.isPresent());
            return libro;
        } catch (DataAccessException e) {
            throw errorBaseDatos("buscar el libro", e);
        }
    }

    /** Consulta maestro + detalle: el libro con sus ejemplares y cuántos hay en cada estado. */
    public Optional<DetalleLibro> detalle(String isbn) {
        try {
            Optional<Libro> encontrado = buscarPorIsbn(isbn);
            if (encontrado.isEmpty()) {
                return Optional.empty();
            }
            Libro libro = encontrado.get();
            ObjectId libroId = new ObjectId(libro.getId());
            List<Ejemplar> suyos = ejemplares.findByLibroId(libroId, Sort.by("codigo"));
            Map<EstadoEjemplar, Long> conteo = ejemplares.contarPorEstado(libroId);

            int total = suyos.size();
            List<ConteoEstado> porEstado = Arrays.stream(EstadoEjemplar.values())
                    .map(estado -> conteoEstado(estado, conteo.get(estado), total))
                    .toList();
            int prestamos = suyos.stream().mapToInt(Ejemplar::getNumeroPrestamos).sum();
            double valor = suyos.stream().mapToDouble(Ejemplar::getPrecioReposicion).sum();

            log.info("Detalle del libro {}: {} ejemplares, {} préstamos", libro.getIsbn(), total, prestamos);
            return Optional.of(new DetalleLibro(libro, suyos, porEstado, total, prestamos, valor));
        } catch (DataAccessException e) {
            throw errorBaseDatos("consultar el detalle del libro", e);
        }
    }

    // ------------------------------------------------------------------ Insertar / Actualizar / Eliminar

    public Libro crear(@Valid LibroInput in) {
        String isbn = in.getIsbn().trim();
        log.info("Crear libro: isbn={}, título={}", isbn, in.getTitulo());
        try {
            if (libros.existsByIsbn(isbn)) {
                throw new LibreriaException(Tipo.DUPLICADO, mensajeDuplicado(isbn));
            }
            Libro libro = Libro.builder()
                    .isbn(isbn)
                    .titulo(in.getTitulo().trim())
                    .autor(in.getAutor().trim())
                    .precio(in.getPrecio())
                    .numeroPaginas(in.getNumeroPaginas())
                    .fechaPublicacion(in.getFechaPublicacion())
                    .genero(in.getGenero())
                    .editorial(Paginacion.limpiar(in.getEditorial()))
                    .sinopsis(Paginacion.limpiar(in.getSinopsis()))
                    .build();
            Libro guardado = libros.insert(libro);
            log.info("Libro creado: isbn={}, _id={}", isbn, guardado.getId());
            return guardado;
        } catch (DuplicateKeyException e) {
            // Dos altas simultáneas del mismo ISBN: las dos pasan el existsByIsbn, pero el índice
            // único de MongoDB solo deja guardar una.
            log.warn("Alta rechazada por el índice único: el ISBN {} ya existe", isbn);
            throw new LibreriaException(Tipo.DUPLICADO, mensajeDuplicado(isbn), e);
        } catch (LibreriaException e) {
            log.warn("Crear libro rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("guardar el libro", e);
        }
    }

    /**
     * Actualiza el libro cuyo ISBN viene en el input (el ISBN no se cambia). Si el input trae la
     * versión que leyó el cliente y otro usuario lo modificó después, MongoDB no lo guarda y se
     * responde con un conflicto.
     */
    public Libro actualizar(@Valid LibroInput in) {
        String isbn = in.getIsbn().trim();
        log.info("Actualizar libro: isbn={}, versión leída={}", isbn, in.getVersion());
        try {
            Libro libro = libros.findByIsbn(isbn)
                    .orElseThrow(() -> new LibreriaException(Tipo.NO_ENCONTRADO, mensajeNoExiste(isbn)));
            if (in.getVersion() != null) {
                libro.setVersion(in.getVersion());
            }
            libro.setTitulo(in.getTitulo().trim());
            libro.setAutor(in.getAutor().trim());
            libro.setPrecio(in.getPrecio());
            libro.setNumeroPaginas(in.getNumeroPaginas());
            libro.setFechaPublicacion(in.getFechaPublicacion());
            libro.setGenero(in.getGenero());
            libro.setEditorial(Paginacion.limpiar(in.getEditorial()));
            libro.setSinopsis(Paginacion.limpiar(in.getSinopsis()));

            Libro guardado = libros.save(libro);
            log.info("Libro actualizado: isbn={}, versión nueva={}", isbn, guardado.getVersion());
            return guardado;
        } catch (OptimisticLockingFailureException e) {
            log.warn("Conflicto de versión al actualizar el libro {}: otro usuario lo modificó", isbn);
            throw new LibreriaException(Tipo.CONFLICTO, "Otro usuario modificó este libro; vuelva a buscarlo.", e);
        } catch (LibreriaException e) {
            log.warn("Actualizar libro rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("actualizar el libro", e);
        }
    }

    /** Elimina el libro y lo devuelve. Regla: no se elimina un libro que tiene ejemplares. */
    public Libro eliminar(String isbn) {
        String buscado = isbn == null ? "" : isbn.trim();
        log.info("Eliminar libro: isbn={}", buscado);
        try {
            Libro libro = libros.findByIsbn(buscado)
                    .orElseThrow(() -> new LibreriaException(Tipo.NO_ENCONTRADO, mensajeNoExiste(buscado)));
            long cantidad = ejemplares.countByLibroId(new ObjectId(libro.getId()));
            if (cantidad > 0) {
                throw new LibreriaException(Tipo.REGLA_NEGOCIO, "No se puede eliminar el libro " + buscado
                        + " porque tiene " + cantidad + " ejemplar(es) registrado(s). Elimine primero sus ejemplares.");
            }
            libros.delete(libro);
            log.info("Libro eliminado: isbn={}, _id={}", buscado, libro.getId());
            return libro;
        } catch (OptimisticLockingFailureException e) {
            log.warn("Conflicto de versión al eliminar el libro {}", buscado);
            throw new LibreriaException(Tipo.CONFLICTO, "Otro usuario modificó este libro; vuelva a buscarlo.", e);
        } catch (LibreriaException e) {
            log.warn("Eliminar libro rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("eliminar el libro", e);
        }
    }

    // ------------------------------------------------------------------ Apoyo para GraphQL

    /** Libros por su _id, en una sola consulta (para resolver Ejemplar.libro de muchos ejemplares). */
    public Map<String, Libro> porIds(Collection<String> ids) {
        try {
            return libros.findAllById(ids).stream().collect(Collectors.toMap(Libro::getId, Function.identity()));
        } catch (DataAccessException e) {
            throw errorBaseDatos("leer los libros de los ejemplares", e);
        }
    }

    /** Cuántos ejemplares tiene cada libro, en una sola agregación. */
    public Map<ObjectId, Long> contarEjemplares(Collection<ObjectId> librosIds) {
        try {
            return ejemplares.contarPorLibros(librosIds);
        } catch (DataAccessException e) {
            throw errorBaseDatos("contar los ejemplares", e);
        }
    }

    public List<Ejemplar> ejemplaresDe(Libro libro) {
        try {
            return ejemplares.findByLibroId(new ObjectId(libro.getId()), Sort.by("codigo"));
        } catch (DataAccessException e) {
            throw errorBaseDatos("leer los ejemplares del libro", e);
        }
    }

    // ------------------------------------------------------------------ Utilidades

    static ConteoEstado conteoEstado(EstadoEjemplar estado, Long cantidad, long total) {
        int n = cantidad == null ? 0 : cantidad.intValue();
        return new ConteoEstado(estado, n, total == 0 ? 0 : n * 100.0 / total);
    }

    private void registrarBusqueda(String criterio, String valor, boolean encontrado) {
        if (encontrado) {
            log.info("Consultar libro por {} '{}': encontrado", criterio, valor);
        } else {
            log.warn("Consultar libro por {} '{}': no existe", criterio, valor);
        }
    }

    private static String mensajeDuplicado(String isbn) {
        return "Ya existe un libro con el ISBN " + isbn + ".";
    }

    private static String mensajeNoExiste(String isbn) {
        return "No existe un libro con el ISBN " + isbn + ".";
    }

    private LibreriaException errorBaseDatos(String accion, DataAccessException e) {
        log.error("Error de MongoDB al {}", accion, e);
        return new LibreriaException(Tipo.ERROR_INTERNO,
                "No se pudo " + accion + ": la base de datos no respondió. Intente de nuevo.", e);
    }
}
