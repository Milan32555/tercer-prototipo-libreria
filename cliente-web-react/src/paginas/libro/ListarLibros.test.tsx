import { act, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ListarLibros from "./ListarLibros";
import { consultarRevisionLibros, listarLibros } from "../../graphql/servicioLibro";
import { ErrorGraphQL } from "../../graphql/clienteGraphQL";
import { libroObservable } from "../../observer/Observable";
import { elegirOpcion, renderizar } from "../../pruebas/utilidades";
import { Libro, Pagina } from "../../types";

vi.mock("../../graphql/servicioLibro", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../graphql/servicioLibro")>()),
  listarLibros: vi.fn(),
  consultarRevisionLibros: vi.fn()
}));

const CIEN_ANOS: Libro = {
  isbn: "978-0307474728", titulo: "Cien años de soledad", autor: "Gabriel García Márquez",
  precio: 65000, numeroPaginas: 496, fechaPublicacion: "1967-05-30T00:00:00", genero: "FICCION",
  editorial: "Sudamericana", cantidadEjemplares: 3
};
const PEDRO_PARAMO: Libro = {
  isbn: "978-9681649500", titulo: "Pedro Páramo", autor: "Juan Rulfo",
  precio: 42000, numeroPaginas: 129, fechaPublicacion: "1955-03-19T00:00:00", genero: "FICCION",
  editorial: null, cantidadEjemplares: 2
};

const SIN_FILTRO = { genero: "", precioMin: "", precioMax: "" };

function pagina(contenido: Libro[], totalElementos = contenido.length, numero = 0, totalPaginas = 1): Pagina<Libro> {
  return { contenido, pagina: numero, tamanio: 10, totalElementos, totalPaginas };
}

describe("ListarLibros", () => {
  beforeEach(() => {
    vi.mocked(consultarRevisionLibros).mockReturnValue(new Promise(() => {}));
    vi.mocked(listarLibros).mockResolvedValue(pagina([CIEN_ANOS]));
  });

  it("muestra todos los libros en una grilla paginada al abrir la pantalla", async () => {
    renderizar(<ListarLibros />);

    expect(await screen.findByText("Cien años de soledad")).toBeInTheDocument();
    const tabla = screen.getByRole("table", { name: "Libros del inventario" });
    expect(within(tabla).getByRole("columnheader", { name: "Páginas" })).toBeInTheDocument();
    expect(within(tabla).getByRole("cell", { name: "496" })).toHaveClass("numero");
    expect(within(tabla).getByRole("cell", { name: "Ficción" })).toBeInTheDocument();
    expect(within(tabla).getByRole("cell", { name: "Sudamericana" })).toBeInTheDocument();
    expect(within(tabla).getByRole("cell", { name: "3" })).toBeInTheDocument();
    expect(screen.getByText("1 libro")).toBeInTheDocument();
    expect(screen.getByText("Página 1 de 1")).toBeInTheDocument();
    expect(listarLibros).toHaveBeenCalledWith(SIN_FILTRO, 0, 10);
  });

  it("filtra en el servidor por género y rango de precio", async () => {
    const { user } = renderizar(<ListarLibros />);
    await screen.findByText("Cien años de soledad");

    await user.type(screen.getByLabelText("Precio mínimo"), "40000");
    await user.type(screen.getByLabelText("Precio máximo"), "60000");
    await user.click(screen.getByRole("button", { name: "Filtrar" }));
    await waitFor(() => expect(listarLibros).toHaveBeenLastCalledWith(
      { genero: "", precioMin: "40000", precioMax: "60000" }, 0, 10));

    // Elegir un género filtra de inmediato, sin perder el rango escrito.
    await elegirOpcion(user, "Género", "Poesía");
    await waitFor(() => expect(listarLibros).toHaveBeenLastCalledWith(
      { genero: "POESIA", precioMin: "40000", precioMax: "60000" }, 0, 10));
    expect(screen.getByText(/Consulta: libros por precio y género/)).toBeInTheDocument();
  });

  it("no envía un rango de precios al revés", async () => {
    const { user } = renderizar(<ListarLibros />);
    await screen.findByText("Cien años de soledad");

    await user.type(screen.getByLabelText("Precio mínimo"), "90000");
    await user.type(screen.getByLabelText("Precio máximo"), "10000");
    await user.click(screen.getByRole("button", { name: "Filtrar" }));

    expect(screen.getByText("El precio mínimo no puede ser mayor que el máximo.")).toBeInTheDocument();
    expect(listarLibros).toHaveBeenCalledTimes(1);
  });

  it("pasa de página y cambia el tamaño de página en el servidor", async () => {
    vi.mocked(listarLibros).mockResolvedValue(pagina([CIEN_ANOS], 25, 0, 3));
    const { user } = renderizar(<ListarLibros />);
    await screen.findByText("Página 1 de 3");

    vi.mocked(listarLibros).mockResolvedValue(pagina([PEDRO_PARAMO], 25, 1, 3));
    await user.click(screen.getByRole("button", { name: "Página siguiente" }));
    expect(await screen.findByText("Pedro Páramo")).toBeInTheDocument();
    expect(listarLibros).toHaveBeenLastCalledWith(SIN_FILTRO, 1, 10);
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument();
    expect(screen.getByText("25 libros")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Filas por página"), "20");
    await waitFor(() => expect(listarLibros).toHaveBeenLastCalledWith(SIN_FILTRO, 0, 20));
  });

  it("limpiar filtros vuelve a pedir la lista completa", async () => {
    const { user } = renderizar(<ListarLibros />);
    await screen.findByText("Cien años de soledad");
    expect(screen.queryByRole("button", { name: "Limpiar filtros" })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Precio mínimo"), "1");
    await user.click(screen.getByRole("button", { name: "Limpiar filtros" }));

    await waitFor(() => expect(listarLibros).toHaveBeenLastCalledWith(SIN_FILTRO, 0, 10));
    expect(screen.getByLabelText("Precio mínimo")).toHaveValue(null);
  });

  it("Observer: se refresca sola, con los filtros actuales, cuando el sujeto notifica", async () => {
    const { user } = renderizar(<ListarLibros />);
    await screen.findByText("Cien años de soledad");
    await user.type(screen.getByLabelText("Precio máximo"), "50000");
    vi.mocked(listarLibros).mockResolvedValue(pagina([PEDRO_PARAMO]));

    act(() => libroObservable.notificar());

    expect(await screen.findByText("Pedro Páramo")).toBeInTheDocument();
    expect(listarLibros).toHaveBeenLastCalledWith({ genero: "", precioMin: "", precioMax: "50000" }, 0, 10);
    expect(screen.queryByText("Cien años de soledad")).not.toBeInTheDocument();
  });

  it("si falla la carga muestra el error y permite reintentar", async () => {
    vi.mocked(listarLibros).mockRejectedValueOnce(new ErrorGraphQL("No se pudo conectar con el servidor."));
    const { user } = renderizar(<ListarLibros />);

    expect(await screen.findByText("No se pudo cargar la lista")).toBeInTheDocument();
    expect(screen.getByText("No se pudo conectar con el servidor.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(await screen.findByText("Cien años de soledad")).toBeInTheDocument();
  });

  it("estado vacío: sin libros invita a adicionar; con filtro invita a limpiarlo", async () => {
    vi.mocked(listarLibros).mockResolvedValue(pagina([], 0, 0, 0));
    const { user } = renderizar(<ListarLibros />);

    expect(await screen.findByText("Todavía no hay libros")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Precio mínimo"), "999999");
    await user.click(screen.getByRole("button", { name: "Filtrar" }));

    expect(await screen.findByText("Ningún libro coincide con el filtro")).toBeInTheDocument();
  });
});
