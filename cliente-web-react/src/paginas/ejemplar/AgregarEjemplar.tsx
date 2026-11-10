import { FormEvent, useState } from "react";
import { Check } from "lucide-react";
import { crearEjemplar } from "../../graphql/servicioEjemplar";
import { textoONulo } from "../../graphql/servicioLibro";
import { mensajeDeError } from "../../graphql/clienteGraphQL";
import { ejemplarObservable } from "../../observer/Observable";
import { CampoLibroPorIsbn } from "../../componentes/CampoLibroPorIsbn";
import { OPCIONES_ESTADO } from "../../componentes/opciones";
import { AreaTexto } from "../../componentes/ui/AreaTexto";
import { useAvisos } from "../../componentes/ui/Avisos";
import { Banner } from "../../componentes/ui/Banner";
import { Boton } from "../../componentes/ui/Boton";
import { Campo } from "../../componentes/ui/Campo";
import { Encabezado } from "../../componentes/ui/Encabezado";
import { Selector } from "../../componentes/ui/Selector";
import { EstadoEjemplar } from "../../types";
import {
  Errores, hayErrores, validar, validarCodigoEjemplar, validarDecimalPositivo, validarEnteroNoNegativo,
  validarFechaNoFutura, validarIsbn, validarLargoMaximo, validarObligatorio
} from "../../validaciones";

type CampoEjemplar =
  | "codigo" | "isbnLibro" | "ubicacion" | "fechaIngreso" | "precioReposicion" | "numeroPrestamos" | "observaciones";

const VACIO = {
  codigo: "", isbnLibro: "", ubicacion: "", fechaIngreso: "", precioReposicion: "", numeroPrestamos: "0", observaciones: ""
};

/** Caso de uso: Adicionar Ejemplar (una copia física de un libro que ya existe, elegido por su ISBN). */
export default function AgregarEjemplar() {
  const [datos, setDatos] = useState(VACIO);
  const [estado, setEstado] = useState<EstadoEjemplar>("DISPONIBLE");
  const [errores, setErrores] = useState<Errores<CampoEjemplar>>({});
  const [errorServidor, setErrorServidor] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const avisar = useAvisos();

  const poner = (campo: CampoEjemplar, valor: string) => {
    setDatos((d) => ({ ...d, [campo]: valor }));
    if (errores[campo]) setErrores((err) => ({ ...err, [campo]: undefined }));
  };
  const cambiar = (campo: CampoEjemplar) => (e: { target: { value: string } }) => poner(campo, e.target.value);

  async function manejarEnvio(e: FormEvent) {
    e.preventDefault();
    setErrorServidor(null);
    const encontrados = validar<CampoEjemplar>({
      codigo: validarCodigoEjemplar(datos.codigo),
      isbnLibro: validarIsbn(datos.isbnLibro),
      ubicacion: validarObligatorio(datos.ubicacion),
      fechaIngreso: validarFechaNoFutura(datos.fechaIngreso),
      precioReposicion: validarDecimalPositivo(datos.precioReposicion),
      numeroPrestamos: validarEnteroNoNegativo(datos.numeroPrestamos),
      observaciones: validarLargoMaximo(datos.observaciones, 300)
    });
    setErrores(encontrados);
    if (hayErrores(encontrados)) return;

    setEnviando(true);
    try {
      const ejemplar = await crearEjemplar({
        codigo: datos.codigo.trim().toUpperCase(),
        isbnLibro: datos.isbnLibro.trim(),
        estado,
        ubicacion: datos.ubicacion.trim(),
        fechaIngreso: datos.fechaIngreso,
        precioReposicion: Number(datos.precioReposicion),
        numeroPrestamos: Number(datos.numeroPrestamos),
        observaciones: textoONulo(datos.observaciones)
      });
      ejemplarObservable.notificar();
      avisar({
        titulo: "Ejemplar adicionado",
        descripcion: `${ejemplar.codigo} quedó registrado para «${ejemplar.libro?.titulo ?? ejemplar.isbnLibro}».`
      });
      setDatos(VACIO);
      setEstado("DISPONIBLE");
    } catch (err) {
      setErrorServidor(mensajeDeError(err));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <Encabezado seccion="Ejemplares" titulo="Adicionar ejemplar" descripcion="Registra una copia física de un libro que ya está en el inventario." />

      {errorServidor && <Banner titulo="No se pudo adicionar el ejemplar" className="espacio-abajo">{errorServidor}</Banner>}

      <form className="panel formulario-secciones" onSubmit={manejarEnvio} noValidate>
        <section className="seccion-formulario" aria-labelledby="sec-ejemplar">
          <div>
            <h2 id="sec-ejemplar">Identificación</h2>
            <p className="seccion-formulario-ayuda">El código es único por copia (EJ-000). El libro debe existir: elígelo por su ISBN.</p>
          </div>
          <div className="rejilla-campos">
            <Campo etiqueta="Código del ejemplar" value={datos.codigo} onChange={cambiar("codigo")} error={errores.codigo}
                   placeholder="EJ-021" autoComplete="off" />
            <CampoLibroPorIsbn valor={datos.isbnLibro} alCambiar={(v) => poner("isbnLibro", v)} error={errores.isbnLibro} />
          </div>
        </section>

        <section className="seccion-formulario" aria-labelledby="sec-estado">
          <div>
            <h2 id="sec-estado">Estado y ubicación</h2>
            <p className="seccion-formulario-ayuda">Dónde está la copia y en qué condición se encuentra.</p>
          </div>
          <div className="rejilla-campos">
            <Selector etiqueta="Estado" valor={estado} opciones={OPCIONES_ESTADO} alCambiar={(s) => setEstado(s || "DISPONIBLE")} />
            <Campo etiqueta="Ubicación" value={datos.ubicacion} onChange={cambiar("ubicacion")} error={errores.ubicacion}
                   placeholder="Estante A1" />
          </div>
        </section>

        <section className="seccion-formulario" aria-labelledby="sec-ingreso">
          <div>
            <h2 id="sec-ingreso">Ingreso y valor</h2>
            <p className="seccion-formulario-ayuda">Préstamos que ya tenía la copia al registrarla (normalmente 0).</p>
          </div>
          <div className="rejilla-campos">
            <Campo etiqueta="Fecha de ingreso" type="datetime-local" value={datos.fechaIngreso}
                   onChange={cambiar("fechaIngreso")} error={errores.fechaIngreso} />
            <Campo etiqueta="Precio de reposición" type="number" inputMode="decimal" min="0" step="0.01" prefijo="$"
                   value={datos.precioReposicion} onChange={cambiar("precioReposicion")} error={errores.precioReposicion} />
            <Campo etiqueta="Número de préstamos" type="number" inputMode="numeric" min="0" step="1"
                   value={datos.numeroPrestamos} onChange={cambiar("numeroPrestamos")} error={errores.numeroPrestamos} />
            <AreaTexto etiqueta="Observaciones (opcional)" value={datos.observaciones} onChange={cambiar("observaciones")}
                       error={errores.observaciones} maximo={300} />
          </div>
        </section>

        <div className="acciones acciones-pie">
          <Boton type="submit" variante="primario" icono={Check} cargando={enviando}>Adicionar ejemplar</Boton>
        </div>
      </form>
    </>
  );
}
