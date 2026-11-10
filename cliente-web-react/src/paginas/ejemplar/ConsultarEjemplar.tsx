import { FormEvent, useState } from "react";
import { Search } from "lucide-react";
import { buscarEjemplarPorCodigo, buscarEjemplaresPorLibroYUbicacion } from "../../graphql/servicioEjemplar";
import { mensajeDeError } from "../../graphql/clienteGraphQL";
import FichaEjemplar from "../../componentes/FichaEjemplar";
import { Banner } from "../../componentes/ui/Banner";
import { Boton } from "../../componentes/ui/Boton";
import { BuscadorClave } from "../../componentes/ui/BuscadorClave";
import { Campo } from "../../componentes/ui/Campo";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { Segmentado } from "../../componentes/ui/Segmentado";
import { SinResultados } from "../../componentes/ui/SinResultados";
import { Ejemplar } from "../../types";
import { Errores, hayErrores, validar, validarObligatorio } from "../../validaciones";

type Criterio = "codigo" | "libro";

const CRITERIOS = [
  { valor: "codigo", texto: "Por código" },
  { valor: "libro", texto: "Por libro y ubicación" }
] as const;

/**
 * Caso de uso: Consultar Ejemplar con dos criterios: por su código (un solo ejemplar) o por el
 * ISBN de su libro + la ubicación (si hay varias copias del libro en ese lugar, se muestran todas).
 */
export default function ConsultarEjemplar() {
  const [criterio, setCriterio] = useState<Criterio>("codigo");
  const [encontrados, setEncontrados] = useState<Ejemplar[]>([]);
  const [isbn, setIsbn] = useState("");
  const [ubicacion, setUbicacion] = useState("");
  const [errores, setErrores] = useState<Errores<"isbn" | "ubicacion">>({});
  const [sinResultado, setSinResultado] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  async function ejecutar(busqueda: () => Promise<Ejemplar[]>, mensajeVacio: string) {
    setError(null); setEncontrados([]); setSinResultado(null);
    setCargando(true);
    try {
      const resultado = await busqueda();
      setEncontrados(resultado);
      if (resultado.length === 0) setSinResultado(mensajeVacio);
    } catch (err) {
      setError(mensajeDeError(err));
    } finally {
      setCargando(false);
    }
  }

  function buscarPorCodigo(codigo: string) {
    void ejecutar(
      async () => {
        const ejemplar = await buscarEjemplarPorCodigo(codigo);
        return ejemplar ? [ejemplar] : [];
      },
      `No hay ningún ejemplar con el código «${codigo}».`
    );
  }

  function buscarPorLibro(e: FormEvent) {
    e.preventDefault();
    const problemas = validar({ isbn: validarObligatorio(isbn), ubicacion: validarObligatorio(ubicacion) });
    setErrores(problemas);
    if (hayErrores(problemas)) return;
    void ejecutar(
      () => buscarEjemplaresPorLibroYUbicacion(isbn, ubicacion),
      `No hay ejemplares del libro «${isbn.trim()}» en «${ubicacion.trim()}».`
    );
  }

  function cambiarCriterio(nuevo: Criterio) {
    setCriterio(nuevo);
    setEncontrados([]); setSinResultado(null); setError(null); setErrores({});
  }

  const selectorCriterio = (
    <Segmentado etiqueta="Buscar por" nombre="criterio" valor={criterio} opciones={CRITERIOS} alCambiar={cambiarCriterio} />
  );

  return (
    <>
      <Encabezado
        seccion="Ejemplares"
        titulo="Consultar ejemplar"
        descripcion="Busca una copia por su código, o por el ISBN de su libro y la ubicación (sin importar mayúsculas)."
      />
      <div className="pila">
        {criterio === "codigo" ? (
          <BuscadorClave etiqueta="Código del ejemplar" placeholder="EJ-001" cargando={cargando} alBuscar={buscarPorCodigo}
                         antes={selectorCriterio} />
        ) : (
          <form className="panel barra-filtros" role="search" onSubmit={buscarPorLibro} noValidate>
            {selectorCriterio}
            <Campo className="campo-busqueda" etiqueta="ISBN del libro" value={isbn} placeholder="978-0307474728"
                   onChange={(e) => { setIsbn(e.target.value); setErrores((x) => ({ ...x, isbn: undefined })); }}
                   error={errores.isbn} autoComplete="off" />
            <Campo className="campo-busqueda" etiqueta="Ubicación" value={ubicacion} placeholder="Estante A1"
                   onChange={(e) => { setUbicacion(e.target.value); setErrores((x) => ({ ...x, ubicacion: undefined })); }}
                   error={errores.ubicacion} autoComplete="off" />
            <Boton type="submit" variante="primario" icono={Search} cargando={cargando}>Buscar</Boton>
          </form>
        )}
        {error && <Banner titulo="No se pudo consultar">{error}</Banner>}
        {sinResultado && <SinResultados titulo="Sin resultados">{sinResultado}</SinResultados>}
        {encontrados.length > 1 && (
          <p className="conteo" aria-live="polite">{encontrados.length} ejemplares de ese libro en esa ubicación</p>
        )}
        {encontrados.map((ejemplar) => <FichaEjemplar key={ejemplar.codigo} ejemplar={ejemplar} />)}
      </div>
    </>
  );
}
