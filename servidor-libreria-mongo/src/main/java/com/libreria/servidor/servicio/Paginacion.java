package com.libreria.servidor.servicio;

import com.libreria.servidor.excepciones.LibreriaException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Valida los parámetros de paginación que llegan de los clientes. */
final class Paginacion {

    static final int TAMANIO_MAXIMO = 100;

    private Paginacion() {
    }

    static Pageable crear(int pagina, int tamanio, Sort orden) {
        if (pagina < 0) {
            throw new LibreriaException(LibreriaException.Tipo.VALIDACION, "La página no puede ser negativa.");
        }
        if (tamanio < 1 || tamanio > TAMANIO_MAXIMO) {
            throw new LibreriaException(LibreriaException.Tipo.VALIDACION,
                    "El tamaño de página debe estar entre 1 y " + TAMANIO_MAXIMO + ".");
        }
        return PageRequest.of(pagina, tamanio, orden);
    }

    /** Texto sin espacios sobrantes; null si queda vacío (para los campos opcionales). */
    static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
