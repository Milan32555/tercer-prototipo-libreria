package com.libreria.servidor.dto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IsbnValidoTest {

    private final IsbnValido.Validador validador = new IsbnValido.Validador();

    @ParameterizedTest
    @ValueSource(strings = {"1", "42", "907", "1001", "9999", " 1002 "})
    void aceptaDeUnoACuatroDigitos(String isbn) {
        assertThat(validador.isValid(isbn, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "978-0307474728", "10-01", "ab12", "1 001", "-1", "10.1", "ISBN1"})
    void rechazaMasDeCuatroDigitosOLetras(String isbn) {
        assertThat(validador.isValid(isbn, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void vacioLoValidaNotBlank(String isbn) {
        assertThat(validador.isValid(isbn, null)).isTrue();
    }
}
