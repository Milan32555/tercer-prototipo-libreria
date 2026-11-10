/// Géneros literarios soportados por el servidor (enum Genero en el schema GraphQL).
const List<String> generos = ['FICCION', 'NO_FICCION', 'CIENCIA', 'HISTORIA', 'INFANTIL', 'POESIA', 'OTRO'];

const Map<String, String> _etiquetasGenero = {
  'FICCION': 'Ficción',
  'NO_FICCION': 'No ficción',
  'CIENCIA': 'Ciencia',
  'HISTORIA': 'Historia',
  'INFANTIL': 'Infantil',
  'POESIA': 'Poesía',
  'OTRO': 'Otro',
};

/// Nombre legible del género para la interfaz (al servidor se envía el valor del enum).
String etiquetaGenero(String genero) => _etiquetasGenero[genero] ?? genero;

/// Objeto maestro: Libro (documento de la colección "libros" en MongoDB).
class Libro {
  /// Llave primaria: _id (ObjectId) del documento.
  final String? id;
  final String isbn;
  final String titulo;
  final String autor;
  final double precio;
  final int numeroPaginas;
  final String fechaPublicacion; // ISO-8601, p.ej. 2024-05-10T14:30:00
  final String genero;

  /// Opcional.
  final String? editorial;

  /// Opcional (máximo 500 caracteres).
  final String? sinopsis;

  /// Versión del documento (concurrencia optimista): se devuelve al actualizar.
  final int? version;

  /// Cuántos ejemplares tiene (lo cuenta el servidor).
  final int? cantidadEjemplares;

  Libro({
    this.id,
    required this.isbn,
    required this.titulo,
    required this.autor,
    required this.precio,
    required this.numeroPaginas,
    required this.fechaPublicacion,
    required this.genero,
    this.editorial,
    this.sinopsis,
    this.version,
    this.cantidadEjemplares,
  });

  factory Libro.desdeJson(Map<String, dynamic> json) => Libro(
    id: json['id'] as String?,
    isbn: json['isbn'] as String,
    titulo: json['titulo'] as String,
    autor: json['autor'] as String,
    precio: (json['precio'] as num).toDouble(),
    numeroPaginas: json['numeroPaginas'] as int,
    fechaPublicacion: json['fechaPublicacion'] as String,
    genero: json['genero'] as String,
    editorial: json['editorial'] as String?,
    sinopsis: json['sinopsis'] as String?,
    version: json['version'] as int?,
    cantidadEjemplares: json['cantidadEjemplares'] as int?,
  );
}
