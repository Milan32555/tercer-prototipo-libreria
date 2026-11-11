package com.libreria.servidor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas de la API GraphQL completa (todas las consultas y mutaciones) contra MongoDB. */
@AutoConfigureGraphQlTester
class GraphQlApiTest extends PruebaBase {

    private static final String LIBRO = """
            {isbn: "%s", titulo: "%s", autor: "Gabriel García Márquez", precio: %s, numeroPaginas: 496,
             fechaPublicacion: "1967-05-30T00:00:00", genero: %s, editorial: "Sudamericana"%s}""";

    @Autowired
    GraphQlTester graphQl;

    @BeforeEach
    void datos() {
        crearLibro(ISBN_CIEN, "Cien años de soledad", 65000, "FICCION");
        crearLibro(ISBN_MARIA, "María", 39000, "FICCION");
        crearLibro("1012", "Las venas abiertas de América Latina", 79000, "HISTORIA");
        crearEjemplar("EJ-001", ISBN_CIEN, "DISPONIBLE", "Estante A1");
        crearEjemplar("EJ-002", ISBN_CIEN, "PRESTADO", "Estante A1");
        crearEjemplar("EJ-003", ISBN_MARIA, "DANIADO", "Bodega");
    }

    // ------------------------------------------------------------------ Libro

    @Test
    void crearLibroDevuelveIdVersionYOpcionales() {
        graphQl.document("mutation { crearLibro(input: " + String.format(LIBRO, "1008", "Pedro Páramo",
                        "42000", "FICCION", ", sinopsis: \"Comala\"") + ") { id isbn version editorial sinopsis cantidadEjemplares } }")
                .execute()
                .path("crearLibro.id").entity(String.class).satisfies(id -> assertThat(id).hasSize(24))
                .path("crearLibro.version").entity(Integer.class).isEqualTo(0)
                .path("crearLibro.sinopsis").entity(String.class).isEqualTo("Comala")
                .path("crearLibro.cantidadEjemplares").entity(Integer.class).isEqualTo(0);
    }

    @Test
    void listarLibrosPaginadoConCantidadDeEjemplares() {
        graphQl.document("{ libros(pagina: 0, tamanio: 2) { totalElementos totalPaginas pagina tamanio contenido { titulo cantidadEjemplares } } }")
                .execute()
                .path("libros.totalElementos").entity(Integer.class).isEqualTo(3)
                .path("libros.totalPaginas").entity(Integer.class).isEqualTo(2)
                .path("libros.contenido[*].titulo").entityList(String.class)
                .containsExactly("Cien años de soledad", "Las venas abiertas de América Latina")
                .path("libros.contenido[0].cantidadEjemplares").entity(Integer.class).isEqualTo(2);
    }

    @Test
    void librosPorPrecioYGenero() {
        graphQl.document("{ librosPorPrecioYGenero(precioMin: 40000, precioMax: 80000, genero: FICCION) { totalElementos contenido { titulo } } }")
                .execute()
                .path("librosPorPrecioYGenero.contenido[*].titulo").entityList(String.class)
                .containsExactly("Cien años de soledad");
    }

    @Test
    void consultarLibroPorLosDosCriterios() {
        graphQl.document("{ a: libroPorIsbn(isbn: \"1004\") { titulo } b: libroPorTitulo(titulo: \"MARIA\") { isbn } c: libroPorIsbn(isbn: \"9999\") { titulo } }")
                .execute()
                .path("a.titulo").entity(String.class).isEqualTo("María")
                .path("b.isbn").entity(String.class).isEqualTo(ISBN_MARIA)
                .path("c").valueIsNull();
    }

    @Test
    void detalleLibroMaestroYDetalle() {
        graphQl.document("{ detalleLibro(isbn: \"1001\") { libro { titulo } totalEjemplares totalPrestamos conteoPorEstado { estado cantidad } ejemplares { codigo isbnLibro libroId libro { titulo } } } }")
                .execute()
                .path("detalleLibro.libro.titulo").entity(String.class).isEqualTo("Cien años de soledad")
                .path("detalleLibro.totalEjemplares").entity(Integer.class).isEqualTo(2)
                .path("detalleLibro.ejemplares[*].codigo").entityList(String.class).containsExactly("EJ-001", "EJ-002")
                .path("detalleLibro.ejemplares[0].isbnLibro").entity(String.class).isEqualTo(ISBN_CIEN)
                .path("detalleLibro.ejemplares[1].libro.titulo").entity(String.class).isEqualTo("Cien años de soledad")
                .path("detalleLibro.conteoPorEstado[1].cantidad").entity(Integer.class).isEqualTo(1);
    }

    @Test
    void actualizarLibroConVersionYConflicto() {
        String actualizar = "mutation { actualizarLibro(input: " + String.format(LIBRO, ISBN_CIEN, "Cien años (nuevo)",
                "70000", "FICCION", ", version: 0") + ") { titulo precio version } }";

        graphQl.document(actualizar).execute()
                .path("actualizarLibro.version").entity(Integer.class).isEqualTo(1)
                .path("actualizarLibro.precio").entity(Double.class).isEqualTo(70000.0);

        graphQl.document(actualizar).execute().errors().satisfy(errores -> {
            assertThat(errores).hasSize(1);
            assertThat(errores.get(0).getMessage()).isEqualTo("Otro usuario modificó este libro; vuelva a buscarlo.");
            assertThat(errores.get(0).getExtensions()).containsEntry("codigo", "CONFLICTO");
        });
    }

    @Test
    void eliminarLibroConYSinEjemplares() {
        graphQl.document("mutation { eliminarLibro(isbn: \"1012\") { titulo } }").execute()
                .path("eliminarLibro.titulo").entity(String.class).isEqualTo("Las venas abiertas de América Latina");

        error("mutation { eliminarLibro(isbn: \"1001\") { titulo } }", "REGLA_NEGOCIO",
                "No se puede eliminar el libro 1001 porque tiene 2 ejemplar(es) registrado(s). "
                        + "Elimine primero sus ejemplares.");
        error("mutation { eliminarLibro(isbn: \"9999\") { titulo } }", "NO_ENCONTRADO",
                "No existe un libro con el ISBN 9999.");
    }

    @Test
    void erroresDeValidacionYDuplicadoEnEspanol() {
        error("mutation { crearLibro(input: " + String.format(LIBRO, "abc", "", "-1", "FICCION", "") + ") { isbn } }",
                "VALIDACION", "El ISBN debe tener de 1 a 4 dígitos (solo números), por ejemplo 1001. "
                        + "El precio debe ser mayor que cero. El título es obligatorio.");
        error("mutation { crearLibro(input: " + String.format(LIBRO, ISBN_CIEN, "Repetido", "1", "FICCION", "") + ") { isbn } }",
                "DUPLICADO", "Ya existe un libro con el ISBN 1001.");
    }

    // ------------------------------------------------------------------ Ejemplar

    @Test
    void ejemplaresConLibroTraeTodosLosAtributosMasTituloYAutor() {
        graphQl.document("""
                        { ejemplaresConLibro(estado: PRESTADO, pagina: 0, tamanio: 10) { totalElementos contenido {
                            id codigo libroId isbnLibro tituloLibro autorLibro estado ubicacion fechaIngreso
                            precioReposicion numeroPrestamos observaciones version } } }""")
                .execute()
                .path("ejemplaresConLibro.totalElementos").entity(Integer.class).isEqualTo(1)
                .path("ejemplaresConLibro.contenido[0]").entity(Map.class).satisfies(fila -> {
                    assertThat(fila).containsEntry("codigo", "EJ-002")
                            .containsEntry("isbnLibro", ISBN_CIEN)
                            .containsEntry("tituloLibro", "Cien años de soledad")
                            .containsEntry("autorLibro", "Gabriel García Márquez")
                            .containsEntry("ubicacion", "Estante A1")
                            .containsEntry("fechaIngreso", "2024-02-10T09:00:00")
                            .containsEntry("numeroPrestamos", 4)
                            .containsEntry("version", 0);
                    assertThat((String) fila.get("libroId")).hasSize(24);
                });
        graphQl.document("{ ejemplaresConLibro(isbn: \"1004\") { contenido { codigo tituloLibro } } }")
                .execute()
                .path("ejemplaresConLibro.contenido[*].codigo").entityList(String.class).containsExactly("EJ-003");
    }

    @Test
    void listarEjemplaresPaginado() {
        graphQl.document("{ ejemplares(pagina: 1, tamanio: 2) { totalElementos totalPaginas contenido { codigo isbnLibro } } }")
                .execute()
                .path("ejemplares.totalElementos").entity(Integer.class).isEqualTo(3)
                .path("ejemplares.contenido[*].codigo").entityList(String.class).containsExactly("EJ-003")
                .path("ejemplares.contenido[0].isbnLibro").entity(String.class).isEqualTo(ISBN_MARIA);
    }

    @Test
    void consultarEjemplarPorLosDosCriterios() {
        graphQl.document("{ ejemplarPorCodigo(codigo: \"ej-003\") { codigo estado libro { titulo } } ejemplaresPorLibroYUbicacion(isbn: \"1001\", ubicacion: \"estante a1\") { codigo } }")
                .execute()
                .path("ejemplarPorCodigo.libro.titulo").entity(String.class).isEqualTo("María")
                .path("ejemplaresPorLibroYUbicacion[*].codigo").entityList(String.class).containsExactly("EJ-001", "EJ-002");
    }

    @Test
    void actualizarEjemplarSumaPrestamoYEliminar() {
        graphQl.document("""
                        mutation { actualizarEjemplar(input: {codigo: "EJ-001", isbnLibro: "1001", estado: PRESTADO,
                          ubicacion: "Estante A1", fechaIngreso: "2024-02-10T09:00:00", precioReposicion: 65000,
                          numeroPrestamos: 50, observaciones: "Prestado a Sara", version: 0}) {
                          estado numeroPrestamos observaciones version } }""")
                .execute()
                .path("actualizarEjemplar.numeroPrestamos").entity(Integer.class).isEqualTo(5)
                .path("actualizarEjemplar.observaciones").entity(String.class).isEqualTo("Prestado a Sara")
                .path("actualizarEjemplar.version").entity(Integer.class).isEqualTo(1);

        graphQl.document("mutation { eliminarEjemplar(codigo: \"EJ-001\") { codigo numeroPrestamos } }").execute()
                .path("eliminarEjemplar.numeroPrestamos").entity(Integer.class).isEqualTo(5);
        error("mutation { eliminarEjemplar(codigo: \"EJ-001\") { codigo } }", "NO_ENCONTRADO",
                "No existe un ejemplar con el código EJ-001.");
    }

    @Test
    void erroresDeEjemplar() {
        error(ejemplarMutacion("crearEjemplar", "EJ-9", ISBN_CIEN), "VALIDACION",
                "El código debe tener el formato EJ-000 (EJ- y de 3 a 6 dígitos).");
        error(ejemplarMutacion("crearEjemplar", "EJ-001", ISBN_CIEN), "DUPLICADO", "Ya existe un ejemplar con el código EJ-001.");
        error(ejemplarMutacion("crearEjemplar", "EJ-100", "9999"), "NO_ENCONTRADO",
                "No existe un libro con el ISBN 9999; el ejemplar debe pertenecer a un libro registrado.");
        error(ejemplarMutacion("actualizarEjemplar", "EJ-001", ISBN_MARIA), "REGLA_NEGOCIO",
                "El ejemplar EJ-001 pertenece al libro 1001; no se puede cambiar de libro.");
    }

    // ------------------------------------------------------------------ Resumen y Observer

    @Test
    void resumenInventarioConLasTresTareas() {
        graphQl.document("{ resumenInventario { totalLibros totalEjemplares valorInventario ejemplaresPorEstado { estado cantidad } tareas { nombre hilo exitosa } } }")
                .execute()
                .path("resumenInventario.totalLibros").entity(Integer.class).isEqualTo(3)
                .path("resumenInventario.totalEjemplares").entity(Integer.class).isEqualTo(3)
                .path("resumenInventario.valorInventario").entity(Double.class).isEqualTo(195000.0)
                .path("resumenInventario.tareas[*].exitosa").entityList(Boolean.class).containsExactly(true, true, true);
    }

    @Test
    void lasRevisionesAumentanConCadaCambio() {
        int libros = revision("revisionLibros");
        int ejemplares = revision("revisionEjemplares");
        assertThat(libros).isEqualTo(3);
        assertThat(ejemplares).isEqualTo(3);

        graphQl.document("mutation { eliminarEjemplar(codigo: \"EJ-003\") { codigo } }").executeAndVerify();
        assertThat(revision("revisionEjemplares")).isEqualTo(4);
        assertThat(revision("revisionLibros")).isEqualTo(3);
    }

    @Test
    void generadorDeCargaDisponibleEnElPerfilDePruebas() {
        graphQl.document("mutation { generarDatosCarga(libros: 10, ejemplares: 30) { librosCreados ejemplaresCreados } }")
                .execute()
                .path("generarDatosCarga.librosCreados").entity(Integer.class).isEqualTo(10)
                .path("generarDatosCarga.ejemplaresCreados").entity(Integer.class).isEqualTo(30);
        graphQl.document("{ libros { totalElementos } ejemplares { totalElementos } }").execute()
                .path("libros.totalElementos").entity(Integer.class).isEqualTo(13)
                .path("ejemplares.totalElementos").entity(Integer.class).isEqualTo(33);
    }

    // ------------------------------------------------------------------ Utilidades

    private int revision(String campo) {
        return graphQl.document("{ " + campo + " }").execute().path(campo).entity(Integer.class).get();
    }

    private void error(String documento, String codigo, String mensaje) {
        graphQl.document(documento).execute().errors().satisfy(errores -> {
            assertThat(errores).hasSize(1);
            assertThat(errores.get(0).getMessage()).isEqualTo(mensaje);
            assertThat(errores.get(0).getExtensions()).containsEntry("codigo", codigo);
        });
    }

    private void crearLibro(String isbn, String titulo, double precio, String genero) {
        graphQl.document("mutation { crearLibro(input: " + String.format(LIBRO, isbn, titulo, String.valueOf(precio),
                genero, "") + ") { isbn } }").executeAndVerify();
    }

    private void crearEjemplar(String codigo, String isbn, String estado, String ubicacion) {
        graphQl.document(String.format("""
                mutation { crearEjemplar(input: {codigo: "%s", isbnLibro: "%s", estado: %s, ubicacion: "%s",
                  fechaIngreso: "2024-02-10T09:00:00", precioReposicion: 65000, numeroPrestamos: 4}) { codigo } }""",
                codigo, isbn, estado, ubicacion)).executeAndVerify();
    }

    private static String ejemplarMutacion(String mutacion, String codigo, String isbn) {
        return String.format("""
                mutation { %s(input: {codigo: "%s", isbnLibro: "%s", estado: DISPONIBLE, ubicacion: "A1",
                  fechaIngreso: "2024-02-10T09:00:00", precioReposicion: 1000}) { codigo } }""", mutacion, codigo, isbn);
    }
}
