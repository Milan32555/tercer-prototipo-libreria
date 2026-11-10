package com.libreria.servidor.servicio;

import com.libreria.servidor.PruebaBase;
import com.libreria.servidor.dto.EjemplarInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.ManejadorErrores;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EjemplarConLibro;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.model.Pagina;
import jakarta.validation.ConstraintViolationException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServicioEjemplarTest extends PruebaBase {

    @Autowired
    ServicioEjemplar servicio;

    @Autowired
    ServicioLibro servicioLibro;

    Libro cien;

    @BeforeEach
    void crearLibros() {
        cien = servicioLibro.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        servicioLibro.crear(libro(ISBN_MARIA, "María"));
    }

    @Test
    void crearGuardaLaLlaveForaneaComoObjectIdDelLibro() {
        Ejemplar creado = servicio.crear(ejemplar("ej-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, " Estante A1 "));

        assertThat(creado.getCodigo()).isEqualTo("EJ-001");
        assertThat(creado.getUbicacion()).isEqualTo("Estante A1");
        assertThat(creado.getLibroId()).isEqualTo(new ObjectId(cien.getId()));

        Document guardado = mongo.getCollection("ejemplares").find(new Document("codigo", "EJ-001")).first();
        assertThat(guardado).isNotNull();
        assertThat(guardado.get("libroId")).isInstanceOf(ObjectId.class).isEqualTo(new ObjectId(cien.getId()));
        assertThat(guardado.get("_id")).isInstanceOf(ObjectId.class);
    }

    @Test
    void sinNumeroDePrestamosEmpiezaEnCero() {
        EjemplarInput in = ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1");
        in.setNumeroPrestamos(null);

        assertThat(servicio.crear(in).getNumeroPrestamos()).isZero();
    }

    @Test
    void validacionesDelEjemplar() {
        EjemplarInput malo = EjemplarInput.builder()
                .codigo("X-1")
                .isbnLibro("abc")
                .estado(null)
                .ubicacion(" ")
                .fechaIngreso(LocalDateTime.now().plusDays(1))
                .precioReposicion(0)
                .numeroPrestamos(-1)
                .observaciones("x".repeat(301))
                .build();

        assertThatThrownBy(() -> servicio.crear(malo))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(e -> assertThat(ManejadorErrores.mensajeValidacion((ConstraintViolationException) e))
                        .contains("El código debe tener el formato EJ-000")
                        .contains("El ISBN debe tener 10 o 13 dígitos")
                        .contains("El estado del ejemplar es obligatorio.")
                        .contains("La ubicación es obligatoria.")
                        .contains("La fecha de ingreso no puede ser futura.")
                        .contains("El precio de reposición debe ser mayor que cero.")
                        .contains("El número de préstamos no puede ser negativo.")
                        .contains("Las observaciones admiten máximo 300 caracteres."));
    }

    @Test
    void soloParaUnLibroExistente() {
        assertThatThrownBy(() -> servicio.crear(ejemplar("EJ-001", "978-0000000000", EstadoEjemplar.DISPONIBLE, "A")))
                .isInstanceOf(LibreriaException.class)
                .hasMessage("No existe un libro con el ISBN 978-0000000000; el ejemplar debe pertenecer a un libro registrado.");
    }

    @Test
    void codigoUnico() {
        servicio.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));

        assertThatThrownBy(() -> servicio.crear(ejemplar("ej-001", ISBN_MARIA, EstadoEjemplar.DISPONIBLE, "B")))
                .isInstanceOf(LibreriaException.class)
                .hasMessage("Ya existe un ejemplar con el código EJ-001.");
    }

    @Test
    void pasarAPrestadoSumaUnPrestamoYElClienteNoLoPuedeCambiar() {
        Ejemplar creado = servicio.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));
        EjemplarInput prestar = ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.PRESTADO, "Estante A1");
        prestar.setNumeroPrestamos(99);
        prestar.setVersion(creado.getVersion());

        Ejemplar prestado = servicio.actualizar(prestar);
        assertThat(prestado.getNumeroPrestamos()).isEqualTo(5);
        assertThat(prestado.getVersion()).isEqualTo(1);

        // Seguir en PRESTADO no vuelve a sumar.
        EjemplarInput sigue = ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.PRESTADO, "Estante B1");
        assertThat(servicio.actualizar(sigue).getNumeroPrestamos()).isEqualTo(5);

        // Devolverlo y volver a prestarlo sí suma.
        servicio.actualizar(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante B1"));
        assertThat(servicio.actualizar(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.PRESTADO, "Estante B1"))
                .getNumeroPrestamos()).isEqualTo(6);
    }

    @Test
    void actualizarNoCambiaDeLibro() {
        servicio.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));

        assertThatThrownBy(() -> servicio.actualizar(ejemplar("EJ-001", ISBN_MARIA, EstadoEjemplar.DISPONIBLE, "A")))
                .isInstanceOf(LibreriaException.class)
                .hasMessage("El ejemplar EJ-001 pertenece al libro 978-0307474728; no se puede cambiar de libro.");
    }

    @Test
    void actualizarConVersionViejaEsConflicto() {
        Ejemplar creado = servicio.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));
        EjemplarInput uno = ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DANIADO, "Bodega");
        uno.setVersion(creado.getVersion());
        servicio.actualizar(uno);

        EjemplarInput dos = ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante C3");
        dos.setVersion(creado.getVersion());

        assertThatThrownBy(() -> servicio.actualizar(dos))
                .hasMessage("Otro usuario modificó este ejemplar; vuelva a buscarlo.");
    }

    @Test
    void eliminarDevuelveElEjemplarYPermiteBorrarElLibroDespues() {
        servicio.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));

        Ejemplar eliminado = servicio.eliminar(" ej-001 ");

        assertThat(eliminado.getCodigo()).isEqualTo("EJ-001");
        assertThat(servicio.buscarPorCodigo("EJ-001")).isEmpty();
        assertThat(servicioLibro.eliminar(ISBN_CIEN).getIsbn()).isEqualTo(ISBN_CIEN);
        assertThatThrownBy(() -> servicio.eliminar("EJ-001")).hasMessage("No existe un ejemplar con el código EJ-001.");
    }

    @Test
    void busquedaPorCodigoYPorLibroMasUbicacion() {
        servicio.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));
        servicio.crear(ejemplar("EJ-002", ISBN_CIEN, EstadoEjemplar.PRESTADO, "Estante A1"));
        servicio.crear(ejemplar("EJ-003", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Bodega"));

        assertThat(servicio.buscarPorCodigo("ej-002")).map(Ejemplar::getEstado).contains(EstadoEjemplar.PRESTADO);
        assertThat(servicio.buscarPorLibroYUbicacion(ISBN_CIEN, "estante a1"))
                .extracting(Ejemplar::getCodigo).containsExactly("EJ-001", "EJ-002");
        assertThat(servicio.buscarPorLibroYUbicacion(ISBN_CIEN, "BODEGA")).hasSize(1);
        assertThat(servicio.buscarPorLibroYUbicacion(ISBN_MARIA, "Estante A1")).isEmpty();
        assertThat(servicio.buscarPorLibroYUbicacion("978-0000000000", "Estante A1")).isEmpty();
        assertThat(servicio.buscarPorLibroYUbicacion(ISBN_CIEN, " ")).isEmpty();
    }

    @Test
    void consultaPersonalizadaEjemplaresConLibro() {
        EjemplarInput conObservacion = ejemplar("EJ-002", ISBN_CIEN, EstadoEjemplar.PRESTADO, "Estante A1");
        conObservacion.setObservaciones("Edición conmemorativa");
        servicio.crear(conObservacion);
        servicio.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));
        servicio.crear(ejemplar("EJ-003", ISBN_MARIA, EstadoEjemplar.PRESTADO, "Estante B3"));

        Pagina<EjemplarConLibro> todos = servicio.listarConLibro(null, null, 0, 10);
        assertThat(todos.totalElementos()).isEqualTo(3);
        EjemplarConLibro fila = todos.contenido().get(1);
        assertThat(fila.getCodigo()).isEqualTo("EJ-002");
        assertThat(fila.getId()).hasSize(24);
        assertThat(fila.getLibroId()).isEqualTo(cien.getId());
        assertThat(fila.getIsbnLibro()).isEqualTo(ISBN_CIEN);
        assertThat(fila.getTituloLibro()).isEqualTo("Cien años de soledad");
        assertThat(fila.getAutorLibro()).isEqualTo("Gabriel García Márquez");
        assertThat(fila.getEstado()).isEqualTo(EstadoEjemplar.PRESTADO);
        assertThat(fila.getUbicacion()).isEqualTo("Estante A1");
        assertThat(fila.getFechaIngreso()).isEqualTo(LocalDateTime.of(2024, 2, 10, 9, 0));
        assertThat(fila.getPrecioReposicion()).isEqualTo(65000);
        assertThat(fila.getNumeroPrestamos()).isEqualTo(4);
        assertThat(fila.getObservaciones()).isEqualTo("Edición conmemorativa");
        assertThat(fila.getVersion()).isZero();

        assertThat(servicio.listarConLibro(EstadoEjemplar.PRESTADO, null, 0, 10).contenido())
                .extracting(EjemplarConLibro::getCodigo).containsExactly("EJ-002", "EJ-003");
        assertThat(servicio.listarConLibro(EstadoEjemplar.PRESTADO, ISBN_MARIA, 0, 10).contenido())
                .extracting(EjemplarConLibro::getTituloLibro).containsExactly("María");
        assertThat(servicio.listarConLibro(null, "978-0000000000", 0, 10).totalElementos()).isZero();

        Pagina<EjemplarConLibro> segunda = servicio.listarConLibro(null, null, 1, 2);
        assertThat(segunda.totalPaginas()).isEqualTo(2);
        assertThat(segunda.contenido()).extracting(EjemplarConLibro::getCodigo).containsExactly("EJ-003");
    }

    @Test
    void listarTodosPaginado() {
        for (int i = 1; i <= 12; i++) {
            servicio.crear(ejemplar(String.format("EJ-%03d", i), ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));
        }

        Pagina<Ejemplar> pagina = servicio.listar(1, 5);

        assertThat(pagina.totalElementos()).isEqualTo(12);
        assertThat(pagina.contenido()).extracting(Ejemplar::getCodigo).containsExactly("EJ-006", "EJ-007", "EJ-008",
                "EJ-009", "EJ-010");
    }
}
