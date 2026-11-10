export type Genero =
  | "FICCION"
  | "NO_FICCION"
  | "CIENCIA"
  | "HISTORIA"
  | "INFANTIL"
  | "POESIA"
  | "OTRO";

export const GENEROS: Genero[] = [
  "FICCION",
  "NO_FICCION",
  "CIENCIA",
  "HISTORIA",
  "INFANTIL",
  "POESIA",
  "OTRO"
];

const ETIQUETAS_GENERO: Record<Genero, string> = {
  FICCION: "Ficción",
  NO_FICCION: "No ficción",
  CIENCIA: "Ciencia",
  HISTORIA: "Historia",
  INFANTIL: "Infantil",
  POESIA: "Poesía",
  OTRO: "Otro"
};

/** Nombre legible del género (al servidor se envía el valor del enum). */
export function etiquetaGenero(genero: Genero): string {
  return ETIQUETAS_GENERO[genero];
}

export type EstadoEjemplar = "DISPONIBLE" | "PRESTADO" | "DANIADO" | "DADO_DE_BAJA";

export const ESTADOS_EJEMPLAR: EstadoEjemplar[] = [
  "DISPONIBLE",
  "PRESTADO",
  "DANIADO",
  "DADO_DE_BAJA"
];

const ETIQUETAS_ESTADO: Record<EstadoEjemplar, string> = {
  DISPONIBLE: "Disponible",
  PRESTADO: "Prestado",
  DANIADO: "Dañado",
  DADO_DE_BAJA: "Dado de baja"
};

/** Nombre legible del estado (al servidor se envía el valor del enum). */
export function etiquetaEstado(estado: EstadoEjemplar): string {
  return ETIQUETAS_ESTADO[estado];
}

export interface Libro {
  /** Llave primaria: _id (ObjectId) del documento en MongoDB. */
  id?: string;
  isbn: string;
  titulo: string;
  autor: string;
  precio: number;
  numeroPaginas: number;
  fechaPublicacion: string; // ISO-8601, p.ej. 2024-05-10T14:30:00
  genero: Genero;
  /** Opcional. */
  editorial?: string | null;
  /** Opcional (máximo 500 caracteres). */
  sinopsis?: string | null;
  /** Versión del documento (concurrencia optimista): se devuelve al actualizar. */
  version?: number | null;
  /** Cuántos ejemplares tiene (lo cuenta el servidor). */
  cantidadEjemplares?: number;
}

/** Datos que se envían para crear o actualizar un libro (input LibroInput del servidor). */
export interface LibroInput {
  isbn: string;
  titulo: string;
  autor: string;
  precio: number;
  numeroPaginas: number;
  fechaPublicacion: string;
  genero: Genero;
  editorial: string | null;
  sinopsis: string | null;
  version?: number | null;
}

export interface Ejemplar {
  /** Llave primaria: _id (ObjectId) del documento en MongoDB. */
  id?: string;
  codigo: string;
  /** Llave foránea: _id del libro en la colección libros. */
  libroId?: string;
  isbnLibro: string;
  estado: EstadoEjemplar;
  ubicacion: string;
  fechaIngreso: string;
  precioReposicion: number;
  numeroPrestamos: number;
  /** Opcional. */
  observaciones?: string | null;
  version?: number | null;
  /** Título y autor del libro al que pertenece. */
  libro?: { titulo: string; autor: string } | null;
}

/** Datos que se envían para crear o actualizar un ejemplar (input EjemplarInput del servidor). */
export interface EjemplarInput {
  codigo: string;
  isbnLibro: string;
  estado: EstadoEjemplar;
  ubicacion: string;
  fechaIngreso: string;
  precioReposicion: number;
  numeroPrestamos?: number | null;
  observaciones: string | null;
  version?: number | null;
}

/** Fila de Listar Ejemplares: todos los atributos del ejemplar + isbn, título y autor del libro. */
export interface EjemplarConLibro {
  id: string;
  codigo: string;
  libroId: string;
  isbnLibro: string | null;
  tituloLibro: string | null;
  autorLibro: string | null;
  estado: EstadoEjemplar;
  ubicacion: string;
  fechaIngreso: string;
  precioReposicion: number;
  numeroPrestamos: number;
  observaciones: string | null;
  version: number | null;
}

/** Una página de resultados de un Listar (la paginación la hace el servidor en MongoDB). */
export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}

/** Cuántos ejemplares hay en un estado y su porcentaje (0 a 100); lo calcula el servidor. */
export interface ConteoEstado {
  estado: EstadoEjemplar;
  cantidad: number;
  porcentaje: number;
}

/** Maestro + detalle: un libro con sus ejemplares y los conteos por estado. */
export interface DetalleLibro {
  libro: Libro;
  ejemplares: Ejemplar[];
  conteoPorEstado: ConteoEstado[];
  totalEjemplares: number;
  totalPrestamos: number;
  valorReposicion: number;
}

/** Cómo terminó una de las 3 tareas concurrentes del resumen en el servidor. */
export interface ResultadoTarea {
  nombre: string;
  hilo: string;
  duracionMs: number;
  exitosa: boolean;
}

/** Resumen del inventario para la ventana principal (query resumenInventario). */
export interface ResumenInventario {
  totalLibros: number;
  totalEjemplares: number;
  ejemplaresPorEstado: ConteoEstado[];
  valorInventario: number;
  duracionMs: number;
  tareas: ResultadoTarea[];
}
