package com.libreria.servidor;

import com.libreria.servidor.dto.EjemplarInput;
import com.libreria.servidor.dto.LibroInput;
import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.model.Ejemplar;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.Libro;
import com.libreria.servidor.servicio.ServicioEjemplar;
import com.libreria.servidor.servicio.ServicioLibro;
import com.libreria.servidor.servicio.ServicioRevisiones;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia contra MongoDB real: varios hilos hacen la misma operación al mismo tiempo
 * (todos esperan una señal de salida para arrancar juntos).
 */
class ConcurrenciaTest extends PruebaBase {

    @Autowired
    ServicioLibro servicioLibro;

    @Autowired
    ServicioEjemplar servicioEjemplar;

    @Autowired
    ServicioRevisiones revisiones;

    @Test
    void veinteAltasSimultaneasDelMismoIsbnDejanExactamenteUna() throws Exception {
        List<String> resultados = enParalelo(20, () -> servicioLibro.crear(libro(ISBN_CIEN, "Cien años de soledad")));

        System.out.printf("[CONCURRENCIA] 20 altas del mismo ISBN: %d éxito(s), %d rechazo(s) \"Ya existe\"%n",
                resultados.stream().filter("OK"::equals).count(),
                resultados.stream().filter(r -> r.startsWith("DUPLICADO")).count());
        assertThat(resultados).filteredOn("OK"::equals).hasSize(1);
        assertThat(resultados).filteredOn(r -> r.equals("DUPLICADO: Ya existe un libro con el ISBN 978-0307474728."))
                .hasSize(19);
        assertThat(mongo.getCollection("libros").countDocuments()).isEqualTo(1);
    }

    @Test
    void veinteAltasSimultaneasDelMismoCodigoDejanExactamenteUna() throws Exception {
        servicioLibro.crear(libro(ISBN_CIEN, "Cien años de soledad"));

        List<String> resultados = enParalelo(20,
                () -> servicioEjemplar.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "Estante A1")));

        assertThat(resultados).filteredOn("OK"::equals).hasSize(1);
        assertThat(resultados).filteredOn(r -> r.startsWith("DUPLICADO")).hasSize(19);
        assertThat(mongo.getCollection("ejemplares").countDocuments()).isEqualTo(1);
    }

    @Test
    void dosActualizacionesConLaMismaVersionDejanUnConflicto() throws Exception {
        Libro leido = servicioLibro.crear(libro(ISBN_CIEN, "Cien años de soledad"));

        List<String> resultados = enParalelo(2, () -> {
            LibroInput cambio = libro(ISBN_CIEN, "Cambio de " + Thread.currentThread().getName());
            cambio.setVersion(leido.getVersion());
            return servicioLibro.actualizar(cambio);
        });

        System.out.printf("[CONCURRENCIA] 2 actualizaciones con la versión %d: %s%n", leido.getVersion(), resultados);
        assertThat(resultados).filteredOn("OK"::equals).hasSize(1);
        assertThat(resultados).containsOnlyOnce("CONFLICTO: Otro usuario modificó este libro; vuelva a buscarlo.");
        assertThat(servicioLibro.buscarPorIsbn(ISBN_CIEN).orElseThrow().getVersion()).isEqualTo(1);
    }

    @Test
    void prestamosSimultaneosDelMismoEjemplarNoSePierden() throws Exception {
        servicioLibro.crear(libro(ISBN_CIEN, "Cien años de soledad"));
        Ejemplar creado = servicioEjemplar.crear(ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.DISPONIBLE, "A1"));

        // Dos clientes leyeron el ejemplar DISPONIBLE y lo prestan a la vez: solo uno puede ganar.
        List<String> resultados = enParalelo(2, () -> {
            EjemplarInput prestar = ejemplar("EJ-001", ISBN_CIEN, EstadoEjemplar.PRESTADO, "A1");
            prestar.setVersion(creado.getVersion());
            return servicioEjemplar.actualizar(prestar);
        });

        assertThat(resultados).filteredOn("OK"::equals).hasSize(1);
        assertThat(resultados).filteredOn(r -> r.startsWith("CONFLICTO")).hasSize(1);
        assertThat(servicioEjemplar.buscarPorCodigo("EJ-001").orElseThrow().getNumeroPrestamos()).isEqualTo(5);
    }

    @Test
    void laRevisionCuentaCadaCambioAunqueSeanSimultaneos() throws Exception {
        long antes = revisiones.valor("libros");

        List<String> resultados = enParalelo(10, () -> {
            String n = Thread.currentThread().getName().replaceAll("\\D", "");
            return servicioLibro.crear(libro(String.format("978-00000000%02d", Integer.parseInt(n) % 100), "L" + n));
        });

        long exitos = resultados.stream().filter("OK"::equals).count();
        assertThat(revisiones.valor("libros") - antes).isEqualTo(exitos);
    }

    /** Ejecuta la operación en n hilos que arrancan al mismo tiempo; devuelve "OK" o "TIPO: mensaje". */
    private static List<String> enParalelo(int n, Callable<Object> operacion) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(n);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<String>> futuros = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futuros.add(hilos.submit(() -> {
                salida.await();
                try {
                    operacion.call();
                    return "OK";
                } catch (LibreriaException e) {
                    return e.getTipo() + ": " + e.getMessage();
                }
            }));
        }
        salida.countDown();
        List<String> resultados = new ArrayList<>();
        for (Future<String> futuro : futuros) {
            resultados.add(futuro.get(30, TimeUnit.SECONDS));
        }
        hilos.shutdown();
        return resultados;
    }
}
