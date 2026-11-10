package com.libreria.servidor.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Patrón Observer en el servidor: Spring Data MongoDB publica un evento cada vez que guarda o
 * elimina un documento, y este observador lo recibe y aumenta la revisión de esa colección.
 * Así ningún servicio tiene que acordarse de avisar: cualquier cambio en libros o ejemplares
 * queda reflejado en la revisión que consultan los clientes.
 */
@Component
public class ObservadorColecciones extends AbstractMongoEventListener<Object> {

    private static final Logger log = LoggerFactory.getLogger(ObservadorColecciones.class);
    private static final Set<String> OBSERVADAS = Set.of("libros", "ejemplares");

    private final ServicioRevisiones revisiones;

    public ObservadorColecciones(ServicioRevisiones revisiones) {
        this.revisiones = revisiones;
    }

    @Override
    public void onAfterSave(AfterSaveEvent<Object> evento) {
        notificar(evento.getCollectionName(), "guardado");
    }

    @Override
    public void onAfterDelete(AfterDeleteEvent<Object> evento) {
        notificar(evento.getCollectionName(), "eliminado");
    }

    private void notificar(String coleccion, String cambio) {
        if (OBSERVADAS.contains(coleccion)) {
            revisiones.incrementar(coleccion);
            log.debug("Cambio en {} ({}): revisión aumentada", coleccion, cambio);
        }
    }
}
