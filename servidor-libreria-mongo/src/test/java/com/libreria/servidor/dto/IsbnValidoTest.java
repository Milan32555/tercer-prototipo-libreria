package com.libreria.servidor.dto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IsbnValidoTest {

    private final IsbnValido.Validador validador = new IsbnValido.Validador();

    @ParameterizedTest
    @ValueSource(strings = {"978-0307474728", "9780307474728", "0-306-40615-X", "030640615x", "84-376-0494-7"})
    void aceptaIsbn10E13ConGuiones(String isbn) {
        assertThat(validador.isValid(isbn, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"978-030747472", "978--0307474728", "-9780307474728", "ISBN 9780307474728",
            "978-0307474728-", "97803074747289", "X-306-40615-0", "978.0307474728"})
    void rechazaFormatosInvalidos(String isbn) {
        assertThat(validador.isValid(isbn, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void vacioLoValidaNotBlank(String isbn) {
        assertThat(validador.isValid(isbn, null)).isTrue();
    }
}
