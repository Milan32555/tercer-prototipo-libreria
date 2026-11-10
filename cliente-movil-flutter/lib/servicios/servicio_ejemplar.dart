import '../modelos/ejemplar.dart';
import '../modelos/pagina.dart';
import 'cliente_graphql.dart';

/// Todos los atributos del ejemplar, más el título y el autor de su libro.
const String camposEjemplar =
    'id codigo libroId isbnLibro estado ubicacion fechaIngreso precioReposicion numeroPrestamos observaciones version '
    'libro { titulo autor }';

/// Listar ejemplares (consulta personalizada `ejemplaresConLibro`, un $lookup en MongoDB): TODOS
/// los atributos de cada ejemplar más el ISBN, título y autor de su libro. Filtros opcionales por
/// estado y/o ISBN; paginado en el servidor.
Future<Pagina<Ejemplar>> listarEjemplaresConLibro({
  required int pagina,
  required int tamanio,
  String? estado,
  String? isbn,
}) async {
  final data = await ClienteGraphQL().ejecutar(
    '''query (\$estado: EstadoEjemplar, \$isbn: String, \$pagina: Int!, \$tamanio: Int!) {
         ejemplaresConLibro(estado: \$estado, isbn: \$isbn, pagina: \$pagina, tamanio: \$tamanio) {
           pagina tamanio totalElementos totalPaginas
           contenido {
             id codigo libroId isbnLibro tituloLibro autorLibro estado ubicacion fechaIngreso
             precioReposicion numeroPrestamos observaciones version
           }
         }
       }''',
    variables: {
      'estado': (estado == null || estado.isEmpty) ? null : estado,
      'isbn': (isbn == null || isbn.trim().isEmpty) ? null : isbn.trim(),
      'pagina': pagina,
      'tamanio': tamanio,
    },
  );
  return Pagina.desdeJson(data['ejemplaresConLibro'] as Map<String, dynamic>, Ejemplar.desdeJson);
}

/// Búsqueda individual, criterio 1: el código del ejemplar.
Future<Ejemplar?> buscarEjemplarPorCodigo(String codigo) async {
  final data = await ClienteGraphQL().ejecutar(
    'query (\$codigo: ID!) { ejemplarPorCodigo(codigo: \$codigo) { $camposEjemplar } }',
    variables: {'codigo': codigo.trim()},
  );
  final ejemplar = data['ejemplarPorCodigo'];
  return ejemplar == null ? null : Ejemplar.desdeJson(ejemplar as Map<String, dynamic>);
}

/// Búsqueda individual, criterio 2: ISBN del libro + ubicación (puede haber varias copias).
Future<List<Ejemplar>> buscarEjemplaresPorLibroYUbicacion(String isbn, String ubicacion) async {
  final data = await ClienteGraphQL().ejecutar(
    '''query (\$isbn: String!, \$ubicacion: String!) {
         ejemplaresPorLibroYUbicacion(isbn: \$isbn, ubicacion: \$ubicacion) { $camposEjemplar }
       }''',
    variables: {'isbn': isbn.trim(), 'ubicacion': ubicacion.trim()},
  );
  return (data['ejemplaresPorLibroYUbicacion'] as List)
      .map((e) => Ejemplar.desdeJson(e as Map<String, dynamic>))
      .toList();
}

/// Número de revisión de la colección en el servidor (lo usa el Observer de Listar).
Future<int> consultarRevisionEjemplares() async {
  final data = await ClienteGraphQL().ejecutar('query { revisionEjemplares }');
  return data['revisionEjemplares'] as int;
}

Future<Ejemplar> crearEjemplar({
  required String codigo,
  required String estado,
  required String ubicacion,
  required String fechaIngreso,
  required double precioReposicion,
  required int numeroPrestamos,
  required String isbnLibro,
  String? observaciones,
}) async {
  final data = await ClienteGraphQL().ejecutar(
    'mutation (\$input: EjemplarInput!) { crearEjemplar(input: \$input) { $camposEjemplar } }',
    variables: {
      'input': {
        'codigo': codigo,
        'isbnLibro': isbnLibro,
        'estado': estado,
        'ubicacion': ubicacion,
        'fechaIngreso': fechaIngreso,
        'precioReposicion': precioReposicion,
        'numeroPrestamos': numeroPrestamos,
        'observaciones': observaciones,
        'version': null,
      },
    },
  );
  return Ejemplar.desdeJson(data['crearEjemplar'] as Map<String, dynamic>);
}

/// Un solo input para crear y actualizar: el código indica qué ejemplar se modifica.
/// numeroPrestamos no se envía (lo lleva el servidor) y [version] es la que se leyó al buscarlo.
Future<Ejemplar> actualizarEjemplar({
  required String codigo,
  required String isbnLibro,
  required String estado,
  required String ubicacion,
  required String fechaIngreso,
  required double precioReposicion,
  String? observaciones,
  int? version,
}) async {
  final data = await ClienteGraphQL().ejecutar(
    'mutation (\$input: EjemplarInput!) { actualizarEjemplar(input: \$input) { $camposEjemplar } }',
    variables: {
      'input': {
        'codigo': codigo,
        'isbnLibro': isbnLibro,
        'estado': estado,
        'ubicacion': ubicacion,
        'fechaIngreso': fechaIngreso,
        'precioReposicion': precioReposicion,
        'numeroPrestamos': null,
        'observaciones': observaciones,
        'version': version,
      },
    },
  );
  return Ejemplar.desdeJson(data['actualizarEjemplar'] as Map<String, dynamic>);
}

Future<Ejemplar> eliminarEjemplar(String codigo) async {
  final data = await ClienteGraphQL().ejecutar(
    'mutation (\$codigo: ID!) { eliminarEjemplar(codigo: \$codigo) { $camposEjemplar } }',
    variables: {'codigo': codigo},
  );
  return Ejemplar.desdeJson(data['eliminarEjemplar'] as Map<String, dynamic>);
}
