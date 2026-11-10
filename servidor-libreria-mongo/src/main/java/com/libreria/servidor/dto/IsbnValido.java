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
 * Validación propia (Bean Validation): un ISBN-10 o ISBN-13 escrito con dígitos y guiones.
 * Un valor vacío se considera válido aquí; que sea obligatorio lo exige @NotBlank.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsbnValido.Validador.class)
public @interface IsbnValido {

    String message() default "El ISBN debe tener 10 o 13 dígitos (se permiten guiones), por ejemplo 978-0307474728.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<IsbnValido, String> {

        /** Solo dígitos y guiones; empieza y termina en dígito (el ISBN-10 puede terminar en X). */
        private static final Pattern CARACTERES = Pattern.compile("^\\d[\\d-]*[\\dXx]$");
        private static final Pattern DIGITOS = Pattern.compile("^(\\d{9}[\\dXx]|\\d{13})$");

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            if (valor == null || valor.isBlank()) {
                return true;
            }
            String texto = valor.trim();
            return CARACTERES.matcher(texto).matches()
                    && !texto.contains("--")
                    && DIGITOS.matcher(texto.replace("-", "")).matches();
        }
    }
}
