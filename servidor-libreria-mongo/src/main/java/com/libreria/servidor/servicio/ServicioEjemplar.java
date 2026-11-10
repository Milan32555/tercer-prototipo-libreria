package com.libreria.servidor.servicio;

import com.libreria.servidor.dto.EjemplarInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.LibreriaException.Tipo;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EjemplarConLibro;
import com.libreria.servidor.model.EstadoEjemplar;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Lógica de negocio del CRUD de Ejemplar (colección B, detalle de Libro). Los clientes trabajan
 * con el ISBN del libro; aquí se traduce al ObjectId del libro (llave foránea libroId).
 */
@Service
@Validated
public class ServicioEjemplar {

    private static final Logger log = LoggerFactory.getLogger(ServicioEjemplar.class);
    private static final Sort ORDEN = Sort.by("codigo");

    private final EjemplarRepository ejemplares;
    private final LibroRepository libros;

    public ServicioEjemplar(EjemplarRepository ejemplares, LibroRepository libros) {
        this.ejemplares = ejemplares;
        this.libros = libros;
    }

    // ------------------------------------------------------------------ Listar

    /** Listar todos los ejemplares (paginado, por código). */
    public Pagina<Ejemplar> listar(int pagina, int tamanio) {
        try {
            Pageable pageable = Paginacion.crear(pagina, tamanio, ORDEN);
            Pagina<Ejemplar> resultado = Pagina.de(ejemplares.findAll(pageable));
            log.info("Listar ejemplares: página {} con {} de {} ejemplares", pagina,
                    resultado.contenido().size(), resultado.totalElementos());
            return resultado;
        } catch (LibreriaException e) {
            log.warn("Listar ejemplares rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("listar los ejemplares", e);
        }
    }

    /**
     * Consulta personalizada 1 (grilla de Listar Ejemplares): ejemplares con el título y el autor
     * de su libro ($lookup), filtrados por estado y/o ISBN del libro.
     */
    public Pagina<EjemplarConLibro> listarConLibro(EstadoEjemplar estado, String isbn, int pagina, int tamanio) {
        try {
            Pageable pageable = Paginacion.crear(pagina, tamanio, ORDEN);
            ObjectId libroId = null;
            String isbnBuscado = Paginacion.limpiar(isbn);
            if (isbnBuscado != null) {
                Optional<Libro> libro = libros.findByIsbn(isbnBuscado);
                if (libro.isEmpty()) {
                    log.warn("Ejemplares con libro: no existe un libro con el ISBN {}", isbnBuscado);
                    return new Pagina<>(List.of(), pagina, tamanio, 0, 0);
                }
                libroId = new ObjectId(libro.get().getId());
            }
            Page<EjemplarConLibro> page = ejemplares.ejemplaresConLibro(estado, libroId, pageable);
            log.info("Ejemplares con libro (estado={}, isbn={}): {} encontrados", estado, isbnBuscado,
                    page.getTotalElements());
            return Pagina.de(page);
        } catch (LibreriaException e) {
            log.warn("Ejemplares con libro rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("listar los ejemplares con su libro", e);
        }
    }

    // ------------------------------------------------------------------ Consultar

    /** Búsqueda individual, criterio 1: código del ejemplar. */
    public Optional<Ejemplar> buscarPorCodigo(String codigo) {
        String buscado = normalizarCodigo(codigo);
        if (buscado == null) {
            log.warn("Consultar ejemplar por código: no se indicó el código");
            return Optional.empty();
        }
        try {
            Optional<Ejemplar> ejemplar = ejemplares.findByCodigo(buscado);
            if (ejemplar.isPresent()) {
                log.info("Consultar ejemplar por código {}: encontrado", buscado);
            } else {
                log.warn("Consultar ejemplar por código {}: no existe", buscado);
            }
            return ejemplar;
        } catch (DataAccessException e) {
            throw errorBaseDatos("buscar el ejemplar", e);
        }
    }

    /**
     * Búsqueda individual, criterio 2: ISBN del libro + ubicación. Puede haber varias copias del
     * mismo libro en la misma ubicación, así que devuelve todas (ordenadas por código).
     */
    public List<Ejemplar> buscarPorLibroYUbicacion(String isbn, String ubicacion) {
        String isbnBuscado = Paginacion.limpiar(isbn);
        String ubicacionBuscada = Paginacion.limpiar(ubicacion);
        if (isbnBuscado == null || ubicacionBuscada == null) {
            log.warn("Consultar ejemplar por libro y ubicación: faltan datos (isbn={}, ubicación={})",
                    isbnBuscado, ubicacionBuscada);
            return List.of();
        }
        try {
            Optional<Libro> libro = libros.findByIsbn(isbnBuscado);
            if (libro.isEmpty()) {
                log.warn("Consultar ejemplar por libro y ubicación: no existe un libro con el ISBN {}", isbnBuscado);
                return List.of();
            }
            List<Ejemplar> encontrados = ejemplares.findByLibroIdAndUbicacion(
                    new ObjectId(libro.get().getId()), ubicacionBuscada);
            if (encontrados.isEmpty()) {
                log.warn("Consultar ejemplar por ISBN {} y ubicación '{}': no hay ejemplares", isbnBuscado,
                        ubicacionBuscada);
            } else {
                log.info("Consultar ejemplar por ISBN {} y ubicación '{}': {} encontrado(s)", isbnBuscado,
                        ubicacionBuscada, encontrados.size());
            }
            return encontrados;
        } catch (DataAccessException e) {
            throw errorBaseDatos("buscar los ejemplares", e);
        }
    }

    // ------------------------------------------------------------------ Insertar / Actualizar / Eliminar

    /** Crea el ejemplar. Regla: solo para un libro existente; el código no debe existir. */
    public Ejemplar crear(@Valid EjemplarInput in) {
        String codigo = normalizarCodigo(in.getCodigo());
        String isbn = in.getIsbnLibro().trim();
        log.info("Crear ejemplar: código={}, isbn del libro={}", codigo, isbn);
        try {
            if (ejemplares.existsByCodigo(codigo)) {
                throw new LibreriaException(Tipo.DUPLICADO, mensajeDuplicado(codigo));
            }
            Libro libro = libros.findByIsbn(isbn)
                    .orElseThrow(() -> new LibreriaException(Tipo.NO_ENCONTRADO, "No existe un libro con el ISBN "
                            + isbn + "; el ejemplar debe pertenecer a un libro registrado."));
            Ejemplar ejemplar = Ejemplar.builder()
                    .codigo(codigo)
                    .libroId(new ObjectId(libro.getId()))
                    .estado(in.getEstado())
                    .ubicacion(in.getUbicacion().trim())
                    .fechaIngreso(in.getFechaIngreso())
                    .precioReposicion(in.getPrecioReposicion())
                    .numeroPrestamos(in.getNumeroPrestamos() == null ? 0 : in.getNumeroPrestamos())
                    .observaciones(Paginacion.limpiar(in.getObservaciones()))
                    .build();
            Ejemplar guardado = ejemplares.insert(ejemplar);
            log.info("Ejemplar creado: código={}, _id={}, libroId={}", codigo, guardado.getId(), guardado.getLibroId());
            return guardado;
        } catch (DuplicateKeyException e) {
            log.warn("Alta rechazada por el índice único: el código {} ya existe", codigo);
            throw new LibreriaException(Tipo.DUPLICADO, mensajeDuplicado(codigo), e);
        } catch (LibreriaException e) {
            log.warn("Crear ejemplar rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("guardar el ejemplar", e);
        }
    }

    /**
     * Actualiza el ejemplar cuyo código viene en el input. Reglas: no cambia de libro; el número de
     * préstamos no se toma del cliente: el servidor le suma 1 cuando el ejemplar pasa a PRESTADO.
     */
    public Ejemplar actualizar(@Valid EjemplarInput in) {
        String codigo = normalizarCodigo(in.getCodigo());
        log.info("Actualizar ejemplar: código={}, versión leída={}", codigo, in.getVersion());
        try {
            Ejemplar ejemplar = ejemplares.findByCodigo(codigo)
                    .orElseThrow(() -> new LibreriaException(Tipo.NO_ENCONTRADO, mensajeNoExiste(codigo)));
            Libro libro = libros.findById(ejemplar.getLibroId().toHexString()).orElse(null);
            String isbnActual = libro == null ? null : libro.getIsbn();
            if (!Objects.equals(isbnActual, in.getIsbnLibro().trim())) {
                throw new LibreriaException(Tipo.REGLA_NEGOCIO, "El ejemplar " + codigo + " pertenece al libro "
                        + isbnActual + "; no se puede cambiar de libro.");
            }
            if (in.getNumeroPrestamos() != null && in.getNumeroPrestamos() != ejemplar.getNumeroPrestamos()) {
                log.info("Actualizar ejemplar {}: se ignora numeroPrestamos={} (lo lleva el servidor)", codigo,
                        in.getNumeroPrestamos());
            }
            boolean pasaAPrestado = ejemplar.getEstado() != EstadoEjemplar.PRESTADO
                    && in.getEstado() == EstadoEjemplar.PRESTADO;
            if (in.getVersion() != null) {
                ejemplar.setVersion(in.getVersion());
            }
            if (pasaAPrestado) {
                ejemplar.setNumeroPrestamos(ejemplar.getNumeroPrestamos() + 1);
            }
            ejemplar.setEstado(in.getEstado());
            ejemplar.setUbicacion(in.getUbicacion().trim());
            ejemplar.setFechaIngreso(in.getFechaIngreso());
            ejemplar.setPrecioReposicion(in.getPrecioReposicion());
            ejemplar.setObservaciones(Paginacion.limpiar(in.getObservaciones()));

            Ejemplar guardado = ejemplares.save(ejemplar);
            log.info("Ejemplar actualizado: código={}, estado={}, préstamos={}{}, versión nueva={}", codigo,
                    guardado.getEstado(), guardado.getNumeroPrestamos(), pasaAPrestado ? " (+1 por préstamo)" : "",
                    guardado.getVersion());
            return guardado;
        } catch (OptimisticLockingFailureException e) {
            log.warn("Conflicto de versión al actualizar el ejemplar {}: otro usuario lo modificó", codigo);
            throw new LibreriaException(Tipo.CONFLICTO, "Otro usuario modificó este ejemplar; vuelva a buscarlo.", e);
        } catch (LibreriaException e) {
            log.warn("Actualizar ejemplar rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("actualizar el ejemplar", e);
        }
    }

    /** Elimina el ejemplar y lo devuelve. */
    public Ejemplar eliminar(String codigo) {
        String buscado = normalizarCodigo(codigo);
        log.info("Eliminar ejemplar: código={}", buscado);
        try {
            Ejemplar ejemplar = ejemplares.findByCodigo(buscado == null ? "" : buscado)
                    .orElseThrow(() -> new LibreriaException(Tipo.NO_ENCONTRADO, mensajeNoExiste(codigo)));
            ejemplares.delete(ejemplar);
            log.info("Ejemplar eliminado: código={}, _id={}", buscado, ejemplar.getId());
            return ejemplar;
        } catch (OptimisticLockingFailureException e) {
            log.warn("Conflicto de versión al eliminar el ejemplar {}", buscado);
            throw new LibreriaException(Tipo.CONFLICTO, "Otro usuario modificó este ejemplar; vuelva a buscarlo.", e);
        } catch (LibreriaException e) {
            log.warn("Eliminar ejemplar rechazado: {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            throw errorBaseDatos("eliminar el ejemplar", e);
        }
    }

    // ------------------------------------------------------------------ Utilidades

    static String normalizarCodigo(String codigo) {
        String limpio = Paginacion.limpiar(codigo);
        return limpio == null ? null : limpio.toUpperCase(Locale.ROOT);
    }

    private static String mensajeDuplicado(String codigo) {
        return "Ya existe un ejemplar con el código " + codigo + ".";
    }

    private static String mensajeNoExiste(String codigo) {
        return "No existe un ejemplar con el código " + (codigo == null ? "" : codigo.trim()) + ".";
    }

    private LibreriaException errorBaseDatos(String accion, DataAccessException e) {
        log.error("Error de MongoDB al {}", accion, e);
        return new LibreriaException(Tipo.ERROR_INTERNO,
                "No se pudo " + accion + ": la base de datos no respondió. Intente de nuevo.", e);
    }
}
