import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ConsultarEjemplar from "./ConsultarEjemplar";
import { buscarEjemplarPorCodigo, buscarEjemplaresPorLibroYUbicacion } from "../../graphql/servicioEjemplar";
import { ErrorGraphQL } from "../../graphql/clienteGraphQL";
import { renderizar } from "../../pruebas/utilidades";
import { Ejemplar } from "../../types";

vi.mock("../../graphql/servicioEjemplar", () => ({
  buscarEjemplarPorCodigo: vi.fn(),
  buscarEjemplaresPorLibroYUbicacion: vi.fn()
}));

const EJ_007: Ejemplar = {
  codigo: "EJ-007", estado: "DANIADO", ubicacion: "Bodega", fechaIngreso: "2023-11-14T15:10:00",
  precioReposicion: 39000, numeroPrestamos: 11, isbnLibro: "978-9589012338",
  observaciones: "Lomo despegado", libro: { titulo: "María", autor: "Jorge Isaacs" }
};

async function buscar(codigo: string) {
  const { user } = renderizar(<ConsultarEjemplar />);
  await user.type(screen.getByLabelText("Código del ejemplar"), codigo);
  await user.click(screen.getByRole("button", { name: "Buscar" }));
}

describe("ConsultarEjemplar", () => {
  it("consulta UN SOLO ejemplar por código y muestra todos sus atributos", async () => {
    vi.mocked(buscarEjemplarPorCodigo).mockResolvedValue(EJ_007);

    await buscar("ej-007");

    const ficha = await screen.findByRole("article", { name: "Ejemplar EJ-007" });
    expect(within(ficha).getAllByText("Dañado")).toHaveLength(2); // cabecera + dato "Estado"
    expect(within(ficha).getByText("Bodega")).toBeInTheDocument();
    expect(within(ficha).getByText("11")).toBeInTheDocument();
    expect(within(ficha).getByText(/14\/11\/2023.*15:10/)).toBeInTheDocument();
    expect(within(ficha).getByText("Lomo despegado")).toBeInTheDocument();
    expect(within(ficha).getByText("Jorge Isaacs")).toBeInTheDocument();
    expect(buscarEjemplarPorCodigo).toHaveBeenCalledWith("ej-007");
  });

  it("segundo criterio: por ISBN del libro + ubicación (muestra todas las copias que haya)", async () => {
    vi.mocked(buscarEjemplaresPorLibroYUbicacion).mockResolvedValue([EJ_007, { ...EJ_007, codigo: "EJ-017" }]);
    const { user } = renderizar(<ConsultarEjemplar />);

    await user.click(screen.getByRole("radio", { name: "Por libro y ubicación" }));
    await user.click(screen.getByRole("button", { name: "Buscar" }));
    expect(screen.getAllByText("Este campo es obligatorio.")).toHaveLength(2);

    await user.type(screen.getByLabelText("ISBN del libro"), "978-9589012338");
    await user.type(screen.getByLabelText("Ubicación"), "bodega");
    await user.click(screen.getByRole("button", { name: "Buscar" }));

    expect(await screen.findByRole("article", { name: "Ejemplar EJ-017" })).toBeInTheDocument();
    expect(screen.getByRole("article", { name: "Ejemplar EJ-007" })).toBeInTheDocument();
    expect(screen.getByText("2 ejemplares de ese libro en esa ubicación")).toBeInTheDocument();
    expect(buscarEjemplaresPorLibroYUbicacion).toHaveBeenCalledWith("978-9589012338", "bodega");
  });

  it("segundo criterio sin resultados", async () => {
    vi.mocked(buscarEjemplaresPorLibroYUbicacion).mockResolvedValue([]);
    const { user } = renderizar(<ConsultarEjemplar />);

    await user.click(screen.getByRole("radio", { name: "Por libro y ubicación" }));
    await user.type(screen.getByLabelText("ISBN del libro"), "978-9589012338");
    await user.type(screen.getByLabelText("Ubicación"), "Estante Z9");
    await user.click(screen.getByRole("button", { name: "Buscar" }));

    expect(await screen.findByText("No hay ejemplares del libro «978-9589012338» en «Estante Z9».")).toBeInTheDocument();
  });

  it("informa cuando no existe o cuando falla la conexión", async () => {
    vi.mocked(buscarEjemplarPorCodigo).mockResolvedValueOnce(null);
    await buscar("EJ-999");
    expect(await screen.findByText("No hay ningún ejemplar con el código «EJ-999».")).toBeInTheDocument();
  });

  it("muestra el error del servidor", async () => {
    vi.mocked(buscarEjemplarPorCodigo).mockRejectedValue(new ErrorGraphQL("No se pudo conectar con el servidor."));
    await buscar("EJ-001");
    expect(await screen.findByRole("alert")).toHaveTextContent("No se pudo conectar con el servidor.");
  });
});
