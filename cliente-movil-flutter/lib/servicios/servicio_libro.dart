import '../modelos/libro.dart';
import '../modelos/pagina.dart';
import '../modelos/resumen_inventario.dart';
import 'cliente_graphql.dart';

/// Todos los atributos del libro (incluidos los opcionales, el _id y la versión).
const String camposLibro = 'id isbn titulo autor precio numeroPaginas fechaPublicacion genero editorial sinopsis version';

const String _camposPagina = 'pagina tamanio totalElementos totalPaginas';

Libro? _aLibroOpcional(Object? json) => json == null ? null : Libro.desdeJson(json as Map<String, dynamic>);

/// Texto opcional de un formulario: null si quedó vacío.
String? textoONulo(String texto) => texto.trim().isEmpty ? null : texto.trim();

/// Listar libros, paginado. Sin filtro usa la query `libros` (todos); con filtro por género y/o
/// rango de precio usa la consulta personalizada `librosPorPrecioYGenero`, que filtra en MongoDB.
Future<Pagina<Libro>> listarLibros({
  required int pagina,
  required int tamanio,
  String? genero,
  double? precioMin,
  double? precioMax,
}) async {
  final campos = '$_camposPagina contenido { $camposLibro cantidadEjemplares }';
  final hayFiltro = (genero != null && genero.isNotEmpty) || precioMin != null || precioMax != null;
  if (!hayFiltro) {
    final data = await ClienteGraphQL().ejecutar(
      'query (\$pagina: Int!, \$tamanio: Int!) { libros(pagina: \$pagina, tamanio: \$tamanio) { $campos } }',
      variables: {'pagina': pagina, 'tamanio': tamanio},
    );
    return Pagina.desdeJson(data['libros'] as Map<String, dynamic>, Libro.desdeJson);
  }
  final data = await ClienteGraphQL().ejecutar(
    '''query (\$min: Float, \$max: Float, \$genero: Genero, \$pagina: Int!, \$tamanio: Int!) {
         librosPorPrecioYGenero(precioMin: \$min, precioMax: \$max, genero: \$genero, pagina: \$pagina, tamanio: \$tamanio) {
           $campos
         }
       }''',
    variables: {
      'min': precioMin,
      'max': precioMax,
      'genero': (genero == null || genero.isEmpty) ? null : genero,
      'pagina': pagina,
      'tamanio': tamanio,
    },
  );
  return Pagina.desdeJson(data['librosPorPrecioYGenero'] as Map<String, dynamic>, Libro.desdeJson);
}

Future<Libro?> buscarLibroPorIsbn(String isbn) async {
  final data = await ClienteGraphQL().ejecutar(
    'query (\$isbn: ID!) { libroPorIsbn(isbn: \$isbn) { $camposLibro cantidadEjemplares } }',
    variables: {'isbn': isbn.trim()},
  );
  return _aLibroOpcional(data['libroPorIsbn']);
}

Future<Libro?> buscarLibroPorTitulo(String titulo) async {
  final data = await ClienteGraphQL().ejecutar(
    'query (\$titulo: String!) { libroPorTitulo(titulo: \$titulo) { $camposLibro cantidadEjemplares } }',
    variables: {'titulo': titulo.trim()},
  );
  return _aLibroOpcional(data['libroPorTitulo']);
}

/// Maestro + detalle: el libro, sus ejemplares y cuántos hay en cada estado.
Future<DetalleLibro?> consultarDetalleLibro(String isbn) async {
  final data = await ClienteGraphQL().ejecutar(
    '''query (\$isbn: ID!) {
         detalleLibro(isbn: \$isbn) {
           libro { $camposLibro }
           ejemplares { id codigo libroId isbnLibro estado ubicacion fechaIngreso precioReposicion numeroPrestamos observaciones version }
           conteoPorEstado { estado cantidad porcentaje }
           totalEjemplares totalPrestamos valorReposicion
         }
       }''',
    variables: {'isbn': isbn.trim()},
  );
  final detalle = data['detalleLibro'];
  return detalle == null ? null : DetalleLibro.desdeJson(detalle as Map<String, dynamic>);
}

/// Número de revisión de la colección en el servidor (lo usa el Observer de Listar).
Future<int> consultarRevisionLibros() async {
  final data = await ClienteGraphQL().ejecutar('query { revisionLibros }');
  return data['revisionLibros'] as int;
}

Map<String, Object?> _input({
  required String isbn,
  required String titulo,
  required String autor,
  required double precio,
  required int numeroPaginas,
  required String fechaPublicacion,
  required String genero,
  String? editorial,
  String? sinopsis,
  int? version,
}) => {
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
};

Future<Libro> crearLibro({
  required String isbn,
  required String titulo,
  required String autor,
  required double precio,
  required int numeroPaginas,
  required String fechaPublicacion,
  required String genero,
  String? editorial,
  String? sinopsis,
}) async {
  final data = await ClienteGraphQL().ejecutar(
    'mutation (\$input: LibroInput!) { crearLibro(input: \$input) { $camposLibro } }',
    variables: {
      'input': _input(
        isbn: isbn,
        titulo: titulo,
        autor: autor,
        precio: precio,
        numeroPaginas: numeroPaginas,
        fechaPublicacion: fechaPublicacion,
        genero: genero,
        editorial: editorial,
        sinopsis: sinopsis,
      ),
    },
  );
  return Libro.desdeJson(data['crearLibro'] as Map<String, dynamic>);
}

/// El isbn va dentro del input: indica qué libro se modifica (no se cambia). [version] es la que
/// se leyó al buscarlo: si otro usuario lo cambió entretanto, el servidor rechaza la actualización.
Future<Libro> actualizarLibro({
  required String isbn,
  required String titulo,
  required String autor,
  required double precio,
  required int numeroPaginas,
  required String fechaPublicacion,
  required String genero,
  String? editorial,
  String? sinopsis,
  int? version,
}) async {
  final data = await ClienteGraphQL().ejecutar(
    'mutation (\$input: LibroInput!) { actualizarLibro(input: \$input) { $camposLibro } }',
    variables: {
      'input': _input(
        isbn: isbn,
        titulo: titulo,
        autor: autor,
        precio: precio,
        numeroPaginas: numeroPaginas,
        fechaPublicacion: fechaPublicacion,
        genero: genero,
        editorial: editorial,
        sinopsis: sinopsis,
        version: version,
      ),
    },
  );
  return Libro.desdeJson(data['actualizarLibro'] as Map<String, dynamic>);
}

Future<Libro> eliminarLibro(String isbn) async {
  final data = await ClienteGraphQL().ejecutar(
    'mutation (\$isbn: ID!) { eliminarLibro(isbn: \$isbn) { $camposLibro } }',
    variables: {'isbn': isbn},
  );
  return Libro.desdeJson(data['eliminarLibro'] as Map<String, dynamic>);
}
