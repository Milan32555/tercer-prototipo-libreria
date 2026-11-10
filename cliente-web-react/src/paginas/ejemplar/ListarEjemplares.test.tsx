import { act, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ListarEjemplares from "./ListarEjemplares";
import { consultarRevisionEjemplares, listarEjemplaresConLibro } from "../../graphql/servicioEjemplar";
import { ErrorGraphQL } from "../../graphql/clienteGraphQL";
import { ejemplarObservable } from "../../observer/Observable";
import { renderizar } from "../../pruebas/utilidades";
import { EjemplarConLibro, Pagina } from "../../types";

vi.mock("../../graphql/servicioEjemplar", () => ({
  listarEjemplaresConLibro: vi.fn(),
  consultarRevisionEjemplares: vi.fn()
}));

const EJ_004: EjemplarConLibro = {
  id: "6af377e075da53cd0c59cab3", codigo: "EJ-004", libroId: "6af377e075da53cd0c59ca9f",
  isbnLibro: "978-0307474728", tituloLibro: "Cien años de soledad", autorLibro: "Gabriel García Márquez",
  estado: "PRESTADO", ubicacion: "Estante A1", fechaIngreso: "2025-01-15T10:20:00", precioReposicion: 65000,
  numeroPrestamos: 6, observaciones: "Edición conmemorativa", version: 1
};

function pagina(contenido: EjemplarConLibro[], totalElementos = contenido.length, numero = 0, totalPaginas = 1): Pagina<EjemplarConLibro> {
  return { contenido, pagina: numero, tamanio: 10, totalElementos, totalPaginas };
}

describe("ListarEjemplares", () => {
  beforeEach(() => {
    vi.mocked(consultarRevisionEjemplares).mockReturnValue(new Promise(() => {}));
    vi.mocked(listarEjemplaresConLibro).mockResolvedValue(pagina([EJ_004]));
  });

  it("muestra TODOS los atributos del ejemplar más el título y el autor del libro", async () => {
    renderizar(<ListarEjemplares />);

    expect(await screen.findByText("EJ-004")).toBeInTheDocument();
    const tabla = screen.getByRole("table", { name: "Ejemplares del inventario" });
    const encabezados = within(tabla).getAllByRole("columnheader").map((th) => th.textContent);
    expect(encabezados).toEqual([
      "Código", "Título del libro", "Autor del libro", "ISBN del libro", "Estado", "Ubicación", "Fecha de ingreso",
      "Préstamos", "Reposición", "Observaciones", "Versión", "ID (_id)", "ID del libro (libroId)"
    ]);
    expect(within(tabla).getByText("Cien años de soledad")).toBeInTheDocument();
    expect(within(tabla).getByText("Gabriel García Márquez")).toBeInTheDocument();
    expect(within(tabla).getByText("Prestado")).toHaveClass("insignia-estado");
    expect(within(tabla).getByText("Edición conmemorativa")).toBeInTheDocument();
    expect(within(tabla).getByText("6af377e075da53cd0c59ca9f")).toBeInTheDocument();
    expect(within(tabla).getByRole("cell", { name: "6" })).toBeInTheDocument();
    expect(screen.getByText("1 ejemplar")).toBeInTheDocument();
    expect(listarEjemplaresConLibro).toHaveBeenCalledWith("", "", 0, 10);
  });

  it("filtra en el servidor por estado e ISBN, y limpia los filtros", async () => {
    const { user } = renderizar(<ListarEjemplares />);
    await screen.findByText("EJ-004");

    await user.type(screen.getByLabelText("ISBN del libro"), "978-0307474728");
    await user.click(screen.getByRole("radio", { name: "Prestado" }));
    await waitFor(() => expect(listarEjemplaresConLibro).toHaveBeenLastCalledWith("PRESTADO", "978-0307474728", 0, 10));

    await user.click(screen.getByRole("button", { name: "Limpiar filtros" }));
    await waitFor(() => expect(listarEjemplaresConLibro).toHaveBeenLastCalledWith("", "", 0, 10));
    expect(screen.getByRole("radio", { name: "Todos" })).toBeChecked();
  });

  it("pagina en el servidor", async () => {
    vi.mocked(listarEjemplaresConLibro).mockResolvedValue(pagina([EJ_004], 23, 0, 3));
    const { user } = renderizar(<ListarEjemplares />);
    await screen.findByText("Página 1 de 3");
    expect(screen.getByRole("button", { name: "Página anterior" })).toBeDisabled();

    vi.mocked(listarEjemplaresConLibro).mockResolvedValue(pagina([{ ...EJ_004, codigo: "EJ-021" }], 23, 2, 3));
    await user.click(screen.getByRole("button", { name: "Página siguiente" }));
    await user.click(screen.getByRole("button", { name: "Página siguiente" }));

    expect(await screen.findByText("EJ-021")).toBeInTheDocument();
    expect(listarEjemplaresConLibro).toHaveBeenLastCalledWith("", "", 2, 10);
  });

  it("Observer: se refresca sola cuando el sujeto notifica", async () => {
    renderizar(<ListarEjemplares />);
    await screen.findByText("EJ-004");
    vi.mocked(listarEjemplaresConLibro).mockResolvedValue(pagina([EJ_004, { ...EJ_004, codigo: "EJ-021", id: "x" }]));

    act(() => ejemplarObservable.notificar());

    expect(await screen.findByText("EJ-021")).toBeInTheDocument();
    expect(screen.getByText("2 ejemplares")).toBeInTheDocument();
  });

  it("muestra el error de carga y el estado vacío con filtro", async () => {
    vi.mocked(listarEjemplaresConLibro).mockRejectedValueOnce(new ErrorGraphQL("No se pudo conectar con el servidor."));
    const { user } = renderizar(<ListarEjemplares />);
    expect(await screen.findByText("No se pudo cargar la lista")).toBeInTheDocument();

    vi.mocked(listarEjemplaresConLibro).mockResolvedValue(pagina([], 0, 0, 0));
    await user.click(screen.getByRole("radio", { name: "Dañado" }));

    expect(await screen.findByText("Ningún ejemplar coincide con el filtro")).toBeInTheDocument();
  });
});
