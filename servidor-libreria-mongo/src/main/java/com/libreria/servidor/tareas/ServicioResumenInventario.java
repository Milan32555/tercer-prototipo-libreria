package com.libreria.servidor.tareas;

import com.libreria.servidor.model.ConteoEstado;
import com.libreria.servidor.model.EstadoEjemplar;
import com.libreria.servidor.model.ResultadoTarea;
import com.libreria.servidor.model.ResumenInventario;
import com.libreria.servidor.repository.EjemplarRepository;
import com.libreria.servidor.repository.LibroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Servicio con 3 tareas concurrentes (como el POC de Hilos y CompletableFuture):
 * <ol>
 *     <li>contar los libros,</li>
 *     <li>contar los ejemplares por estado (agregación $group),</li>
 *     <li>calcular el valor total del inventario (suma de precioReposicion).</li>
 * </ol>
 * Las tres se lanzan a la vez en el ejecutor "ejecutorTareas" (hilos tarea-N), se esperan con
 * allOf y cada una tiene un tiempo máximo. Si una falla o se demora, el resumen igual se entrega
 * con las otras dos y la falla queda registrada con logger.error.
 */
@Service
public class ServicioResumenInventario {

    private static final Logger log = LoggerFactory.getLogger(ServicioResumenInventario.class);

    private final LibroRepository libros;
    private final EjemplarRepository ejemplares;
    private final TaskExecutor ejecutor;
    private final long tiempoMaximoMs;

    public ServicioResumenInventario(LibroRepository libros, EjemplarRepository ejemplares,
                                     @Qualifier("ejecutorTareas") TaskExecutor ejecutor,
                                     @Value("${libreria.tareas.tiempo-maximo-ms:5000}") long tiempoMaximoMs) {
        this.libros = libros;
        this.ejemplares = ejemplares;
        this.ejecutor = ejecutor;
        this.tiempoMaximoMs = tiempoMaximoMs;
    }

    public ResumenInventario resumen() {
        long inicio = System.nanoTime();
        log.info("Resumen del inventario: se lanzan 3 tareas en paralelo (hilo {})", Thread.currentThread().getName());

        CompletableFuture<Parcial<Long>> totalLibros =
                lanzar("contar-libros", libros::count, -1L);
        CompletableFuture<Parcial<Map<EstadoEjemplar, Long>>> porEstado =
                lanzar("contar-ejemplares-por-estado", () -> ejemplares.contarPorEstado(null), conteoVacio());
        CompletableFuture<Parcial<Double>> valorTotal =
                lanzar("valor-total-inventario", ejemplares::sumarPrecioReposicion, -1.0);

        try {
            CompletableFuture.allOf(totalLibros, porEstado, valorTotal).join();
        } catch (CompletionException e) {
            // No debería pasar: cada tarea ya maneja su propia falla con exceptionally.
            log.error("Fallo inesperado esperando las tareas del resumen", e);
        }

        Map<EstadoEjemplar, Long> conteo = porEstado.join().valor();
        int totalEjemplares = (int) conteo.values().stream().mapToLong(Long::longValue).sum();
        List<ConteoEstado> estados = Arrays.stream(EstadoEjemplar.values())
                .map(estado -> {
                    int cantidad = conteo.getOrDefault(estado, 0L).intValue();
                    return new ConteoEstado(estado, cantidad,
                            totalEjemplares == 0 ? 0 : cantidad * 100.0 / totalEjemplares);
                })
                .toList();
        List<ResultadoTarea> tareas = List.of(totalLibros.join().tarea(), porEstado.join().tarea(),
                valorTotal.join().tarea());
        long duracion = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

        log.info("Resumen del inventario listo en {} ms: {} libros, {} ejemplares, valor ${}", duracion,
                totalLibros.join().valor(), totalEjemplares, String.format("%,.0f", valorTotal.join().valor()));
        return new ResumenInventario(totalLibros.join().valor().intValue(), totalEjemplares, estados,
                valorTotal.join().valor(), duracion, tareas);
    }

    /** Lanza una tarea en el ejecutor propio, con tiempo máximo y manejo de su falla. */
    private <T> CompletableFuture<Parcial<T>> lanzar(String nombre, Supplier<T> trabajo, T valorSiFalla) {
        return CompletableFuture.supplyAsync(() -> {
                    String hilo = Thread.currentThread().getName();
                    long inicio = System.nanoTime();
                    log.info("Tarea {} iniciada en el hilo {}", nombre, hilo);
                    T valor = trabajo.get();
                    long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
                    log.info("Tarea {} terminada en el hilo {} ({} ms)", nombre, hilo, ms);
                    return new Parcial<>(valor, new ResultadoTarea(nombre, hilo, ms, true, "OK"));
                }, ejecutor)
                .orTimeout(tiempoMaximoMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    Throwable causa = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                    String mensaje = causa instanceof TimeoutException
                            ? "Superó el tiempo máximo de " + tiempoMaximoMs + " ms"
                            : causa.getClass().getSimpleName() + ": " + causa.getMessage();
                    log.error("Tarea {} falló: {}", nombre, mensaje, causa);
                    return new Parcial<>(valorSiFalla, new ResultadoTarea(nombre, "-", 0, false, mensaje));
                });
    }

    private static Map<EstadoEjemplar, Long> conteoVacio() {
        Map<EstadoEjemplar, Long> conteo = new EnumMap<>(EstadoEjemplar.class);
        for (EstadoEjemplar estado : EstadoEjemplar.values()) {
            conteo.put(estado, 0L);
        }
        return conteo;
    }

    /** Resultado de una tarea: el valor calculado y cómo terminó. */
    private record Parcial<T>(T valor, ResultadoTarea tarea) {
    }
}
