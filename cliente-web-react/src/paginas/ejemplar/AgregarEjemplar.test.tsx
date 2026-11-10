import { fireEvent, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AgregarEjemplar from "./AgregarEjemplar";
import { crearEjemplar } from "../../graphql/servicioEjemplar";
import { buscarLibroPorIsbn } from "../../graphql/servicioLibro";
import { ErrorGraphQL } from "../../graphql/clienteGraphQL";
import { ejemplarObservable } from "../../observer/Observable";
import { elegirOpcion, renderizar } from "../../pruebas/utilidades";

vi.mock("../../graphql/servicioEjemplar", () => ({
  crearEjemplar: vi.fn(),
  consultarRevisionEjemplares: vi.fn()
}));

vi.mock("../../graphql/servicioLibro", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../graphql/servicioLibro")>()),
  buscarLibroPorIsbn: vi.fn()
}));

const CIEN_ANOS = {
  isbn: "978-0307474728", titulo: "Cien años de soledad", autor: "Gabriel García Márquez", precio: 65000,
  numeroPaginas: 496, fechaPublicacion: "1967-05-30T00:00:00", genero: "FICCION" as const
};

async function llenarFormulario(prestamos: string) {
  vi.mocked(buscarLibroPorIsbn).mockResolvedValue(CIEN_ANOS);
  const resultado = renderizar(<AgregarEjemplar />);
  const { user } = resultado;
  await user.type(screen.getByLabelText("Código del ejemplar"), "EJ-100");
  await user.type(screen.getByLabelText("ISBN del libro"), "978-0307474728");
  await user.type(screen.getByLabelText("Ubicación"), "Estante C3");
  fireEvent.change(screen.getByLabelText("Fecha de ingreso"), { target: { value: "2024-05-10T14:30" } });
  await user.type(screen.getByLabelText("Precio de reposición"), "45000");
  await user.clear(screen.getByLabelText("Número de préstamos"));
  await user.type(screen.getByLabelText("Número de préstamos"), prestamos);
  await elegirOpcion(user, "Estado", "Prestado");
  // El libro se elige por su ISBN: se muestra su título y autor.
  expect(await screen.findByText("Cien años de soledad")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Adicionar ejemplar" }));
  return user;
}

describe("AgregarEjemplar", () => {
  it("envía todos los atributos (incluido el número de préstamos) y notifica al Observer", async () => {
    vi.mocked(crearEjemplar).mockImplementation(async (ejemplar) => ({
      ...ejemplar, numeroPrestamos: ejemplar.numeroPrestamos ?? 0, libro: { titulo: CIEN_ANOS.titulo, autor: CIEN_ANOS.autor }
    }));
    const notificar = vi.spyOn(ejemplarObservable, "notificar");

    await llenarFormulario("3");

    expect(await screen.findByText("Ejemplar adicionado")).toBeInTheDocument();
    expect(screen.getByText("EJ-100 quedó registrado para «Cien años de soledad».")).toBeInTheDocument();
    expect(crearEjemplar).toHaveBeenCalledWith({
      codigo: "EJ-100", isbnLibro: "978-0307474728", estado: "PRESTADO", ubicacion: "Estante C3",
      fechaIngreso: "2024-05-10T14:30", precioReposicion: 45000, numeroPrestamos: 3, observaciones: null
    });
    expect(notificar).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("Número de préstamos")).toHaveValue(0);
    expect(screen.getByRole("combobox", { name: "Estado" })).toHaveTextContent("Disponible");
  });

  it("valida el formato antes de enviar (préstamos negativos, campos vacíos)", async () => {
    await llenarFormulario("-2");

    expect(screen.getByText("No puede ser negativo.")).toBeInTheDocument();
    expect(crearEjemplar).not.toHaveBeenCalled();
  });

  it("avisa si el ISBN no es de ningún libro y valida el formato del código", async () => {
    vi.mocked(buscarLibroPorIsbn).mockResolvedValue(null);
    const { user } = renderizar(<AgregarEjemplar />);

    await user.type(screen.getByLabelText("ISBN del libro"), "978-0000000000");
    expect(await screen.findByText("No hay ningún libro con ese ISBN.")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Código del ejemplar"), "X-1");
    await user.click(screen.getByRole("button", { name: "Adicionar ejemplar" }));
    expect(screen.getByText("Usa el formato EJ-000 (EJ- y de 3 a 6 dígitos).")).toBeInTheDocument();
    expect(crearEjemplar).not.toHaveBeenCalled();
  });

  it("muestra el rechazo del servidor y conserva los datos", async () => {
    vi.mocked(crearEjemplar).mockRejectedValue(new ErrorGraphQL("No existe un libro con el ISBN 978-0307474728."));

    await llenarFormulario("2");

    expect(await screen.findByRole("alert")).toHaveTextContent("No existe un libro con el ISBN 978-0307474728.");
    expect(screen.getByLabelText("Código del ejemplar")).toHaveValue("EJ-100");
  });
});
