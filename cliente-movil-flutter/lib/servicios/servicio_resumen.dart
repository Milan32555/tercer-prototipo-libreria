import '../modelos/resumen_inventario.dart';
import 'cliente_graphql.dart';

/// Resumen del inventario para la ventana principal: totales, ejemplares por estado y valor del
/// inventario. Lo calculan 3 tareas en paralelo en el servidor; la pantalla solo lo muestra.
Future<ResumenInventario> consultarResumenInventario() async {
  final data = await ClienteGraphQL().ejecutar('''query {
       resumenInventario {
         totalLibros
         totalEjemplares
         ejemplaresPorEstado { estado cantidad porcentaje }
         valorInventario
         duracionMs
         tareas { nombre hilo duracionMs exitosa }
       }
     }''');
  return ResumenInventario.desdeJson(data['resumenInventario'] as Map<String, dynamic>);
}
