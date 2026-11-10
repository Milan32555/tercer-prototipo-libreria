import { FormEvent, useState } from "react";
import { FilterX, Library, Plus, Search } from "lucide-react";
import { listarEjemplaresConLibro } from "../../graphql/servicioEjemplar";
import { mensajeDeError } from "../../graphql/clienteGraphQL";
import { ejemplarObservable } from "../../observer/Observable";
import { useListadoObservado } from "../../observer/useListadoObservado";
import { formatearFecha, formatearPrecio } from "../../formato";
import { Banner } from "../../componentes/ui/Banner";
import { Boton, BotonEnlace } from "../../componentes/ui/Boton";
import { Campo } from "../../componentes/ui/Campo";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { InsigniaEstado } from "../../componentes/ui/Insignias";
import { Paginador } from "../../componentes/ui/Paginador";
import { Segmentado } from "../../componentes/ui/Segmentado";
import { Columna, TablaDatos } from "../../componentes/ui/TablaDatos";
import { ESTADOS_EJEMPLAR, EstadoEjemplar, EjemplarConLibro, etiquetaEstado } from "../../types";

type FiltroEstado = EstadoEjemplar | "TODOS";

const OPCIONES_FILTRO_ESTADO = [
  { valor: "TODOS" as FiltroEstado, texto: "Todos" },
  ...ESTADOS_EJEMPLAR.map((e) => ({ valor: e as FiltroEstado, texto: etiquetaEstado(e) }))
];

/** TODOS los atributos del ejemplar + título y autor de su libro (colección A). */
const COLUMNAS: Columna<EjemplarConLibro>[] = [
  { titulo: "Código", celda: (e) => <strong>{e.codigo}</strong> },
  { titulo: "Título del libro", celda: (e) => <span className="titulo-libro">{e.tituloLibro ?? "—"}</span> },
  { titulo: "Autor del libro", celda: (e) => e.autorLibro ?? "—" },
  { titulo: "ISBN del libro", celda: (e) => e.isbnLibro ?? "—", apagada: true },
  { titulo: "Estado", celda: (e) => <InsigniaEstado estado={e.estado} /> },
  { titulo: "Ubicación", celda: (e) => e.ubicacion },
  { titulo: "Fecha de ingreso", celda: (e) => formatearFecha(e.fechaIngreso), apagada: true },
  { titulo: "Préstamos", celda: (e) => e.numeroPrestamos, numerica: true },
  { titulo: "Reposición", celda: (e) => formatearPrecio(e.precioReposicion), numerica: true },
  {
    titulo: "Observaciones",
    celda: (e) => (e.observaciones ? <span className="celda-texto-largo" title={e.observaciones}>{e.observaciones}</span> : "—"),
    apagada: true
  },
  { titulo: "Versión", celda: (e) => e.version ?? "—", numerica: true },
  { titulo: "ID (_id)", celda: (e) => <code className="codigo-id">{e.id}</code> },
  { titulo: "ID del libro (libroId)", celda: (e) => <code className="codigo-id">{e.libroId}</code> }
];

/**
 * Caso de uso: Listar Ejemplares. Usa la consulta personalizada ejemplaresConLibro ($lookup en
 * MongoDB): cada fila trae TODOS los atributos del ejemplar más el título y el autor de su
 * libro. Filtros por estado y/o ISBN del libro, y paginación, en el servidor. Observer igual
 * que en Listar Libros.
 */
export default function ListarEjemplares() {
  const [ejemplares, setEjemplares] = useState<EjemplarConLibro[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [pagina, setPagina] = useState(0);
  const [tamanio, setTamanio] = useState(10);
  const [estado, setEstado] = useState<FiltroEstado>("TODOS");
  const [isbn, setIsbn] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [cargado, setCargado] = useState(false);

  async function cargar(e: FiltroEstado = estado, i: string = isbn, p: number = pagina, t: number = tamanio) {
    setCargando(true);
    setError(null);
    try {
      const resultado = await listarEjemplaresConLibro(e === "TODOS" ? "" : e, i, p, t);
      if (resultado.contenido.length === 0 && p > 0 && resultado.totalPaginas > 0) {
        setPagina(resultado.totalPaginas - 1);
        await cargar(e, i, resultado.totalPaginas - 1, t);
        return;
      }
      setEjemplares(resultado.contenido);
      setTotal(resultado.totalElementos);
      setTotalPaginas(resultado.totalPaginas);
    } catch (err) {
      setError(mensajeDeError(err));
    } finally {
      setCargando(false);
      setCargado(true);
    }
  }

  useListadoObservado(ejemplarObservable, cargar);

  function filtrar(e: FormEvent) {
    e.preventDefault();
    setPagina(0);
    void cargar(estado, isbn, 0);
  }

  function cambiarEstado(nuevo: FiltroEstado) {
    setEstado(nuevo);
    setPagina(0);
    void cargar(nuevo, isbn, 0);
  }

  function limpiar() {
    setEstado("TODOS");
    setIsbn("");
    setPagina(0);
    void cargar("TODOS", "", 0);
  }

  function irAPagina(nueva: number) {
    setPagina(nueva);
    void cargar(estado, isbn, nueva);
  }

  function cambiarTamanio(nuevo: number) {
    setTamanio(nuevo);
    setPagina(0);
    void cargar(estado, isbn, 0, nuevo);
  }

  const hayFiltros = estado !== "TODOS" || isbn.trim() !== "";

  return (
    <>
      <Encabezado
        seccion="Ejemplares"
        titulo="Listar ejemplares"
        descripcion="Todas las copias físicas con todos sus datos y el título y autor de su libro."
        acciones={<BotonEnlace to="/ejemplares/agregar" variante="primario" icono={Plus}>Adicionar ejemplar</BotonEnlace>}
      />

      {error && ejemplares.length > 0 && <Banner titulo="No se pudo actualizar la lista" className="espacio-abajo">{error}</Banner>}

      <section className="panel" aria-label="Ejemplares">
        <form className="barra-filtros" role="search" onSubmit={filtrar}>
          <Segmentado etiqueta="Estado" nombre="estado" valor={estado} opciones={OPCIONES_FILTRO_ESTADO} alCambiar={cambiarEstado} />
          <Campo
            className="campo-busqueda"
            etiqueta="ISBN del libro"
            value={isbn}
            onChange={(e) => setIsbn(e.target.value)}
            placeholder="Opcional"
            autoComplete="off"
          />
          <Boton type="submit" icono={Search} disabled={cargando && !cargado}>Filtrar</Boton>
          {hayFiltros && <Boton variante="fantasma" icono={FilterX} onClick={limpiar}>Limpiar filtros</Boton>}
        </form>

        <div className="barra-tabla" aria-live="polite">
          <span className="conteo">{total === 1 ? "1 ejemplar" : `${total} ejemplares`}</span>
          <span>Consulta: ejemplares con su libro ($lookup) · total calculado por el servidor</span>
        </div>

        <TablaDatos
          etiqueta="Ejemplares del inventario"
          columnas={COLUMNAS}
          filas={ejemplares}
          claveFila={(e) => e.codigo}
          cargandoInicial={!cargado}
          ocupada={cargando}
          error={error}
          alReintentar={() => void cargar()}
          ancha
          vacio={hayFiltros
            ? {
                icono: FilterX,
                titulo: "Ningún ejemplar coincide con el filtro",
                descripcion: "Prueba con otro estado o revisa el ISBN.",
                accion: <Boton icono={FilterX} onClick={limpiar}>Limpiar filtros</Boton>
              }
            : {
                icono: Library,
                titulo: "Todavía no hay ejemplares",
                descripcion: "Las copias que registres aparecerán aquí.",
                accion: <BotonEnlace to="/ejemplares/agregar" icono={Plus}>Adicionar ejemplar</BotonEnlace>
              }}
        />

        <Paginador pagina={pagina} totalPaginas={totalPaginas} tamanio={tamanio} ocupado={cargando}
                   alCambiarPagina={irAPagina} alCambiarTamanio={cambiarTamanio} />
      </section>
    </>
  );
}
