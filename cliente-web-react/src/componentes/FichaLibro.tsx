import { ReactNode } from "react";
import { formatearFecha, formatearPrecio } from "../formato";
import { Libro } from "../types";
import { InsigniaGenero } from "./ui/Insignias";

const SIN_DATO = "Sin registrar";

/**
 * Muestra TODA la información de un Libro, incluidos los atributos opcionales (editorial y
 * sinopsis), la llave primaria de MongoDB y la versión. Se usa al consultar, antes de eliminar
 * y antes de editar.
 */
export default function FichaLibro({ libro, titulo = "Libro" }: { libro: Libro; titulo?: string }) {
  return (
    <article className="panel" aria-label={`Libro ${libro.titulo}`}>
      <header className="ficha-cabecera">
        <div>
          <p className="ficha-tipo">{titulo}</p>
          <h2 className="ficha-titulo titulo-libro">{libro.titulo}</h2>
          <p className="ficha-subtitulo">{libro.autor}</p>
        </div>
      </header>
      <dl className="ficha-datos">
        <Dato termino="ISBN" valor={libro.isbn} />
        <Dato termino="Autor" valor={libro.autor} />
        <Dato termino="Género" valor={<InsigniaGenero genero={libro.genero} />} />
        <Dato termino="Precio de venta" valor={formatearPrecio(libro.precio)} />
        <Dato termino="Número de páginas" valor={libro.numeroPaginas} />
        <Dato termino="Fecha de publicación" valor={formatearFecha(libro.fechaPublicacion)} />
        <Dato termino="Editorial" valor={libro.editorial || <Opcional />} />
        {libro.cantidadEjemplares !== undefined && <Dato termino="Ejemplares" valor={libro.cantidadEjemplares} />}
        <Dato termino="Sinopsis" valor={libro.sinopsis || <Opcional />} ancho />
        {libro.id && <Dato termino="ID en MongoDB (_id)" valor={<code className="codigo-id">{libro.id}</code>} />}
        {libro.version !== undefined && libro.version !== null && <Dato termino="Versión" valor={libro.version} />}
      </dl>
    </article>
  );
}

export function Dato({ termino, valor, nota, ancho = false }: {
  termino: string; valor: ReactNode; nota?: ReactNode; ancho?: boolean;
}) {
  return (
    <div className={ancho ? "dato-ancho" : undefined}>
      <dt>{termino}</dt>
      <dd>{valor}</dd>
      {nota && <dd className="ficha-nota">{nota}</dd>}
    </div>
  );
}

/** Valor de un atributo opcional que no se registró. */
export function Opcional() {
  return <span className="dato-vacio">{SIN_DATO}</span>;
}
