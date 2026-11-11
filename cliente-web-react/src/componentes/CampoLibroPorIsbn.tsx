import { ReactNode, useEffect, useState } from "react";
import { BookCheck, BookX } from "lucide-react";
import { buscarLibroPorIsbn } from "../graphql/servicioLibro";
import { Libro } from "../types";
import { Campo } from "./ui/Campo";

type Verificacion = { tipo: "nada" } | { tipo: "buscando" } | { tipo: "encontrado"; libro: Libro } | { tipo: "no-existe" };

/**
 * El ejemplar se asocia a su libro por el ISBN: al escribirlo, se busca el libro en el servidor y
 * se muestra su título y autor (o que no existe). El servidor traduce el ISBN al _id del libro.
 */
export function CampoLibroPorIsbn({ valor, alCambiar, error }: {
  valor: string;
  alCambiar: (valor: string) => void;
  error?: string | null;
}) {
  const [verificacion, setVerificacion] = useState<Verificacion>({ tipo: "nada" });

  useEffect(() => {
    const isbn = valor.trim();
    if (!/^\d{1,4}$/.test(isbn)) {
      setVerificacion({ tipo: "nada" });
      return;
    }
    let vigente = true;
    setVerificacion({ tipo: "buscando" });
    const espera = setTimeout(() => {
      buscarLibroPorIsbn(isbn)
        .then((libro) => {
          if (vigente) setVerificacion(libro ? { tipo: "encontrado", libro } : { tipo: "no-existe" });
        })
        .catch(() => {
          if (vigente) setVerificacion({ tipo: "nada" });
        });
    }, 350);
    return () => {
      vigente = false;
      clearTimeout(espera);
    };
  }, [valor]);

  let ayuda: ReactNode = "Escribe el ISBN del libro al que pertenece la copia.";
  if (verificacion.tipo === "buscando") ayuda = "Buscando el libro…";
  if (verificacion.tipo === "encontrado") {
    ayuda = (
      <span className="libro-elegido">
        <BookCheck size={14} aria-hidden="true" />
        <span className="titulo-libro">{verificacion.libro.titulo}</span> · {verificacion.libro.autor}
      </span>
    );
  }
  if (verificacion.tipo === "no-existe") {
    ayuda = (
      <span className="libro-no-existe">
        <BookX size={14} aria-hidden="true" /> No hay ningún libro con ese ISBN.
      </span>
    );
  }

  return (
    <Campo
      etiqueta="ISBN del libro"
      value={valor}
      onChange={(e) => alCambiar(e.target.value)}
      error={error}
      placeholder="1001"
      autoComplete="off"
      ayuda={ayuda}
    />
  );
}
