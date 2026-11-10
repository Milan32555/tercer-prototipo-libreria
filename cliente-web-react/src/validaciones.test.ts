import { describe, expect, it } from "vitest";
import {
  hayErrores, validar, validarCodigoEjemplar, validarDecimalPositivo, validarEnteroNoNegativo, validarEnteroPositivo,
  validarFecha, validarFechaNoFutura, validarIsbn, validarLargoMaximo, validarObligatorio
} from "./validaciones";

describe("validaciones de formato", () => {
  it("obligatorio y fecha", () => {
    expect(validarObligatorio("  ")).toBe("Este campo es obligatorio.");
    expect(validarObligatorio("María")).toBeNull();
    expect(validarFecha("")).toBe("Elige la fecha y la hora.");
    expect(validarFecha("2024-02-10T09:00")).toBeNull();
  });

  it("decimal positivo (precios)", () => {
    expect(validarDecimalPositivo("")).toBe("Este campo es obligatorio.");
    expect(validarDecimalPositivo("abc")).toBe("Escribe un número válido.");
    expect(validarDecimalPositivo("0")).toBe("Debe ser mayor que cero.");
    expect(validarDecimalPositivo("39000.5")).toBeNull();
  });

  it("enteros (páginas y préstamos)", () => {
    expect(validarEnteroPositivo("")).toBe("Este campo es obligatorio.");
    expect(validarEnteroPositivo("1.5")).toBe("Escribe un número entero.");
    expect(validarEnteroPositivo("0")).toBe("Debe ser mayor que cero.");
    expect(validarEnteroPositivo("129")).toBeNull();
    expect(validarEnteroNoNegativo("")).toBe("Este campo es obligatorio.");
    expect(validarEnteroNoNegativo("2.5")).toBe("Escribe un número entero.");
    expect(validarEnteroNoNegativo("-1")).toBe("No puede ser negativo.");
    expect(validarEnteroNoNegativo("0")).toBeNull();
  });

  it("reúne solo los campos con error", () => {
    const errores = validar({ isbn: validarObligatorio(""), titulo: validarObligatorio("Rayuela") });
    expect(errores).toEqual({ isbn: "Este campo es obligatorio." });
    expect(hayErrores(errores)).toBe(true);
    expect(hayErrores(validar({ titulo: null }))).toBe(false);
  });

  it("ISBN de 10 o 13 dígitos con guiones (igual que el servidor)", () => {
    expect(validarIsbn("978-0307474728")).toBeNull();
    expect(validarIsbn("0-306-40615-X")).toBeNull();
    expect(validarIsbn("")).toBe("Este campo es obligatorio.");
    expect(validarIsbn("978-030747472")).toBe("El ISBN debe tener 10 o 13 dígitos (se permiten guiones).");
    expect(validarIsbn("978--0307474728")).not.toBeNull();
    expect(validarIsbn("ISBN9780307474728")).not.toBeNull();
  });

  it("código del ejemplar con formato EJ-000", () => {
    expect(validarCodigoEjemplar("EJ-001")).toBeNull();
    expect(validarCodigoEjemplar("ej-100001")).toBeNull();
    expect(validarCodigoEjemplar("EJ-1")).toBe("Usa el formato EJ-000 (EJ- y de 3 a 6 dígitos).");
    expect(validarCodigoEjemplar(" ")).toBe("Este campo es obligatorio.");
  });

  it("fecha no futura y largo máximo de los opcionales", () => {
    const ahora = new Date("2026-11-10T12:00:00");
    expect(validarFechaNoFutura("2026-11-10T11:59", ahora)).toBeNull();
    expect(validarFechaNoFutura("2026-11-10T12:01", ahora)).toBe("La fecha no puede ser futura.");
    expect(validarFechaNoFutura("", ahora)).toBe("Elige la fecha y la hora.");
    expect(validarLargoMaximo("abc", 3)).toBeNull();
    expect(validarLargoMaximo("abcd", 3)).toBe("Máximo 3 caracteres.");
  });
});
