package com.libreria.servidor.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

/**
 * Validación propia (Bean Validation): el ISBN del libro es un código corto de 1 a 4 dígitos
 * (solo números), por ejemplo 1001. Un valor vacío se considera válido aquí; que sea obligatorio
 * lo exige @NotBlank.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsbnValido.Validador.class)
public @interface IsbnValido {

    String message() default "El ISBN debe tener de 1 a 4 dígitos (solo números), por ejemplo 1001.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<IsbnValido, String> {

        private static final Pattern FORMATO = Pattern.compile("^\\d{1,4}$");

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            if (valor == null || valor.isBlank()) {
                return true;
            }
            return FORMATO.matcher(valor.trim()).matches();
        }
    }
}
