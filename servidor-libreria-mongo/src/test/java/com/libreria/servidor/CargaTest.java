package com.libreria.servidor;

import com.libreria.servidor.model.EjemplarConLibro;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Genero;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.model.Pagina;
import com.libreria.servidor.model.ResultadoCarga;
import com.libreria.servidor.model.ResumenInventario;
import com.libreria.servidor.servicio.GeneradorDatosCarga;
import com.libreria.servidor.servicio.ServicioEjemplar;
import com.libreria.servidor.servicio.ServicioLibro;
import com.libreria.servidor.tareas.ServicioResumenInventario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de cantidad de datos: genera 1.000 libros y 5.000 ejemplares y mide cuánto tardan los
 * Listar paginados y la consulta $lookup. Imprime una tabla con los milisegundos.
 */
class CargaTest extends PruebaBase {

    private static final int REPETICIONES = 5;

    @Autowired
    GeneradorDatosCarga generador;

    @Autowired
    ServicioLibro servicioLibro;

    @Autowired
    ServicioEjemplar servicioEjemplar;

    @Autowired
    ServicioResumenInventario servicioResumen;

    @Test
    void listarConMilLibrosYCincoMilEjemplares() {
        ResultadoCarga carga = generador.generar(1_000, 5_000);
        assertThat(mongo.getCollection("libros").countDocuments()).isEqualTo(1_000);
        assertThat(mongo.getCollection("ejemplares").countDocuments()).isEqualTo(5_000);

        Map<String, Long> tiempos = new LinkedHashMap<>();
        tiempos.put("Generar 1.000 libros + 5.000 ejemplares", carga.duracionMs());

        Pagina<Libro> primera = medir(tiempos, "Listar libros, página 1 de 50 (20 por página)",
                () -> servicioLibro.listar(0, 20));
        Pagina<Libro> ultima = medir(tiempos, "Listar libros, página 50 de 50",
                () -> servicioLibro.listar(49, 20));
        Pagina<Libro> filtro = medir(tiempos, "Libros por precio 50.000-100.000 y FICCION",
                () -> servicioLibro.listarPorPrecioYGenero(50_000.0, 100_000.0, Genero.FICCION, 0, 20));
        Pagina<EjemplarConLibro> conLibro = medir(tiempos, "ejemplaresConLibro, página 1 (20)",
                () -> servicioEjemplar.listarConLibro(null, null, 0, 20));
        Pagina<EjemplarConLibro> conLibroUltima = medir(tiempos, "ejemplaresConLibro, página 250 de 250",
                () -> servicioEjemplar.listarConLibro(null, null, 249, 20));
        Pagina<EjemplarConLibro> prestados = medir(tiempos, "ejemplaresConLibro, filtro PRESTADO",
                () -> servicioEjemplar.listarConLibro(EstadoEjemplar.PRESTADO, null, 0, 20));
        ResumenInventario resumen = medir(tiempos, "resumenInventario (3 tareas)", servicioResumen::resumen);

        assertThat(primera.totalElementos()).isEqualTo(1_000);
        assertThat(primera.totalPaginas()).isEqualTo(50);
        assertThat(ultima.contenido()).hasSize(20);
        assertThat(filtro.contenido()).allSatisfy(l -> assertThat(l.getGenero()).isEqualTo(Genero.FICCION));
        assertThat(conLibro.totalElementos()).isEqualTo(5_000);
        assertThat(conLibro.contenido()).allSatisfy(e -> assertThat(e.getTituloLibro()).startsWith("Libro de carga"));
        assertThat(conLibroUltima.contenido()).hasSize(20);
        assertThat(prestados.totalElementos()).isEqualTo(1_250);
        assertThat(resumen.totalEjemplares()).isEqualTo(5_000);

        System.out.println();
        System.out.println("[CARGA] 1.000 libros y 5.000 ejemplares en MongoDB (promedio de " + REPETICIONES + " ejecuciones)");
        tiempos.forEach((consulta, ms) -> System.out.printf("[CARGA] %-45s %6d ms%n", consulta, ms));
        tiempos.forEach((consulta, ms) -> {
            if (!consulta.startsWith("Generar")) {
                assertThat(ms).as(consulta).isLessThan(2_000);
            }
        });
    }

    /** Ejecuta la consulta una vez para calentar y luego REPETICIONES veces; guarda el promedio. */
    private <T> T medir(Map<String, Long> tiempos, String nombre, Supplier<T> consulta) {
        T resultado = consulta.get();
        long inicio = System.nanoTime();
        for (int i = 0; i < REPETICIONES; i++) {
            resultado = consulta.get();
        }
        tiempos.put(nombre, (System.nanoTime() - inicio) / 1_000_000 / REPETICIONES);
        return resultado;
    }
}
