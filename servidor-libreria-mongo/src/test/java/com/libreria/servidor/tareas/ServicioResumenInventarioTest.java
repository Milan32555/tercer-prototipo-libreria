package com.libreria.servidor.tareas;

import com.libreria.servidor.PruebaBase;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.ResultadoTarea;
import com.libreria.servidor.model.ResumenInventario;
import com.libreria.servidor.repository.EjemplarRepository;
import com.libreria.servidor.repository.LibroRepository;
import com.libreria.servidor.servicio.ServicioEjemplar;
import com.libreria.servidor.servicio.ServicioLibro;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicioResumenInventarioTest extends PruebaBase {

    @Autowired
    ServicioResumenInventario servicio;

    @Autowired
    ServicioLibro servicioLibro;

    @Autowired
    ServicioEjemplar servicioEjemplar;

    @Autowired
    LibroRepository libros;

    @Autowired
    EjemplarRepository ejemplares;

    @Autowired
    @Qualifier("ejecutorTareas")
    TaskExecutor ejecutor;

    @Test
    void lasTresTareasCorrenEnHilosPropiosYCalculanElResumen() {
        servicioLibro.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        servicioLibro.crear(libro(ISBN_MARIA, "María"));
        servicioEjemplar.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "A1"));
        servicioEjemplar.crear(ejemplar("EJ-002", ISBN_CIEN, EstadoEjemplar.PRESTADO, "A1"));
        servicioEjemplar.crear(ejemplar("EJ-003", ISBN_MARIA, EstadoEjemplar.PRESTADO, "B3"));
        servicioEjemplar.crear(ejemplar("EJ-004", ISBN_MARIA, EstadoEjemplar.DANIADO, "Bodega"));

        ResumenInventario resumen = servicio.resumen();

        assertThat(resumen.totalLibros()).isEqualTo(2);
        assertThat(resumen.totalEjemplares()).isEqualTo(4);
        assertThat(resumen.valorInventario()).isEqualTo(4 * 65000.0);
        assertThat(resumen.ejemplaresPorEstado()).extracting("cantidad").containsExactly(1, 2, 1, 0);
        assertThat(resumen.ejemplaresPorEstado().get(1).porcentaje()).isEqualTo(50.0);
        assertThat(resumen.tareas()).extracting(ResultadoTarea::nombre)
                .containsExactly("contar-libros", "contar-ejemplares-por-estado", "valor-total-inventario");
        assertThat(resumen.tareas()).allSatisfy(t -> {
            assertThat(t.exitosa()).isTrue();
            assertThat(t.hilo()).startsWith("tarea-");
        });
        System.out.printf("[TAREAS] resumen en %d ms: %s%n", resumen.duracionMs(), resumen.tareas());
    }

    @Test
    void siUnaTareaFallaLasOtrasDosIgualEntreganSuResultado() {
        LibroRepository librosQueFallan = mock(LibroRepository.class);
        when(librosQueFallan.count()).thenThrow(new DataAccessResourceFailureException("MongoDB no responde"));
        ServicioResumenInventario conFalla = new ServicioResumenInventario(librosQueFallan, ejemplares, ejecutor, 5000);
        servicioLibro.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        servicioEjemplar.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "A1"));

        ResumenInventario resumen = conFalla.resumen();

        assertThat(resumen.totalLibros()).isEqualTo(-1);
        assertThat(resumen.totalEjemplares()).isEqualTo(1);
        assertThat(resumen.tareas().get(0).exitosa()).isFalse();
        assertThat(resumen.tareas().get(0).mensaje()).contains("MongoDB no responde");
        assertThat(resumen.tareas().get(1).exitosa()).isTrue();
        assertThat(resumen.tareas().get(2).exitosa()).isTrue();
    }

    @Test
    void unaTareaQueSuperaElTiempoMaximoSeCancelaSinBloquearElResumen() {
        LibroRepository librosLentos = mock(LibroRepository.class);
        when(librosLentos.count()).thenAnswer(invocacion -> {
            Thread.sleep(2_000);
            return 7L;
        });
        ServicioResumenInventario conTiempoCorto = new ServicioResumenInventario(librosLentos, ejemplares, ejecutor, 300);

        long inicio = System.currentTimeMillis();
        ResumenInventario resumen = conTiempoCorto.resumen();
        long ms = System.currentTimeMillis() - inicio;

        assertThat(ms).isLessThan(1_500);
        assertThat(resumen.tareas().get(0).exitosa()).isFalse();
        assertThat(resumen.tareas().get(0).mensaje()).isEqualTo("Superó el tiempo máximo de 300 ms");
        assertThat(libros.count()).isZero();
    }
}
