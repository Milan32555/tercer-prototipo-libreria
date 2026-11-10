import { fireEvent, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ActualizarLibro from "./ActualizarLibro";
import { actualizarLibro, buscarLibroPorIsbn } from "../../graphql/servicioLibro";
import { ErrorGraphQL } from "../../graphql/clienteGraphQL";
import { libroObservable } from "../../observer/Observable";
import { elegirOpcion, renderizar } from "../../pruebas/utilidades";
import { Libro } from "../../types";

vi.mock("../../graphql/servicioLibro", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../graphql/servicioLibro")>()),
  buscarLibroPorIsbn: vi.fn(),
  actualizarLibro: vi.fn(),
  consultarRevisionLibros: vi.fn()
}));

const MARIA: Libro = {
  isbn: "978-9589012338", titulo: "María", autor: "Jorge Isaacs",
  precio: 39000, numeroPaginas: 340, fechaPublicacion: "1867-01-01T08:15:00", genero: "FICCION",
  id: "6af377e075da53cd0c59caa1", editorial: null, sinopsis: "Efraín regresa al Valle del Cauca.", version: 3
};

async function buscar() {
  const resultado = renderizar(<ActualizarLibro />);
  await resultado.user.type(screen.getByLabelText("ISBN del libro"), "978-9589012338");
  await resultado.user.click(screen.getByRole("button", { name: "Buscar" }));
  await screen.findByText("Datos actuales");
  return resultado.user;
}

describe("ActualizarLibro", () => {
  it("busca primero, muestra TODA la información y los campos con los datos actuales", async () => {
    vi.mocked(buscarLibroPorIsbn).mockResolvedValue(MARIA);

    await buscar();

    const ficha = screen.getByRole("article", { name: "Libro María" });
    expect(within(ficha).getByText("978-9589012338")).toBeInTheDocument();
    expect(within(ficha).getByText(/01\/01\/1867.*08:15/)).toBeInTheDocument();
    expect(screen.getByLabelText("Título")).toHaveValue("María");
    expect(screen.getByLabelText("Autor")).toHaveValue("Jorge Isaacs");
    expect(screen.getByLabelText("Precio")).toHaveValue(39000);
    expect(screen.getByLabelText("Número de páginas")).toHaveValue(340);
    expect(screen.getByLabelText("Fecha de publicación")).toHaveValue("1867-01-01T08:15");
    expect(screen.getByRole("combobox", { name: "Género" })).toHaveTextContent("Ficción");
    // También los opcionales, el _id y la versión.
    expect(within(ficha).getByText("Sin registrar")).toBeInTheDocument();
    expect(within(ficha).getByText("Efraín regresa al Valle del Cauca.")).toBeInTheDocument();
    expect(within(ficha).getByText("6af377e075da53cd0c59caa1")).toBeInTheDocument();
    expect(screen.getByLabelText("Sinopsis (opcional)")).toHaveValue("Efraín regresa al Valle del Cauca.");
  });

  it("guarda los cambios, muestra lo que respondió el servidor y notifica al Observer", async () => {
    vi.mocked(buscarLibroPorIsbn).mockResolvedValue(MARIA);
    vi.mocked(actualizarLibro).mockImplementation(async (datos) => ({ ...datos, version: 4 }));
    const notificar = vi.spyOn(libroObservable, "notificar");
    const user = await buscar();

    await user.clear(screen.getByLabelText("Precio"));
    await user.type(screen.getByLabelText("Precio"), "41000");
    fireEvent.change(screen.getByLabelText("Fecha de publicación"), { target: { value: "1867-06-01T09:00" } });
    await elegirOpcion(user, "Género", "Poesía");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByText("Cambios guardados")).toBeInTheDocument();
    expect(actualizarLibro).toHaveBeenCalledWith({
      isbn: "978-9589012338", titulo: "María", autor: "Jorge Isaacs", precio: 41000, numeroPaginas: 340,
      fechaPublicacion: "1867-06-01T09:00", genero: "POESIA",
      editorial: null, sinopsis: "Efraín regresa al Valle del Cauca.", version: 3
    });
    expect(notificar).toHaveBeenCalledTimes(1);
    const guardado = screen.getByRole("article", { name: "Libro María" });
    expect(within(guardado).getByText("Datos guardados")).toBeInTheDocument();
    expect(within(guardado).getByText(/41\.000/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Actualizar otro libro" }));
    expect(screen.getByLabelText("ISBN del libro")).toBeInTheDocument();
  });

  it("valida en línea antes de enviar y muestra el rechazo del servidor", async () => {
    vi.mocked(buscarLibroPorIsbn).mockResolvedValue(MARIA);
    vi.mocked(actualizarLibro).mockRejectedValue(new ErrorGraphQL("La fecha de publicación no puede ser futura."));
    const user = await buscar();

    await user.clear(screen.getByLabelText("Título"));
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
    expect(screen.getByText("Este campo es obligatorio.")).toBeInTheDocument();
    expect(actualizarLibro).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText("Título"), "María");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("La fecha de publicación no puede ser futura.");
    expect(screen.getByLabelText("Título")).toHaveValue("María");
  });

  it("muestra tal cual el conflicto de versión cuando otro usuario cambió el libro", async () => {
    vi.mocked(buscarLibroPorIsbn).mockResolvedValue(MARIA);
    vi.mocked(actualizarLibro).mockRejectedValue(new ErrorGraphQL("Otro usuario modificó este libro; vuelva a buscarlo."));
    const user = await buscar();

    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Otro usuario modificó este libro; vuelva a buscarlo.");
    expect(actualizarLibro).toHaveBeenCalledWith(expect.objectContaining({ version: 3 }));
  });

  it("informa si no existe y permite cancelar la edición", async () => {
    vi.mocked(buscarLibroPorIsbn).mockResolvedValueOnce(null);
    const { user } = renderizar(<ActualizarLibro />);

    await user.type(screen.getByLabelText("ISBN del libro"), "000");
    await user.click(screen.getByRole("button", { name: "Buscar" }));
    expect(await screen.findByText("No hay ningún libro con el ISBN «000».")).toBeInTheDocument();

    vi.mocked(buscarLibroPorIsbn).mockResolvedValueOnce(MARIA);
    await user.click(screen.getByRole("button", { name: "Buscar" }));
    await screen.findByText("Datos actuales");
    await user.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(screen.queryByText("Datos actuales")).not.toBeInTheDocument();
    expect(actualizarLibro).not.toHaveBeenCalled();
  });
});
