import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import DetalleLibro from "./DetalleLibro";
import { consultarDetalleLibro } from "../../graphql/servicioLibro";
import { ErrorGraphQL } from "../../graphql/clienteGraphQL";
import { renderizar } from "../../pruebas/utilidades";
import { DetalleLibro as Detalle } from "../../types";

vi.mock("../../graphql/servicioLibro", () => ({
  consultarDetalleLibro: vi.fn()
}));

const DETALLE: Detalle = {
  libro: {
    isbn: "1001", titulo: "Cien años de soledad", autor: "Gabriel García Márquez", precio: 65000,
    numeroPaginas: 496, fechaPublicacion: "1967-05-30T00:00:00", genero: "FICCION", editorial: "Sudamericana"
  },
  ejemplares: [
    { codigo: "EJ-001", isbnLibro: "1001", estado: "DISPONIBLE", ubicacion: "Estante A1",
      fechaIngreso: "2024-02-10T09:00:00", precioReposicion: 65000, numeroPrestamos: 4 },
    { codigo: "EJ-002", isbnLibro: "1001", estado: "PRESTADO", ubicacion: "Estante A1",
      fechaIngreso: "2024-02-10T09:05:00", precioReposicion: 65000, numeroPrestamos: 9, observaciones: "Firmado" }
  ],
  conteoPorEstado: [
    { estado: "DISPONIBLE", cantidad: 1, porcentaje: 50 },
    { estado: "PRESTADO", cantidad: 1, porcentaje: 50 },
    { estado: "DANIADO", cantidad: 0, porcentaje: 0 },
    { estado: "DADO_DE_BAJA", cantidad: 0, porcentaje: 0 }
  ],
  totalEjemplares: 2,
  totalPrestamos: 13,
  valorReposicion: 130000
};

async function buscar(isbn: string) {
  const { user } = renderizar(<DetalleLibro />);
  await user.type(screen.getByLabelText("ISBN del libro"), isbn);
  await user.click(screen.getByRole("button", { name: "Buscar" }));
}

describe("DetalleLibro (maestro + detalle)", () => {
  it("muestra el libro, el resumen calculado por el servidor y la grilla de sus ejemplares", async () => {
    vi.mocked(consultarDetalleLibro).mockResolvedValue(DETALLE);

    await buscar("1001");

    const maestro = await screen.findByRole("article", { name: "Libro Cien años de soledad" });
    expect(within(maestro).getByText("Maestro: libro")).toBeInTheDocument();
    expect(within(maestro).getByText("Sudamericana")).toBeInTheDocument();

    const resumen = screen.getByRole("region", { name: "Resumen de sus ejemplares" });
    expect(within(resumen).getByText("13")).toBeInTheDocument();
    expect(within(resumen).getByText(/130\.000/)).toBeInTheDocument();
    expect(within(resumen).getAllByText("1 · 50 %")).toHaveLength(2);

    const tabla = screen.getByRole("table", { name: "Ejemplares del libro" });
    expect(within(tabla).getByText("EJ-001")).toBeInTheDocument();
    expect(within(tabla).getByText("Firmado")).toBeInTheDocument();
    expect(screen.getByText("Detalle: 2 ejemplares")).toBeInTheDocument();
  });

  it("informa si el libro no existe y muestra los errores del servidor", async () => {
    vi.mocked(consultarDetalleLibro).mockResolvedValueOnce(null);
    await buscar("9999");
    expect(await screen.findByText("No hay ningún libro con el ISBN «9999».")).toBeInTheDocument();
  });

  it("muestra el error de conexión", async () => {
    vi.mocked(consultarDetalleLibro).mockRejectedValue(new ErrorGraphQL("No se pudo conectar con el servidor."));
    await buscar("1001");
    expect(await screen.findByRole("alert")).toHaveTextContent("No se pudo conectar con el servidor.");
  });
});
