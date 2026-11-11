package com.libreria.servidor.servicio;

import com.libreria.servidor.PruebaBase;
import com.libreria.servidor.dto.LibroInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.excepciones.ManejadorErrores;
import com.libreria.servidor.model.DetalleLibro;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Genero;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.model.Pagina;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServicioLibroTest extends PruebaBase {

    @Autowired
    ServicioLibro servicio;

    @Autowired
    ServicioEjemplar servicioEjemplar;

    @Test
    void crearGuardaEnMongoConIdYVersion() {
        Libro creado = servicio.crear(libro(ISBN_CIEN, "  Cien años de soledad  "));

        assertThat(creado.getId()).hasSize(24);
        assertThat(creado.getVersion()).isZero();
        assertThat(creado.getTitulo()).isEqualTo("Cien años de soledad");
        assertThat(mongo.count(new org.springframework.data.mongodb.core.query.Query(), "libros")).isEqualTo(1);
    }

    @Test
    void losOpcionalesVaciosSeGuardanComoNull() {
        LibroInput in = libro(ISBN_CIEN, "Cien años de soledad");
        in.setEditorial("   ");
        in.setSinopsis(null);

        Libro creado = servicio.crear(in);

        assertThat(creado.getEditorial()).isNull();
        assertThat(creado.getSinopsis()).isNull();
    }

    @Test
    void validacionesDelLibro() {
        LibroInput malo = LibroInput.builder()
                .isbn("12-34")
                .titulo(" ")
                .autor("")
                .precio(-1)
                .numeroPaginas(0)
                .fechaPublicacion(LocalDateTime.now().plusDays(2))
                .genero(null)
                .sinopsis("x".repeat(501))
                .build();

        assertThatThrownBy(() -> servicio.crear(malo))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(e -> assertThat(ManejadorErrores.mensajeValidacion((ConstraintViolationException) e))
                        .contains("El ISBN debe tener de 1 a 4 dígitos")
                        .contains("El título es obligatorio.")
                        .contains("El autor es obligatorio.")
                        .contains("El precio debe ser mayor que cero.")
                        .contains("El número de páginas debe ser mayor que cero.")
                        .contains("La fecha de publicación no puede ser futura.")
                        .contains("El género es obligatorio.")
                        .contains("La sinopsis admite máximo 500 caracteres."));
    }

    @Test
    void aceptaIsbnDeUnoACuatroDigitosYRechazaMasLargos() {
        servicio.crear(libro("7", "ISBN de 1 dígito"));
        servicio.crear(libro("2024", "ISBN de 4 dígitos"));

        assertThat(servicio.buscarPorIsbn("7")).isPresent();
        assertThat(servicio.buscarPorIsbn(" 2024 ")).isPresent();
        assertThatThrownBy(() -> servicio.crear(libro("12345", "Cinco dígitos")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("El ISBN debe tener de 1 a 4 dígitos");
    }

    @Test
    void noPermiteIsbnRepetido() {
        servicio.crear(libro(ISBN_CIEN, "Cien años de soledad"));

        assertThatThrownBy(() -> servicio.crear(libro(" " + ISBN_CIEN + " ", "Otro")))
                .isInstanceOf(LibreriaException.class)
                .hasMessage("Ya existe un libro con el ISBN 1001.")
                .extracting(e -> ((LibreriaException) e).getTipo()).isEqualTo(LibreriaException.Tipo.DUPLICADO);
    }

    @Test
    void buscarPorIsbnYPorTituloSinTildesNiMayusculas() {
        servicio.crear(libro(ISBN_MARIA, "María"));

        assertThat(servicio.buscarPorIsbn(" " + ISBN_MARIA)).map(Libro::getTitulo).contains("María");
        assertThat(servicio.buscarPorTitulo("MARIA")).map(Libro::getIsbn).contains(ISBN_MARIA);
        assertThat(servicio.buscarPorTitulo("maría ")).isPresent();
        assertThat(servicio.buscarPorTitulo("Mar")).isEmpty();
        assertThat(servicio.buscarPorIsbn("9999")).isEmpty();
        assertThat(servicio.buscarPorIsbn("  ")).isEmpty();
    }

    @Test
    void actualizarCambiaTodoMenosElIsbnYSubeLaVersion() {
        Libro creado = servicio.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        LibroInput cambios = libro(ISBN_CIEN, "Cien años de soledad (edición nueva)");
        cambios.setPrecio(70000);
        cambios.setGenero(Genero.OTRO);
        cambios.setEditorial(null);
        cambios.setSinopsis("Nueva sinopsis");
        cambios.setVersion(creado.getVersion());

        Libro actualizado = servicio.actualizar(cambios);

        assertThat(actualizado.getId()).isEqualTo(creado.getId());
        assertThat(actualizado.getVersion()).isEqualTo(1);
        assertThat(actualizado.getPrecio()).isEqualTo(70000);
        assertThat(actualizado.getGenero()).isEqualTo(Genero.OTRO);
        assertThat(actualizado.getEditorial()).isNull();
        assertThat(actualizado.getSinopsis()).isEqualTo("Nueva sinopsis");
    }

    @Test
    void actualizarConVersionViejaEsConflicto() {
        Libro creado = servicio.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        LibroInput primero = libro(ISBN_CIEN, "Primer cambio");
        primero.setVersion(creado.getVersion());
        servicio.actualizar(primero);

        LibroInput segundo = libro(ISBN_CIEN, "Segundo cambio con la versión vieja");
        segundo.setVersion(creado.getVersion());

        assertThatThrownBy(() -> servicio.actualizar(segundo))
                .isInstanceOf(LibreriaException.class)
                .hasMessage("Otro usuario modificó este libro; vuelva a buscarlo.")
                .extracting(e -> ((LibreriaException) e).getTipo()).isEqualTo(LibreriaException.Tipo.CONFLICTO);
        assertThat(servicio.buscarPorIsbn(ISBN_CIEN)).map(Libro::getTitulo).contains("Primer cambio");
    }

    @Test
    void actualizarUnLibroQueNoExiste() {
        assertThatThrownBy(() -> servicio.actualizar(libro(ISBN_CIEN, "No existe")))
                .isInstanceOf(LibreriaException.class)
                .hasMessage("No existe un libro con el ISBN 1001.");
    }

    @Test
    void eliminarDevuelveElLibroBorrado() {
        servicio.crear(libro(ISBN_CIEN, "Cien años de soledad"));

        Libro eliminado = servicio.eliminar(ISBN_CIEN);

        assertThat(eliminado.getTitulo()).isEqualTo("Cien años de soledad");
        assertThat(servicio.buscarPorIsbn(ISBN_CIEN)).isEmpty();
        assertThatThrownBy(() -> servicio.eliminar(ISBN_CIEN)).hasMessage("No existe un libro con el ISBN 1001.");
    }

    @Test
    void noSeEliminaUnLibroConEjemplares() {
        servicio.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        servicioEjemplar.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));

        assertThatThrownBy(() -> servicio.eliminar(ISBN_CIEN))
                .isInstanceOf(LibreriaException.class)
                .hasMessage("No se puede eliminar el libro 1001 porque tiene 1 ejemplar(es) registrado(s). "
                        + "Elimine primero sus ejemplares.");
    }

    @Test
    void listarEsPaginadoYOrdenadoPorTitulo() {
        for (int i = 1; i <= 25; i++) {
            servicio.crear(libro(String.format("20%02d", i), String.format("Libro %02d", i)));
        }

        Pagina<Libro> primera = servicio.listar(0, 10);
        Pagina<Libro> ultima = servicio.listar(2, 10);

        assertThat(primera.totalElementos()).isEqualTo(25);
        assertThat(primera.totalPaginas()).isEqualTo(3);
        assertThat(primera.contenido()).extracting(Libro::getTitulo).startsWith("Libro 01", "Libro 02");
        assertThat(ultima.contenido()).hasSize(5);
        assertThatThrownBy(() -> servicio.listar(0, 0)).hasMessage("El tamaño de página debe estar entre 1 y 100.");
        assertThatThrownBy(() -> servicio.listar(-1, 10)).hasMessage("La página no puede ser negativa.");
    }

    @Test
    void consultaPersonalizadaPorPrecioYGenero() {
        crear("2001", "Barato", 20000, Genero.FICCION);
        crear("2002", "Medio", 50000, Genero.FICCION);
        crear("2003", "Medio historia", 55000, Genero.HISTORIA);
        crear("2004", "Caro", 90000, Genero.FICCION);

        assertThat(servicio.listarPorPrecioYGenero(40000.0, 60000.0, Genero.FICCION, 0, 10).contenido())
                .extracting(Libro::getTitulo).containsExactly("Medio");
        assertThat(servicio.listarPorPrecioYGenero(40000.0, 60000.0, null, 0, 10).totalElementos()).isEqualTo(2);
        assertThat(servicio.listarPorPrecioYGenero(null, null, Genero.FICCION, 0, 10).totalElementos()).isEqualTo(3);
        assertThat(servicio.listarPorPrecioYGenero(60000.0, null, null, 0, 10).contenido())
                .extracting(Libro::getTitulo).containsExactly("Caro");
        assertThatThrownBy(() -> servicio.listarPorPrecioYGenero(10.0, 5.0, null, 0, 10))
                .hasMessage("El precio mínimo no puede ser mayor que el máximo.");
        assertThatThrownBy(() -> servicio.listarPorPrecioYGenero(-1.0, null, null, 0, 10))
                .hasMessage("Los precios del filtro no pueden ser negativos.");
    }

    @Test
    void detalleMaestroConSusEjemplaresYConteos() {
        servicio.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        servicioEjemplar.crear(ejemplar("EJ-002", ISBN_CIEN, EstadoEjemplar.PRESTADO, "Estante A1"));
        servicioEjemplar.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1"));

        DetalleLibro detalle = servicio.detalle(ISBN_CIEN).orElseThrow();

        assertThat(detalle.libro().getTitulo()).isEqualTo("Cien años de soledad");
        assertThat(detalle.ejemplares()).extracting("codigo").containsExactly("EJ-001", "EJ-002");
        assertThat(detalle.totalEjemplares()).isEqualTo(2);
        assertThat(detalle.totalPrestamos()).isEqualTo(8);
        assertThat(detalle.valorReposicion()).isEqualTo(130000);
        assertThat(detalle.conteoPorEstado()).hasSize(4);
        assertThat(detalle.conteoPorEstado().get(0).cantidad()).isEqualTo(1);
        assertThat(detalle.conteoPorEstado().get(1).porcentaje()).isEqualTo(50.0);
        assertThat(servicio.detalle("9999")).isEmpty();
    }

    private void crear(String isbn, String titulo, double precio, Genero genero) {
        LibroInput in = libro(isbn, titulo);
        in.setPrecio(precio);
        in.setGenero(genero);
        servicio.crear(in);
    }
}
