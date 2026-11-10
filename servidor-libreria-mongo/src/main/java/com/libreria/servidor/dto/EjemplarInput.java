package com.libreria.servidor.dto;

import com.libreria.servidor.model.EstadoEjemplar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Datos de un ejemplar, para crearlo o actualizarlo (un solo input). Al actualizar, el código
 * indica qué ejemplar se modifica; numeroPrestamos se ignora porque lo lleva el servidor
 * (le suma 1 cuando el ejemplar pasa a PRESTADO).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EjemplarInput {

    @NotBlank(message = "El código del ejemplar es obligatorio.")
    @Pattern(regexp = "^(?i)EJ-\\d{3,6}$", message = "El código debe tener el formato EJ-000 (EJ- y de 3 a 6 dígitos).")
    private String codigo;

    @NotBlank(message = "El ISBN del libro es obligatorio.")
    @IsbnValido
    private String isbnLibro;

    @NotNull(message = "El estado del ejemplar es obligatorio.")
    private EstadoEjemplar estado;

    @NotBlank(message = "La ubicación es obligatoria.")
    @Size(max = 60, message = "La ubicación admite máximo 60 caracteres.")
    private String ubicacion;

    @NotNull(message = "La fecha de ingreso es obligatoria.")
    @PastOrPresent(message = "La fecha de ingreso no puede ser futura.")
    private LocalDateTime fechaIngreso;

    @Positive(message = "El precio de reposición debe ser mayor que cero.")
    private double precioReposicion;

    /** Solo al crear (si no llega, 0). Al actualizar se ignora. */
    @PositiveOrZero(message = "El número de préstamos no puede ser negativo.")
    private Integer numeroPrestamos;

    /** Opcional. */
    @Size(max = 300, message = "Las observaciones admiten máximo 300 caracteres.")
    private String observaciones;

    /** Solo al actualizar: versión leída por el cliente (concurrencia optimista). */
    private Long version;
}
