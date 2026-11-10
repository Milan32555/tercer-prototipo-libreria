package com.libreria.servidor.dto;

import com.libreria.servidor.model.Genero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Datos de un libro, para crearlo o actualizarlo (un solo input, como pidió el profesor).
 * Al actualizar, el isbn indica qué libro se modifica (no se cambia) y version es la que el
 * cliente leyó al buscarlo: si otro usuario lo cambió entretanto, la actualización se rechaza.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibroInput {

    @NotBlank(message = "El ISBN es obligatorio.")
    @IsbnValido
    private String isbn;

    @NotBlank(message = "El título es obligatorio.")
    @Size(max = 200, message = "El título admite máximo 200 caracteres.")
    private String titulo;

    @NotBlank(message = "El autor es obligatorio.")
    @Size(max = 120, message = "El autor admite máximo 120 caracteres.")
    private String autor;

    @Positive(message = "El precio debe ser mayor que cero.")
    private double precio;

    @Positive(message = "El número de páginas debe ser mayor que cero.")
    private int numeroPaginas;

    @NotNull(message = "La fecha de publicación es obligatoria.")
    @PastOrPresent(message = "La fecha de publicación no puede ser futura.")
    private LocalDateTime fechaPublicacion;

    @NotNull(message = "El género es obligatorio.")
    private Genero genero;

    /** Opcional. */
    @Size(max = 120, message = "La editorial admite máximo 120 caracteres.")
    private String editorial;

    /** Opcional. */
    @Size(max = 500, message = "La sinopsis admite máximo 500 caracteres.")
    private String sinopsis;

    /** Solo al actualizar: versión leída por el cliente (concurrencia optimista). */
    private Long version;
}
