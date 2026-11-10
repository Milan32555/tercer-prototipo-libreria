/// Estados posibles de un Ejemplar (enum EstadoEjemplar en el schema GraphQL).
const List<String> estadosEjemplar = ['DISPONIBLE', 'PRESTADO', 'DANIADO', 'DADO_DE_BAJA'];

const Map<String, String> _etiquetasEstado = {
  'DISPONIBLE': 'Disponible',
  'PRESTADO': 'Prestado',
  'DANIADO': 'Dañado',
  'DADO_DE_BAJA': 'Dado de baja',
};

/// Nombre legible del estado para la interfaz (al servidor se envía el valor del enum).
String etiquetaEstado(String estado) => _etiquetasEstado[estado] ?? estado;

/// Objeto detalle: Ejemplar (documento de la colección "ejemplares"). Pertenece a un Libro:
/// libroId es el _id del libro (llave foránea); la app trabaja con el ISBN del libro.
class Ejemplar {
  /// Llave primaria: _id (ObjectId) del documento.
  final String? id;
  final String codigo;

  /// Llave foránea: _id del libro.
  final String? libroId;
  final String isbnLibro;
  final String estado;
  final String ubicacion;
  final String fechaIngreso;
  final double precioReposicion;
  final int numeroPrestamos;

  /// Opcional.
  final String? observaciones;
  final int? version;

  /// Título y autor del libro (los trae la consulta con el libro o el $lookup de Listar).
  final String? tituloLibro;
  final String? autorLibro;

  Ejemplar({
    this.id,
    required this.codigo,
    this.libroId,
    required this.isbnLibro,
    required this.estado,
    required this.ubicacion,
    required this.fechaIngreso,
    required this.precioReposicion,
    required this.numeroPrestamos,
    this.observaciones,
    this.version,
    this.tituloLibro,
    this.autorLibro,
  });

  /// Sirve para el tipo Ejemplar (con `libro { titulo autor }`) y para EjemplarConLibro
  /// (con `tituloLibro` y `autorLibro`).
  factory Ejemplar.desdeJson(Map<String, dynamic> json) {
    final libro = json['libro'] as Map<String, dynamic>?;
    return Ejemplar(
      id: json['id'] as String?,
      codigo: json['codigo'] as String,
      libroId: json['libroId'] as String?,
      isbnLibro: (json['isbnLibro'] as String?) ?? '',
      estado: json['estado'] as String,
      ubicacion: json['ubicacion'] as String,
      fechaIngreso: json['fechaIngreso'] as String,
      precioReposicion: (json['precioReposicion'] as num).toDouble(),
      numeroPrestamos: json['numeroPrestamos'] as int,
      observaciones: json['observaciones'] as String?,
      version: json['version'] as int?,
      tituloLibro: (json['tituloLibro'] as String?) ?? libro?['titulo'] as String?,
      autorLibro: (json['autorLibro'] as String?) ?? libro?['autor'] as String?,
    );
  }
}
