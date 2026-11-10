package com.libreria.servidor.excepciones;

/**
 * Excepción de negocio con un mensaje listo para mostrarle al usuario.
 * El tipo indica la clase de rechazo y define el código de error GraphQL.
 */
public class LibreriaException extends RuntimeException {

    public enum Tipo {
        /** Datos que no cumplen las validaciones. */
        VALIDACION,
        /** El libro o ejemplar buscado no existe. */
        NO_ENCONTRADO,
        /** Ya existe otro documento con la misma llave (ISBN o código). */
        DUPLICADO,
        /** Otro usuario modificó el documento (concurrencia optimista). */
        CONFLICTO,
        /** Una regla de negocio impide la operación. */
        REGLA_NEGOCIO,
        /** Falla inesperada (por ejemplo, la base de datos no responde). */
        ERROR_INTERNO
    }

    private final Tipo tipo;

    public LibreriaException(Tipo tipo, String mensaje) {
        super(mensaje);
        this.tipo = tipo;
    }

    public LibreriaException(Tipo tipo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.tipo = tipo;
    }

    public Tipo getTipo() {
        return tipo;
    }
}
