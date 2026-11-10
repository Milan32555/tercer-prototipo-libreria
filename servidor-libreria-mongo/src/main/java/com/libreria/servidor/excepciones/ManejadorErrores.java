package com.libreria.servidor.excepciones;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Convierte las excepciones del servidor en errores GraphQL con un mensaje legible en español.
 * El campo extensions.codigo dice qué tipo de rechazo fue (VALIDACION, DUPLICADO, CONFLICTO...).
 */
@Component
public class ManejadorErrores extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        String operacion = env.getField().getName();

        if (ex instanceof LibreriaException e) {
            // Los servicios ya registraron el rechazo (warn) o la falla (error).
            return error(env, e.getTipo(), e.getMessage());
        }
        if (ex instanceof ConstraintViolationException e) {
            String mensaje = mensajeValidacion(e);
            log.warn("Validación rechazada en {}: {}", operacion, mensaje);
            return error(env, LibreriaException.Tipo.VALIDACION, mensaje);
        }
        if (ex instanceof OptimisticLockingFailureException) {
            log.warn("Conflicto de versión en {}", operacion);
            return error(env, LibreriaException.Tipo.CONFLICTO,
                    "Otro usuario modificó este registro; vuelva a buscarlo.");
        }
        if (ex instanceof DuplicateKeyException) {
            log.warn("Llave duplicada en {}", operacion);
            return error(env, LibreriaException.Tipo.DUPLICADO, "Ya existe un registro con esa llave.");
        }
        log.error("Error inesperado en {}", operacion, ex);
        return error(env, LibreriaException.Tipo.ERROR_INTERNO,
                "Ocurrió un error interno en el servidor. Intente de nuevo.");
    }

    /** Todos los mensajes de validación, en orden alfabético para que la respuesta sea estable. */
    public static String mensajeValidacion(ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining(" "));
    }

    private static GraphQLError error(DataFetchingEnvironment env, LibreriaException.Tipo tipo, String mensaje) {
        ErrorType clasificacion = switch (tipo) {
            case NO_ENCONTRADO -> ErrorType.NOT_FOUND;
            case ERROR_INTERNO -> ErrorType.INTERNAL_ERROR;
            default -> ErrorType.BAD_REQUEST;
        };
        return GraphqlErrorBuilder.newError(env)
                .errorType(clasificacion)
                .message(mensaje)
                .extensions(Map.of("codigo", tipo.name()))
                .build();
    }
}
