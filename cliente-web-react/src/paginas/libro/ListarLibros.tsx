import { FormEvent, useState } from "react";
import { BookOpen, FilterX, Plus, Search } from "lucide-react";
import { FiltroLibros, SIN_FILTRO_LIBROS, hayFiltroLibros, listarLibros } from "../../graphql/servicioLibro";
import { mensajeDeError } from "../../graphql/clienteGraphQL";
import { libroObservable } from "../../observer/Observable";
import { useListadoObservado } from "../../observer/useListadoObservado";
import { formatearFechaCorta, formatearPrecio } from "../../formato";
import { OPCIONES_GENERO_FILTRO } from "../../componentes/opciones";
import { Banner } from "../../componentes/ui/Banner";
import { Boton, BotonEnlace } from "../../componentes/ui/Boton";
import { Campo } from "../../componentes/ui/Campo";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { InsigniaGenero } from "../../componentes/ui/Insignias";
import { Paginador } from "../../componentes/ui/Paginador";
import { Selector } from "../../componentes/ui/Selector";
import { Columna, TablaDatos } from "../../componentes/ui/TablaDatos";
import { Genero, Libro } from "../../types";

const COLUMNAS: Columna<Libro>[] = [
  {
    titulo: "Título",
    celda: (l) => (
      <span className="celda-titulo">
        <strong className="titulo-libro">{l.titulo}</strong>
        <small>{l.autor}</small>
      </span>
    )
  },
  { titulo: "Género", celda: (l) => <InsigniaGenero genero={l.genero} /> },
  { titulo: "ISBN", celda: (l) => l.isbn, apagada: true },
  { titulo: "Editorial", celda: (l) => l.editorial || "—", apagada: true },
  { titulo: "Publicación", celda: (l) => formatearFechaCorta(l.fechaPublicacion), apagada: true },
  { titulo: "Páginas", celda: (l) => l.numeroPaginas, numerica: true },
  { titulo: "Ejemplares", celda: (l) => l.cantidadEjemplares ?? "—", numerica: true },
  { titulo: "Precio", celda: (l) => formatearPrecio(l.precio), numerica: true }
];

/**
 * Caso de uso: Listar Libros en una grilla paginada. Sin filtro muestra todos (query libros); con
 * filtro por género y/o rango de precio usa la consulta personalizada librosPorPrecioYGenero.
 * El filtro, el total y la paginación los resuelve el servidor en MongoDB.
 *
 * Patrón Observer: mientras la pantalla está abierta se SUSCRIBE a `libroObservable`; cuando
 * el sujeto notifica un cambio (en este cliente o en otro), la página actual se vuelve a pedir.
 */
export default function ListarLibros() {
  const [libros, setLibros] = useState<Libro[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [pagina, setPagina] = useState(0);
  const [tamanio, setTamanio] = useState(10);
  const [filtro, setFiltro] = useState<FiltroLibros>(SIN_FILTRO_LIBROS);
  const [errorFiltro, setErrorFiltro] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [cargado, setCargado] = useState(false);

  async function cargar(f: FiltroLibros = filtro, p: number = pagina, t: number = tamanio) {
    setCargando(true);
    setError(null);
    try {
      const resultado = await listarLibros(f, p, t);
      // Si se borraron libros y la página quedó vacía, se vuelve a la última que existe.
      if (resultado.contenido.length === 0 && p > 0 && resultado.totalPaginas > 0) {
        setPagina(resultado.totalPaginas - 1);
        await cargar(f, resultado.totalPaginas - 1, t);
        return;
      }
      setLibros(resultado.contenido);
      setTotal(resultado.totalElementos);
      setTotalPaginas(resultado.totalPaginas);
    } catch (err) {
      setError(mensajeDeError(err));
    } finally {
      setCargando(false);
      setCargado(true);
    }
  }

  useListadoObservado(libroObservable, cargar);

  function filtrar(e: FormEvent) {
    e.preventDefault();
    const min = filtro.precioMin.trim() === "" ? null : Number(filtro.precioMin);
    const max = filtro.precioMax.trim() === "" ? null : Number(filtro.precioMax);
    if ((min !== null && (Number.isNaN(min) || min < 0)) || (max !== null && (Number.isNaN(max) || max < 0))) {
      setErrorFiltro("Los precios deben ser números mayores o iguales a cero.");
      return;
    }
    if (min !== null && max !== null && min > max) {
      setErrorFiltro("El precio mínimo no puede ser mayor que el máximo.");
      return;
    }
    setErrorFiltro(null);
    setPagina(0);
    void cargar(filtro, 0);
  }

  function cambiarGenero(nuevo: Genero | "") {
    const nuevoFiltro = { ...filtro, genero: nuevo };
    setFiltro(nuevoFiltro);
    setPagina(0);
    void cargar(nuevoFiltro, 0);
  }

  function limpiar() {
    setFiltro(SIN_FILTRO_LIBROS);
    setErrorFiltro(null);
    setPagina(0);
    void cargar(SIN_FILTRO_LIBROS, 0);
  }

  function irAPagina(nueva: number) {
    setPagina(nueva);
    void cargar(filtro, nueva);
  }

  function cambiarTamanio(nuevo: number) {
    setTamanio(nuevo);
    setPagina(0);
    void cargar(filtro, 0, nuevo);
  }

  const hayFiltros = hayFiltroLibros(filtro);

  return (
    <>
      <Encabezado
        seccion="Libros"
        titulo="Listar libros"
        descripcion="Todos los títulos del inventario, por páginas. El filtro lo aplica el servidor en MongoDB."
        acciones={<BotonEnlace to="/libros/agregar" variante="primario" icono={Plus}>Adicionar libro</BotonEnlace>}
      />

      {error && libros.length > 0 && <Banner titulo="No se pudo actualizar la lista" className="espacio-abajo">{error}</Banner>}

      <section className="panel" aria-label="Libros">
        <form className="barra-filtros" role="search" onSubmit={filtrar} noValidate>
          <Selector etiqueta="Género" valor={filtro.genero} opciones={OPCIONES_GENERO_FILTRO} alCambiar={cambiarGenero} />
          <Campo
            className="campo-precio"
            etiqueta="Precio mínimo"
            type="number" inputMode="decimal" min="0" prefijo="$"
            value={filtro.precioMin}
            onChange={(e) => setFiltro({ ...filtro, precioMin: e.target.value })}
            error={errorFiltro}
          />
          <Campo
            className="campo-precio"
            etiqueta="Precio máximo"
            type="number" inputMode="decimal" min="0" prefijo="$"
            value={filtro.precioMax}
            onChange={(e) => setFiltro({ ...filtro, precioMax: e.target.value })}
          />
          <Boton type="submit" icono={Search} disabled={cargando && !cargado}>Filtrar</Boton>
          {hayFiltros && <Boton variante="fantasma" icono={FilterX} onClick={limpiar}>Limpiar filtros</Boton>}
        </form>

        <div className="barra-tabla" aria-live="polite">
          <span className="conteo">{total === 1 ? "1 libro" : `${total} libros`}</span>
          <span>{hayFiltros ? "Consulta: libros por precio y género" : "Consulta: todos los libros"} · total calculado por el servidor</span>
        </div>

        <TablaDatos
          etiqueta="Libros del inventario"
          columnas={COLUMNAS}
          filas={libros}
          claveFila={(l) => l.isbn}
          cargandoInicial={!cargado}
          ocupada={cargando}
          error={error}
          alReintentar={() => void cargar()}
          vacio={hayFiltros
            ? {
                icono: FilterX,
                titulo: "Ningún libro coincide con el filtro",
                descripcion: "Prueba con otro género o con otro rango de precio.",
                accion: <Boton icono={FilterX} onClick={limpiar}>Limpiar filtros</Boton>
              }
            : {
                icono: BookOpen,
                titulo: "Todavía no hay libros",
                descripcion: "Los libros que adiciones aparecerán aquí.",
                accion: <BotonEnlace to="/libros/agregar" icono={Plus}>Adicionar libro</BotonEnlace>
              }}
        />

        <Paginador pagina={pagina} totalPaginas={totalPaginas} tamanio={tamanio} ocupado={cargando}
                   alCambiarPagina={irAPagina} alCambiarTamanio={cambiarTamanio} />
      </section>
    </>
  );
}
