import { TextareaHTMLAttributes, useId } from "react";
import { CircleAlert } from "lucide-react";
import { clases } from "./clases";

interface AreaTextoProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  etiqueta: string;
  error?: string | null;
  ayuda?: string;
  /** Muestra "n / máximo" debajo del campo. */
  maximo?: number;
}

/** Campo de texto de varias líneas (sinopsis, observaciones), con el mismo estilo que Campo. */
export function AreaTexto({ etiqueta, error, ayuda, maximo, id, value, ...area }: AreaTextoProps) {
  const idGenerado = useId();
  const idCampo = id ?? idGenerado;
  const idError = error ? `${idCampo}-error` : undefined;
  const idAyuda = ayuda || maximo ? `${idCampo}-ayuda` : undefined;
  const largo = typeof value === "string" ? value.length : 0;

  return (
    <div className="campo campo-ancho">
      <label htmlFor={idCampo}>{etiqueta}</label>
      <div className={clases("control control-area", error && "con-error")}>
        <textarea id={idCampo} rows={3} value={value} aria-invalid={error ? true : undefined}
                  aria-describedby={[idError, idAyuda].filter(Boolean).join(" ") || undefined} {...area} />
      </div>
      {error && (
        <p id={idError} className="campo-error">
          <CircleAlert size={14} aria-hidden="true" />
          {error}
        </p>
      )}
      {(ayuda || maximo) && (
        <p id={idAyuda} className="campo-ayuda">
          {ayuda}
          {maximo && <span className={clases("contador", largo > maximo && "excedido")}> {largo} / {maximo}</span>}
        </p>
      )}
    </div>
  );
}
