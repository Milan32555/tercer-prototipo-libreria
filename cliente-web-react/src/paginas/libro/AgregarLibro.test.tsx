import { fireEvent, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AgregarLibro from "./AgregarLibro";
import { crearLibro } from "../../graphql/servicioLibro";
import { ErrorGraphQL } from "../../graphql/clienteGraphQL";
import { libroObservable } from "../../observer/Observable";
import { elegirOpcion, renderizar } from "../../pruebas/utilidades";

vi.mock("../../graphql/servicioLibro", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../graphql/servicioLibro")>()),
  crearLibro: vi.fn(),
  consultarRevisionLibros: vi.fn()
}));

async function llenarYEnviar() {
  const { user } = renderizar(<AgregarLibro />);
  await user.type(screen.getByLabelText("ISBN"), "1008");
  await user.type(screen.getByLabelText("Título"), "Pedro Páramo");
  await user.type(screen.getByLabelText("Autor"), "Juan Rulfo");
  await user.type(screen.getByLabelText("Precio"), "42000.5");
  await user.type(screen.getByLabelText("Número de páginas"), "129");
  fireEvent.change(screen.getByLabelText("Fecha de publicación"), { target: { value: "1955-03-19T10:30" } });
  await elegirOpcion(user, "Género", "No ficción");
  await user.type(screen.getByLabelText("Editorial (opcional)"), "  Fondo de Cultura Económica ");
  await user.click(screen.getByRole("button", { name: "Adicionar libro" }));
  return user;
}

describe("AgregarLibro", () => {
  it("envía todos los atributos con su tipo, avisa el resultado y notifica al Observer", async () => {
    vi.mocked(crearLibro).mockImplementation(async (libro) => ({ ...libro, id: "6af377e075da53cd0c59ca9f", version: 0 }));
    const notificar = vi.spyOn(libroObservable, "notificar");

    await llenarYEnviar();

    expect(await screen.findByText("Libro adicionado")).toBeInTheDocument();
    expect(screen.getByText("«Pedro Páramo» quedó registrado con el ISBN 1008.")).toBeInTheDocument();
    expect(crearLibro).toHaveBeenCalledWith({
      isbn: "1008", titulo: "Pedro Páramo", autor: "Juan Rulfo", precio: 42000.5,
      numeroPaginas: 129, fechaPublicacion: "1955-03-19T10:30", genero: "NO_FICCION",
      editorial: "Fondo de Cultura Económica", sinopsis: null
    });
    expect(notificar).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("ISBN")).toHaveValue("");
  });

  it("valida en línea y no envía un formulario incompleto", async () => {
    const { user } = renderizar(<AgregarLibro />);
    await user.type(screen.getByLabelText("Precio"), "0");

    await user.click(screen.getByRole("button", { name: "Adicionar libro" }));

    expect(screen.getAllByText("Este campo es obligatorio.")).toHaveLength(4); // ISBN, título, autor y páginas
    expect(screen.getByText("Debe ser mayor que cero.")).toBeInTheDocument();
    expect(screen.getByText("Elige la fecha y la hora.")).toBeInTheDocument();
    expect(screen.getByLabelText("ISBN")).toHaveAttribute("aria-invalid", "true");
    expect(crearLibro).not.toHaveBeenCalled();

    // Al corregir un campo, su error desaparece.
    await user.type(screen.getByLabelText("ISBN"), "1");
    expect(screen.getByLabelText("ISBN")).not.toHaveAttribute("aria-invalid");
  });

  it("valida el formato del ISBN, la fecha no futura y el largo de la sinopsis", async () => {
    const { user } = renderizar(<AgregarLibro />);
    await user.type(screen.getByLabelText("ISBN"), "12345");
    fireEvent.change(screen.getByLabelText("Fecha de publicación"), { target: { value: "2999-01-01T00:00" } });
    fireEvent.change(screen.getByLabelText("Sinopsis (opcional)"), { target: { value: "x".repeat(501) } });

    await user.click(screen.getByRole("button", { name: "Adicionar libro" }));

    expect(screen.getByText("El ISBN debe tener de 1 a 4 dígitos (solo números).")).toBeInTheDocument();
    expect(screen.getByText("La fecha no puede ser futura.")).toBeInTheDocument();
    expect(screen.getByText("Máximo 500 caracteres.")).toBeInTheDocument();
    expect(screen.getByText("501 / 500")).toHaveClass("excedido");
    expect(crearLibro).not.toHaveBeenCalled();
  });

  it("muestra el rechazo del servidor y conserva los datos escritos", async () => {
    vi.mocked(crearLibro).mockRejectedValue(new ErrorGraphQL("Ya existe un libro con el ISBN 1008."));

    await llenarYEnviar();

    expect(await screen.findByRole("alert")).toHaveTextContent("Ya existe un libro con el ISBN 1008.");
    expect(screen.getByLabelText("ISBN")).toHaveValue("1008");
  });
});
