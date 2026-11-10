import { useState } from "react";
import { Library } from "lucide-react";
import { consultarDetalleLibro } from "../../graphql/servicioLibro";
import { mensajeDeError } from "../../graphql/clienteGraphQL";
import { formatearFecha, formatearPrecio } from "../../formato";
import FichaLibro from "../../componentes/FichaLibro";
import { Banner } from "../../componentes/ui/Banner";
import { BuscadorClave } from "../../componentes/ui/BuscadorClave";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { InsigniaEstado } from "../../componentes/ui/Insignias";
import { SinResultados } from "../../componentes/ui/SinResultados";
import { Columna, TablaDatos } from "../../componentes/ui/TablaDatos";
import { DetalleLibro as Detalle, Ejemplar } from "../../types";

const formatoPorcentaje = new Intl.NumberFormat("es-CO", { maximumFractionDigits: 1 });

const COLUMNAS: Columna<Ejemplar>[] = [
  { titulo: "Código", celda: (e) => <strong>{e.codigo}</strong> },
  { titulo: "Estado", celda: (e) => <InsigniaEstado estado={e.estado} /> },
  { titulo: "Ubicación", celda: (e) => e.ubicacion },
  { titulo: "Fecha de ingreso", celda: (e) => formatearFecha(e.fechaIngreso), apagada: true },
  { titulo: "Préstamos", celda: (e) => e.numeroPrestamos, numerica: true },
  { titulo: "Reposición", celda: (e) => formatearPrecio(e.precioReposicion), numerica: true },
  { titulo: "Observaciones", celda: (e) => e.observaciones || "—", apagada: true }
];

/**
 * Caso de uso: Detalle de un libro (consulta propia que muestra datos del MAESTRO y del DETALLE):
 * el libro con toda su información, sus ejemplares y cuántos hay en cada estado. Lo arma el
 * servidor con la query detalleLibro.
 */
export default function DetalleLibro() {
  const [detalle, setDetalle] = useState<Detalle | null>(null);
  const [sinResultado, setSinResultado] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  async function buscar(isbn: string) {
    setError(null); setDetalle(null); setSinResultado(null);
    setCargando(true);
    try {
      const resultado = await consultarDetalleLibro(isbn);
      setDetalle(resultado);
      if (!resultado) setSinResultado(`No hay ningún libro con el ISBN «${isbn}».`);
    } catch (err) {
      setError(mensajeDeError(err));
    } finally {
      setCargando(false);
    }
  }

  return (
    <>
      <Encabezado
        seccion="Libros"
        titulo="Detalle del libro"
        descripcion="Un libro (maestro) con todos sus ejemplares (detalle) y cuántos hay en cada estado."
      />
      <div className="pila">
        <BuscadorClave etiqueta="ISBN del libro" placeholder="978-0307474728" cargando={cargando} alBuscar={buscar} />
        {error && <Banner titulo="No se pudo consultar">{error}</Banner>}
        {sinResultado && <SinResultados titulo="Sin resultados">{sinResultado}</SinResultados>}
        {detalle && (
          <>
            <FichaLibro libro={detalle.libro} titulo="Maestro: libro" />

            <section className="panel" aria-labelledby="titulo-resumen-ejemplares">
              <header className="panel-cabecera">
                <h2 className="panel-titulo" id="titulo-resumen-ejemplares">Resumen de sus ejemplares</h2>
                <span className="panel-subtitulo">Calculado por el servidor</span>
              </header>
              <dl className="detalle-conteos">
                <div><dt>Ejemplares</dt><dd className="cifra">{detalle.totalEjemplares}</dd></div>
                <div><dt>Préstamos acumulados</dt><dd className="cifra">{detalle.totalPrestamos}</dd></div>
                <div><dt>Valor de reposición</dt><dd className="cifra">{formatearPrecio(detalle.valorReposicion)}</dd></div>
                {detalle.conteoPorEstado.map((c) => (
                  <div key={c.estado}>
                    <dt><InsigniaEstado estado={c.estado} /></dt>
                    <dd>{c.cantidad} · {formatoPorcentaje.format(c.porcentaje)} %</dd>
                  </div>
                ))}
              </dl>
            </section>

            <section className="panel" aria-label="Detalle: ejemplares del libro">
              <div className="barra-tabla">
                <span className="conteo">Detalle: {detalle.totalEjemplares === 1 ? "1 ejemplar" : `${detalle.totalEjemplares} ejemplares`}</span>
              </div>
              <TablaDatos
                etiqueta="Ejemplares del libro"
                columnas={COLUMNAS}
                filas={detalle.ejemplares}
                claveFila={(e) => e.codigo}
                cargandoInicial={false}
                vacio={{ icono: Library, titulo: "Este libro no tiene ejemplares", descripcion: "Se puede eliminar." }}
              />
            </section>
          </>
        )}
      </div>
    </>
  );
}
