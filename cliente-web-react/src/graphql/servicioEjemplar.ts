import { ClienteGraphQL } from "./clienteGraphQL";
import { EstadoEjemplar, Ejemplar, EjemplarConLibro, EjemplarInput, Pagina } from "../types";

/** Todos los atributos del ejemplar, más el título y el autor de su libro. */
export const CAMPOS_EJEMPLAR = `id codigo libroId isbnLibro estado ubicacion fechaIngreso precioReposicion numeroPrestamos
  observaciones version libro { titulo autor }`;

const graphql = () => ClienteGraphQL.getInstancia();

/**
 * Listar ejemplares (consulta personalizada `ejemplaresConLibro`): TODOS los atributos de cada
 * ejemplar más el ISBN, título y autor de su libro, con filtro por estado y/o ISBN y paginado.
 */
export async function listarEjemplaresConLibro(
  estado: EstadoEjemplar | "",
  isbn: string,
  pagina: number,
  tamanio: number
): Promise<Pagina<EjemplarConLibro>> {
  const data = await graphql().ejecutar<{ ejemplaresConLibro: Pagina<EjemplarConLibro> }>(
    `query ($estado: EstadoEjemplar, $isbn: String, $pagina: Int!, $tamanio: Int!) {
       ejemplaresConLibro(estado: $estado, isbn: $isbn, pagina: $pagina, tamanio: $tamanio) {
         pagina tamanio totalElementos totalPaginas
         contenido {
           id codigo libroId isbnLibro tituloLibro autorLibro estado ubicacion fechaIngreso
           precioReposicion numeroPrestamos observaciones version
         }
       }
     }`,
    { estado: estado || null, isbn: isbn.trim() || null, pagina, tamanio }
  );
  return data.ejemplaresConLibro;
}

/** Búsqueda individual, criterio 1: el código del ejemplar. */
export async function buscarEjemplarPorCodigo(codigo: string): Promise<Ejemplar | null> {
  const data = await graphql().ejecutar<{ ejemplarPorCodigo: Ejemplar | null }>(
    `query ($codigo: ID!) { ejemplarPorCodigo(codigo: $codigo) { ${CAMPOS_EJEMPLAR} } }`,
    { codigo: codigo.trim() }
  );
  return data.ejemplarPorCodigo;
}

/** Búsqueda individual, criterio 2: ISBN del libro + ubicación (puede haber varias copias). */
export async function buscarEjemplaresPorLibroYUbicacion(isbn: string, ubicacion: string): Promise<Ejemplar[]> {
  const data = await graphql().ejecutar<{ ejemplaresPorLibroYUbicacion: Ejemplar[] }>(
    `query ($isbn: String!, $ubicacion: String!) {
       ejemplaresPorLibroYUbicacion(isbn: $isbn, ubicacion: $ubicacion) { ${CAMPOS_EJEMPLAR} }
     }`,
    { isbn: isbn.trim(), ubicacion: ubicacion.trim() }
  );
  return data.ejemplaresPorLibroYUbicacion;
}

/** Número de revisión de la colección en el servidor (lo usa el Observer de Listar). */
export async function consultarRevisionEjemplares(): Promise<number> {
  const data = await graphql().ejecutar<{ revisionEjemplares: number }>(`query { revisionEjemplares }`);
  return data.revisionEjemplares;
}

export async function crearEjemplar(input: EjemplarInput): Promise<Ejemplar> {
  const data = await graphql().ejecutar<{ crearEjemplar: Ejemplar }>(
    `mutation ($input: EjemplarInput!) { crearEjemplar(input: $input) { ${CAMPOS_EJEMPLAR} } }`,
    { input: { ...input, version: null } }
  );
  return data.crearEjemplar;
}

/**
 * Actualiza el ejemplar cuyo código viene en el input. numeroPrestamos no se envía: lo lleva el
 * servidor (suma 1 al pasar a Prestado). Se envía la versión leída al buscarlo.
 */
export async function actualizarEjemplar(input: EjemplarInput): Promise<Ejemplar> {
  const data = await graphql().ejecutar<{ actualizarEjemplar: Ejemplar }>(
    `mutation ($input: EjemplarInput!) { actualizarEjemplar(input: $input) { ${CAMPOS_EJEMPLAR} } }`,
    { input: { ...input, numeroPrestamos: null } }
  );
  return data.actualizarEjemplar;
}

export async function eliminarEjemplar(codigo: string): Promise<Ejemplar> {
  const data = await graphql().ejecutar<{ eliminarEjemplar: Ejemplar }>(
    `mutation ($codigo: ID!) { eliminarEjemplar(codigo: $codigo) { ${CAMPOS_EJEMPLAR} } }`,
    { codigo }
  );
  return data.eliminarEjemplar;
}
