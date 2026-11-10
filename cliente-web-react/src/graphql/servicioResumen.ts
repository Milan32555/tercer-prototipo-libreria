import { ClienteGraphQL } from "./clienteGraphQL";
import { ResumenInventario } from "../types";

/**
 * Resumen del inventario para la ventana principal: totales, ejemplares por estado y valor del
 * inventario. Lo calculan 3 tareas en paralelo en el servidor; la pantalla solo lo muestra.
 */
export async function consultarResumenInventario(): Promise<ResumenInventario> {
  const data = await ClienteGraphQL.getInstancia().ejecutar<{ resumenInventario: ResumenInventario }>(
    `query {
       resumenInventario {
         totalLibros
         totalEjemplares
         ejemplaresPorEstado { estado cantidad porcentaje }
         valorInventario
         duracionMs
         tareas { nombre hilo duracionMs exitosa }
       }
     }`
  );
  return data.resumenInventario;
}
