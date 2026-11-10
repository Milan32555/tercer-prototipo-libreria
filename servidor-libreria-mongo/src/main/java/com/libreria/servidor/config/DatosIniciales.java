package com.libreria.servidor.config;

import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Genero;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.repository.EjemplarRepository;
import com.libreria.servidor.repository.LibroRepository;
import com.libreria.servidor.servicio.GeneradorDatosCarga;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Al arrancar, si la base está vacía, carga los datos de prueba del segundo proyecto:
 * 12 libros y 20 ejemplares (10 disponibles, 6 prestados, 2 dañados y 2 dados de baja).
 * "Delirio" no tiene ejemplares: es el libro que sí se puede eliminar en la demostración.
 * Con el perfil "carga" además genera 1.000 libros y 5.000 ejemplares si todavía no existen.
 * Con libreria.datos-iniciales.reiniciar=true (solo las pruebas E2E, por línea de comandos) primero
 * vacía las colecciones, para que cada corrida empiece con los mismos datos.
 */
@Component
public class DatosIniciales implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private final LibroRepository libros;
    private final EjemplarRepository ejemplares;
    private final GeneradorDatosCarga generador;
    private final MongoTemplate mongo;
    private final boolean habilitado;
    private final boolean generarCarga;
    private final boolean reiniciar;

    public DatosIniciales(LibroRepository libros, EjemplarRepository ejemplares, GeneradorDatosCarga generador,
                          MongoTemplate mongo,
                          @Value("${libreria.datos-iniciales.habilitado:true}") boolean habilitado,
                          @Value("${libreria.carga.generar-al-iniciar:false}") boolean generarCarga,
                          @Value("${libreria.datos-iniciales.reiniciar:false}") boolean reiniciar) {
        this.libros = libros;
        this.ejemplares = ejemplares;
        this.generador = generador;
        this.mongo = mongo;
        this.habilitado = habilitado;
        this.generarCarga = generarCarga;
        this.reiniciar = reiniciar;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!habilitado) {
            return;
        }
        try {
            if (reiniciar) {
                if (!"libreria_e2e".equals(mongo.getDb().getName())) {
                    log.warn("Se pidió reiniciar la base {}, pero solo se permite con libreria_e2e: no se borra nada",
                            mongo.getDb().getName());
                } else {
                    log.warn("Reiniciando la base {}: se borran libros, ejemplares y revisiones", mongo.getDb().getName());
                    for (String coleccion : List.of("libros", "ejemplares", "revisiones")) {
                        mongo.getCollection(coleccion).deleteMany(new org.bson.Document());
                    }
                }
            }
            if (libros.count() == 0) {
                cargarDatosDePrueba();
            } else {
                log.info("La base ya tiene {} libros y {} ejemplares: no se cargan datos de prueba",
                        libros.count(), ejemplares.count());
            }
            if (generarCarga && libros.count() < 1_000) {
                generador.generar(1_000, 5_000);
            }
        } catch (DataAccessException e) {
            log.error("No se pudieron cargar los datos iniciales. ¿Está MongoDB en ejecución?", e);
        }
    }

    private void cargarDatosDePrueba() {
        List<Libro> iniciales = List.of(
                libro("978-0307474728", "Cien años de soledad", "Gabriel García Márquez", 65000, 496,
                        LocalDateTime.of(1967, 5, 30, 0, 0), Genero.FICCION, "Sudamericana",
                        "La historia de la familia Buendía a lo largo de siete generaciones en Macondo."),
                libro("978-8437609355", "La vorágine", "José Eustasio Rivera", 48000, 390,
                        LocalDateTime.of(1924, 11, 25, 0, 0), Genero.FICCION, "Cátedra", null),
                libro("978-8420401751", "Delirio", "Laura Restrepo", 55000, 352,
                        LocalDateTime.of(2004, 3, 1, 0, 0), Genero.FICCION, "Alfaguara", null),
                libro("978-9589012338", "María", "Jorge Isaacs", 39000, 340,
                        LocalDateTime.of(1867, 1, 1, 0, 0), Genero.FICCION, null,
                        "Efraín regresa al Valle del Cauca y vive un amor marcado por la enfermedad de María."),
                libro("978-9584215000", "El olvido que seremos", "Héctor Abad Faciolince", 69000, 274,
                        LocalDateTime.of(2006, 1, 1, 0, 0), Genero.NO_FICCION, "Planeta", null),
                libro("978-9584239563", "Rosario Tijeras", "Jorge Franco", 45000, 186,
                        LocalDateTime.of(1999, 1, 1, 0, 0), Genero.FICCION, null, null),
                libro("978-8483103913", "Los ejércitos", "Evelio Rosero", 58000, 203,
                        LocalDateTime.of(2007, 3, 1, 0, 0), Genero.FICCION, "Tusquets", null),
                libro("978-9681649500", "Pedro Páramo", "Juan Rulfo", 42000, 129,
                        LocalDateTime.of(1955, 3, 19, 0, 0), Genero.FICCION, "Fondo de Cultura Económica",
                        "Juan Preciado llega a Comala buscando a su padre y encuentra un pueblo de murmullos."),
                libro("978-8420633121", "Ficciones", "Jorge Luis Borges", 52000, 218,
                        LocalDateTime.of(1944, 1, 1, 0, 0), Genero.FICCION, "Alianza", null),
                libro("978-8437624747", "Rayuela", "Julio Cortázar", 89000, 746,
                        LocalDateTime.of(1963, 6, 28, 0, 0), Genero.FICCION, "Cátedra", null),
                libro("978-9580469117", "Veinte poemas de amor y una canción desesperada", "Pablo Neruda", 35000, 151,
                        LocalDateTime.of(1924, 6, 1, 0, 0), Genero.POESIA, null, null),
                libro("978-8432311451", "Las venas abiertas de América Latina", "Eduardo Galeano", 79000, 379,
                        LocalDateTime.of(1971, 1, 1, 0, 0), Genero.HISTORIA, "Siglo XXI",
                        "Ensayo sobre la explotación de los recursos de América Latina desde la colonia."));

        Map<String, ObjectId> ids = new HashMap<>();
        for (Libro libro : libros.insert(iniciales)) {
            ids.put(libro.getIsbn(), new ObjectId(libro.getId()));
        }

        List<Ejemplar> copias = List.of(
                ejemplar("EJ-001", EstadoEjemplar.DISPONIBLE, "Estante A1", LocalDateTime.of(2024, 2, 10, 9, 0), 65000, 4, ids.get("978-0307474728"), null),
                ejemplar("EJ-002", EstadoEjemplar.PRESTADO, "Estante A1", LocalDateTime.of(2024, 2, 10, 9, 5), 65000, 9, ids.get("978-0307474728"), null),
                ejemplar("EJ-003", EstadoEjemplar.DISPONIBLE, "Estante B2", LocalDateTime.of(2024, 3, 2, 11, 30), 48000, 2, ids.get("978-8437609355"), null),
                ejemplar("EJ-004", EstadoEjemplar.PRESTADO, "Estante A1", LocalDateTime.of(2025, 1, 15, 10, 20), 65000, 6, ids.get("978-0307474728"), "Edición conmemorativa"),
                ejemplar("EJ-005", EstadoEjemplar.PRESTADO, "Estante B2", LocalDateTime.of(2024, 8, 21, 16, 45), 48000, 5, ids.get("978-8437609355"), null),
                ejemplar("EJ-006", EstadoEjemplar.DISPONIBLE, "Estante B3", LocalDateTime.of(2024, 5, 6, 8, 40), 39000, 3, ids.get("978-9589012338"), null),
                ejemplar("EJ-007", EstadoEjemplar.DANIADO, "Bodega", LocalDateTime.of(2023, 11, 14, 15, 10), 39000, 11, ids.get("978-9589012338"), "Lomo despegado"),
                ejemplar("EJ-008", EstadoEjemplar.PRESTADO, "Estante C1", LocalDateTime.of(2025, 3, 3, 9, 15), 69000, 7, ids.get("978-9584215000"), null),
                ejemplar("EJ-009", EstadoEjemplar.DISPONIBLE, "Estante C1", LocalDateTime.of(2025, 3, 3, 9, 20), 69000, 2, ids.get("978-9584215000"), null),
                ejemplar("EJ-010", EstadoEjemplar.DISPONIBLE, "Estante B4", LocalDateTime.of(2024, 9, 12, 14, 0), 45000, 1, ids.get("978-9584239563"), null),
                ejemplar("EJ-011", EstadoEjemplar.DADO_DE_BAJA, "Bodega", LocalDateTime.of(2022, 6, 30, 11, 0), 45000, 15, ids.get("978-9584239563"), "Páginas faltantes"),
                ejemplar("EJ-012", EstadoEjemplar.DISPONIBLE, "Estante B4", LocalDateTime.of(2025, 6, 18, 10, 35), 58000, 0, ids.get("978-8483103913"), null),
                ejemplar("EJ-013", EstadoEjemplar.PRESTADO, "Estante D1", LocalDateTime.of(2024, 4, 22, 12, 10), 42000, 8, ids.get("978-9681649500"), null),
                ejemplar("EJ-014", EstadoEjemplar.DISPONIBLE, "Estante D1", LocalDateTime.of(2026, 2, 9, 9, 50), 42000, 1, ids.get("978-9681649500"), null),
                ejemplar("EJ-015", EstadoEjemplar.DISPONIBLE, "Estante D2", LocalDateTime.of(2025, 8, 27, 17, 25), 52000, 3, ids.get("978-8420633121"), null),
                ejemplar("EJ-016", EstadoEjemplar.PRESTADO, "Estante D2", LocalDateTime.of(2024, 10, 1, 13, 30), 89000, 4, ids.get("978-8437624747"), null),
                ejemplar("EJ-017", EstadoEjemplar.DANIADO, "Bodega", LocalDateTime.of(2023, 7, 19, 10, 5), 89000, 10, ids.get("978-8437624747"), "Manchas de humedad"),
                ejemplar("EJ-018", EstadoEjemplar.DISPONIBLE, "Mostrador", LocalDateTime.of(2026, 5, 4, 11, 45), 35000, 0, ids.get("978-9580469117"), null),
                ejemplar("EJ-019", EstadoEjemplar.DISPONIBLE, "Estante E1", LocalDateTime.of(2025, 11, 10, 15, 55), 79000, 2, ids.get("978-8432311451"), null),
                ejemplar("EJ-020", EstadoEjemplar.DADO_DE_BAJA, "Bodega", LocalDateTime.of(2021, 9, 8, 9, 30), 79000, 14, ids.get("978-8432311451"), null));
        ejemplares.insert(copias);
        log.info("Datos de prueba cargados en MongoDB: {} libros y {} ejemplares", iniciales.size(), copias.size());
    }

    private static Libro libro(String isbn, String titulo, String autor, double precio, int paginas,
                               LocalDateTime publicacion, Genero genero, String editorial, String sinopsis) {
        return Libro.builder()
                .isbn(isbn).titulo(titulo).autor(autor)
                .precio(precio).numeroPaginas(paginas)
                .fechaPublicacion(publicacion).genero(genero)
                .editorial(editorial).sinopsis(sinopsis)
                .build();
    }

    private static Ejemplar ejemplar(String codigo, EstadoEjemplar estado, String ubicacion, LocalDateTime ingreso,
                                     double precioReposicion, int prestamos, ObjectId libroId, String observaciones) {
        return Ejemplar.builder()
                .codigo(codigo).estado(estado).ubicacion(ubicacion)
                .fechaIngreso(ingreso).precioReposicion(precioReposicion)
                .numeroPrestamos(prestamos).libroId(libroId)
                .observaciones(observaciones)
                .build();
    }
}
