import 'dart:convert';

import 'package:cliente_movil_libreria/servicios/cliente_graphql.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// Error de negocio que el servidor simulado devuelve en "errors" (como el servidor real).
class ErrorSimulado implements Exception {
  ErrorSimulado(this.mensaje);
  final String mensaje;
}

typedef Resolver = Object? Function(Map<String, dynamic> variables);

/// Servidor GraphQL falso para las pruebas: responde según los campos raíz de cada
/// consulta/mutación (puede haber varios en una petición, p. ej. la lista y su total)
/// y guarda las peticiones recibidas para poder verificarlas.
class ServidorSimulado {
  ServidorSimulado(this.resolvers);

  final Map<String, Resolver> resolvers;
  final List<({String campo, Map<String, dynamic> variables})> peticiones = [];

  static final _identificador = RegExp(r'[A-Za-z_]\w*');

  /// Campos del primer nivel de la operación: `query (...) { a(...) { x } b }` → [a, b].
  static List<String> camposRaiz(String query) {
    final campos = <String>[];
    var llaves = 0;
    var parentesis = 0;
    var i = 0;
    while (i < query.length) {
      final c = query[i];
      if (c == '(') {
        parentesis++;
      } else if (c == ')') {
        parentesis--;
      } else if (parentesis == 0 && c == '{') {
        llaves++;
      } else if (parentesis == 0 && c == '}') {
        llaves--;
      } else if (llaves == 1 && parentesis == 0) {
        final nombre = _identificador.matchAsPrefix(query, i);
        if (nombre != null) {
          campos.add(nombre.group(0)!);
          i = nombre.end;
          continue;
        }
      }
      i++;
    }
    return campos;
  }

  /// Conecta el Singleton ClienteGraphQL a este servidor simulado.
  void instalar() => ClienteGraphQL().clienteHttp = MockClient(_responder);

  List<Map<String, dynamic>> variablesDe(String campo) => [
    for (final p in peticiones)
      if (p.campo == campo) p.variables,
  ];

  Future<http.Response> _responder(http.Request request) async {
    final cuerpo = jsonDecode(request.body) as Map<String, dynamic>;
    final campos = camposRaiz(cuerpo['query'] as String);
    final variables = (cuerpo['variables'] as Map<String, dynamic>?) ?? {};
    for (final campo in campos) {
      peticiones.add((campo: campo, variables: variables));
    }

    final sinSimular = campos.where((campo) => !resolvers.containsKey(campo)).toList();
    if (sinSimular.isNotEmpty) {
      return _json({
        'errors': [
          {'message': 'Campo no simulado: ${sinSimular.join(', ')}'},
        ],
      }, 400);
    }
    try {
      return _json({
        'data': {for (final campo in campos) campo: resolvers[campo]!(variables)},
      });
    } on ErrorSimulado catch (e) {
      return _json({
        'errors': [
          {'message': e.mensaje},
        ],
        'data': {for (final campo in campos) campo: null},
      });
    }
  }

  static http.Response _json(Object cuerpo, [int estado = 200]) =>
      http.Response(jsonEncode(cuerpo), estado, headers: {'content-type': 'application/json; charset=utf-8'});
}

Map<String, dynamic> libroJson({
  String id = '6af377e075da53cd0c59caa1',
  String isbn = '1003',
  String titulo = 'Delirio',
  String autor = 'Laura Restrepo',
  num precio = 55000,
  int numeroPaginas = 352,
  String fechaPublicacion = '2004-03-01T10:30:00',
  String genero = 'FICCION',
  String? editorial = 'Alfaguara',
  String? sinopsis,
  int version = 2,
  int? cantidadEjemplares,
}) => {
  'id': id,
  'isbn': isbn,
  'titulo': titulo,
  'autor': autor,
  'precio': precio,
  'numeroPaginas': numeroPaginas,
  'fechaPublicacion': fechaPublicacion,
  'genero': genero,
  'editorial': editorial,
  'sinopsis': sinopsis,
  'version': version,
  'cantidadEjemplares': ?cantidadEjemplares,
};

Map<String, dynamic> ejemplarJson({
  String codigo = 'EJ-001',
  String estado = 'DISPONIBLE',
  String ubicacion = 'Estante A1',
  String fechaIngreso = '2024-02-10T09:00:00',
  num precioReposicion = 65000,
  int numeroPrestamos = 4,
  String isbnLibro = '1001',
  String? observaciones,
  int version = 0,
}) => {
  'id': '6af377e075da53cd0c59cab0',
  'codigo': codigo,
  'libroId': '6af377e075da53cd0c59ca9f',
  'isbnLibro': isbnLibro,
  'estado': estado,
  'ubicacion': ubicacion,
  'fechaIngreso': fechaIngreso,
  'precioReposicion': precioReposicion,
  'numeroPrestamos': numeroPrestamos,
  'observaciones': observaciones,
  'version': version,
  'libro': {'titulo': 'Cien años de soledad', 'autor': 'Gabriel García Márquez'},
};

/// Fila de ejemplaresConLibro ($lookup): atributos del ejemplar + isbn, título y autor del libro.
Map<String, dynamic> ejemplarConLibroJson({
  String codigo = 'EJ-001',
  String estado = 'DISPONIBLE',
  int numeroPrestamos = 4,
  String? observaciones,
}) {
  final fila = ejemplarJson(codigo: codigo, estado: estado, numeroPrestamos: numeroPrestamos, observaciones: observaciones)
    ..remove('libro');
  return {...fila, 'tituloLibro': 'Cien años de soledad', 'autorLibro': 'Gabriel García Márquez'};
}

/// Una página de resultados como la devuelve el servidor.
Map<String, dynamic> paginaJson(List<Map<String, dynamic>> contenido, {int pagina = 0, int? total, int totalPaginas = 1}) => {
  'contenido': contenido,
  'pagina': pagina,
  'tamanio': 10,
  'totalElementos': total ?? contenido.length,
  'totalPaginas': totalPaginas,
};

/// Resumen del inventario tal como lo calcula el servidor (query resumenInventario).
Map<String, dynamic> resumenJson({
  int totalLibros = 2,
  Map<String, int> porEstado = const {'DISPONIBLE': 1, 'PRESTADO': 1, 'DANIADO': 0, 'DADO_DE_BAJA': 0},
}) {
  final total = porEstado.values.fold<int>(0, (a, b) => a + b);
  return {
    'totalLibros': totalLibros,
    'totalEjemplares': total,
    'ejemplaresPorEstado': [
      for (final MapEntry(key: estado, value: cantidad) in porEstado.entries)
        {'estado': estado, 'cantidad': cantidad, 'porcentaje': total == 0 ? 0 : cantidad * 100 / total},
    ],
    'valorInventario': 1162000,
    'duracionMs': 12,
    'tareas': [
      {'nombre': 'contar-libros', 'hilo': 'tarea-1', 'duracionMs': 3, 'exitosa': true},
      {'nombre': 'contar-ejemplares-por-estado', 'hilo': 'tarea-2', 'duracionMs': 9, 'exitosa': true},
      {'nombre': 'valor-total-inventario', 'hilo': 'tarea-3', 'duracionMs': 7, 'exitosa': true},
    ],
  };
}
