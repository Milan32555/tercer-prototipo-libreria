import { ClienteGraphQL } from "./clienteGraphQL";
import { DetalleLibro, Genero, Libro, LibroInput, Pagina } from "../types";

/** Todos los atributos del libro (incluidos los opcionales, el _id y la versión). */
export const CAMPOS_LIBRO = `id isbn titulo autor precio numeroPaginas fechaPublicacion genero editorial sinopsis version`;

const CAMPOS_PAGINA = `pagina tamanio totalElementos totalPaginas`;

const graphql = () => ClienteGraphQL.getInstancia();

export interface FiltroLibros {
  genero: Genero | "";
  precioMin: string;
  precioMax: string;
}

export const SIN_FILTRO_LIBROS: FiltroLibros = { genero: "", precioMin: "", precioMax: "" };

export function hayFiltroLibros(filtro: FiltroLibros): boolean {
  return filtro.genero !== "" || filtro.precioMin.trim() !== "" || filtro.precioMax.trim() !== "";
}

/**
 * Listar libros, paginado. Sin filtro usa la query `libros` (todos); con filtro usa la consulta
 * personalizada `librosPorPrecioYGenero`, que filtra en MongoDB.
 */
export async function listarLibros(filtro: FiltroLibros, pagina: number, tamanio: number): Promise<Pagina<Libro>> {
  const campos = `${CAMPOS_PAGINA} contenido { ${CAMPOS_LIBRO} cantidadEjemplares }`;
  if (!hayFiltroLibros(filtro)) {
    const data = await graphql().ejecutar<{ libros: Pagina<Libro> }>(
      `query ($pagina: Int!, $tamanio: Int!) { libros(pagina: $pagina, tamanio: $tamanio) { ${campos} } }`,
      { pagina, tamanio }
    );
    return data.libros;
  }
  const data = await graphql().ejecutar<{ librosPorPrecioYGenero: Pagina<Libro> }>(
    `query ($min: Float, $max: Float, $genero: Genero, $pagina: Int!, $tamanio: Int!) {
       librosPorPrecioYGenero(precioMin: $min, precioMax: $max, genero: $genero, pagina: $pagina, tamanio: $tamanio) {
         ${campos}
       }
     }`,
    {
      min: numeroONulo(filtro.precioMin),
      max: numeroONulo(filtro.precioMax),
      genero: filtro.genero || null,
      pagina,
      tamanio
    }
  );
  return data.librosPorPrecioYGenero;
}

export async function buscarLibroPorIsbn(isbn: string): Promise<Libro | null> {
  const data = await graphql().ejecutar<{ libroPorIsbn: Libro | null }>(
    `query ($isbn: ID!) { libroPorIsbn(isbn: $isbn) { ${CAMPOS_LIBRO} cantidadEjemplares } }`,
    { isbn: isbn.trim() }
  );
  return data.libroPorIsbn;
}

export async function buscarLibroPorTitulo(titulo: string): Promise<Libro | null> {
  const data = await graphql().ejecutar<{ libroPorTitulo: Libro | null }>(
    `query ($titulo: String!) { libroPorTitulo(titulo: $titulo) { ${CAMPOS_LIBRO} cantidadEjemplares } }`,
    { titulo: titulo.trim() }
  );
  return data.libroPorTitulo;
}

/** Maestro + detalle: el libro, sus ejemplares y cuántos hay en cada estado. */
export async function consultarDetalleLibro(isbn: string): Promise<DetalleLibro | null> {
  const data = await graphql().ejecutar<{ detalleLibro: DetalleLibro | null }>(
    `query ($isbn: ID!) {
       detalleLibro(isbn: $isbn) {
         libro { ${CAMPOS_LIBRO} }
         ejemplares { id codigo libroId isbnLibro estado ubicacion fechaIngreso precioReposicion numeroPrestamos observaciones version }
         conteoPorEstado { estado cantidad porcentaje }
         totalEjemplares totalPrestamos valorReposicion
       }
     }`,
    { isbn: isbn.trim() }
  );
  return data.detalleLibro;
}

/** Número de revisión de la colección en el servidor (lo usa el Observer de Listar). */
export async function consultarRevisionLibros(): Promise<number> {
  const data = await graphql().ejecutar<{ revisionLibros: number }>(`query { revisionLibros }`);
  return data.revisionLibros;
}

export async function crearLibro(input: LibroInput): Promise<Libro> {
  const data = await graphql().ejecutar<{ crearLibro: Libro }>(
    `mutation ($input: LibroInput!) { crearLibro(input: $input) { ${CAMPOS_LIBRO} } }`,
    { input: { ...input, version: null } }
  );
  return data.crearLibro;
}

/**
 * Actualiza el libro cuyo isbn viene en el input (el isbn no se cambia). Se envía la versión
 * leída al buscarlo: si otro usuario lo cambió entretanto, el servidor rechaza la actualización.
 */
export async function actualizarLibro(input: LibroInput): Promise<Libro> {
  const data = await graphql().ejecutar<{ actualizarLibro: Libro }>(
    `mutation ($input: LibroInput!) { actualizarLibro(input: $input) { ${CAMPOS_LIBRO} } }`,
    { input }
  );
  return data.actualizarLibro;
}

export async function eliminarLibro(isbn: string): Promise<Libro> {
  const data = await graphql().ejecutar<{ eliminarLibro: Libro }>(
    `mutation ($isbn: ID!) { eliminarLibro(isbn: $isbn) { ${CAMPOS_LIBRO} } }`,
    { isbn }
  );
  return data.eliminarLibro;
}

/** Texto opcional del formulario: null si quedó vacío. */
export function textoONulo(valor: string): string | null {
  const limpio = valor.trim();
  return limpio === "" ? null : limpio;
}

function numeroONulo(valor: string): number | null {
  return valor.trim() === "" ? null : Number(valor);
}
