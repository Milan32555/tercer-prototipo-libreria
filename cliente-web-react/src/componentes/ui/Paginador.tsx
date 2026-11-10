import { ChevronLeft, ChevronRight } from "lucide-react";
import { Boton } from "./Boton";

export const TAMANIOS_PAGINA = [10, 20, 50] as const;

/**
 * Controles de paginación de un Listar. La página empieza en 0 (como en el servidor) pero se
 * muestra empezando en 1.
 */
export function Paginador({ pagina, totalPaginas, tamanio, ocupado = false, alCambiarPagina, alCambiarTamanio }: {
  pagina: number;
  totalPaginas: number;
  tamanio: number;
  ocupado?: boolean;
  alCambiarPagina: (pagina: number) => void;
  alCambiarTamanio: (tamanio: number) => void;
}) {
  const total = Math.max(totalPaginas, 1);
  return (
    <nav className="paginador" aria-label="Paginación">
      <label className="paginador-tamanio">
        Filas por página
        <select value={tamanio} onChange={(e) => alCambiarTamanio(Number(e.target.value))}>
          {TAMANIOS_PAGINA.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
      </label>
      <span className="paginador-estado" aria-live="polite">Página {Math.min(pagina + 1, total)} de {total}</span>
      <div className="paginador-botones">
        <Boton variante="fantasma" icono={ChevronLeft} disabled={ocupado || pagina <= 0}
               onClick={() => alCambiarPagina(pagina - 1)} aria-label="Página anterior">Anterior</Boton>
        <Boton variante="fantasma" icono={ChevronRight} disabled={ocupado || pagina + 1 >= total}
               onClick={() => alCambiarPagina(pagina + 1)} aria-label="Página siguiente">Siguiente</Boton>
      </div>
    </nav>
  );
}
