package com.libreria.servidor.controller;

import com.libreria.servidor.excepciones.LibreriaException;
import com.libreria.servidor.model.ResultadoCarga;
import com.libreria.servidor.model.ResumenInventario;
import com.libreria.servidor.servicio.GeneradorDatosCarga;
import com.libreria.servidor.tareas.ServicioResumenInventario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Controller GraphQL del resumen del inventario (3 tareas) y del generador de datos de carga. */
@Controller
public class ResumenController {

    private static final Logger log = LoggerFactory.getLogger(ResumenController.class);

    private final ServicioResumenInventario servicioResumen;
    private final GeneradorDatosCarga generador;
    private final boolean cargaHabilitada;

    public ResumenController(ServicioResumenInventario servicioResumen, GeneradorDatosCarga generador,
                             @Value("${libreria.carga.habilitada:false}") boolean cargaHabilitada) {
        this.servicioResumen = servicioResumen;
        this.generador = generador;
        this.cargaHabilitada = cargaHabilitada;
    }

    @QueryMapping
    public ResumenInventario resumenInventario() {
        log.info("GraphQL resumenInventario()");
        return servicioResumen.resumen();
    }

    /** Solo en desarrollo: con el perfil "carga" (libreria.carga.habilitada=true). */
    @MutationMapping
    public ResultadoCarga generarDatosCarga(@Argument int libros, @Argument int ejemplares) {
        log.info("GraphQL generarDatosCarga(libros={}, ejemplares={})", libros, ejemplares);
        if (!cargaHabilitada) {
            log.warn("generarDatosCarga rechazada: el servidor no está en el perfil carga");
            throw new LibreriaException(LibreriaException.Tipo.REGLA_NEGOCIO,
                    "El generador de datos solo está disponible con el perfil \"carga\" (desarrollo).");
        }
        return generador.generar(libros, ejemplares);
    }
}
