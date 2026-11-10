import { Lock } from "lucide-react";
import { formatearFecha, formatearPrecio } from "../formato";
import { Ejemplar } from "../types";
import { Dato, Opcional } from "./FichaLibro";
import { InsigniaEstado } from "./ui/Insignias";

/**
 * Muestra TODA la información de un Ejemplar (incluidas las observaciones opcionales, el _id,
 * la llave foránea libroId y la versión) y el título y autor de su libro. Se usa al consultar,
 * antes de eliminar y antes de editar.
 */
export default function FichaEjemplar({ ejemplar, titulo = "Ejemplar" }: { ejemplar: Ejemplar; titulo?: string }) {
  return (
    <article className="panel" aria-label={`Ejemplar ${ejemplar.codigo}`}>
      <header className="ficha-cabecera">
        <div>
          <p className="ficha-tipo">{titulo}</p>
          <h2 className="ficha-titulo">{ejemplar.codigo}</h2>
          <p className="ficha-subtitulo">
            {ejemplar.libro ? <><span className="titulo-libro">{ejemplar.libro.titulo}</span> · {ejemplar.libro.autor}</> : `Libro ${ejemplar.isbnLibro}`}
          </p>
        </div>
        <InsigniaEstado estado={ejemplar.estado} />
      </header>
      <dl className="ficha-datos">
        <Dato termino="Código" valor={ejemplar.codigo} />
        <Dato termino="Estado" valor={<InsigniaEstado estado={ejemplar.estado} />} />
        <Dato termino="Libro (ISBN)" valor={ejemplar.isbnLibro} />
        {ejemplar.libro && <Dato termino="Título del libro" valor={ejemplar.libro.titulo} />}
        {ejemplar.libro && <Dato termino="Autor del libro" valor={ejemplar.libro.autor} />}
        <Dato termino="Ubicación" valor={ejemplar.ubicacion} />
        <Dato termino="Fecha de ingreso" valor={formatearFecha(ejemplar.fechaIngreso)} />
        <Dato termino="Precio de reposición" valor={formatearPrecio(ejemplar.precioReposicion)} />
        <Dato
          termino="Número de préstamos"
          valor={ejemplar.numeroPrestamos}
          nota={<><Lock size={12} aria-hidden="true" /> Lo calcula el servidor</>}
        />
        <Dato termino="Observaciones" valor={ejemplar.observaciones || <Opcional />} ancho />
        {ejemplar.id && <Dato termino="ID en MongoDB (_id)" valor={<code className="codigo-id">{ejemplar.id}</code>} />}
        {ejemplar.libroId && (
          <Dato termino="ID del libro (libroId)" valor={<code className="codigo-id">{ejemplar.libroId}</code>} />
        )}
        {ejemplar.version !== undefined && ejemplar.version !== null && <Dato termino="Versión" valor={ejemplar.version} />}
      </dl>
    </article>
  );
}
