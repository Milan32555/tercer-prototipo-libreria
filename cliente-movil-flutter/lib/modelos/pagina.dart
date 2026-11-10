/// Una página de resultados de un Listar (la paginación la hace el servidor en MongoDB).
/// La página empieza en 0.
class Pagina<T> {
  final List<T> contenido;
  final int pagina;
  final int tamanio;
  final int totalElementos;
  final int totalPaginas;

  Pagina({
    required this.contenido,
    required this.pagina,
    required this.tamanio,
    required this.totalElementos,
    required this.totalPaginas,
  });

  factory Pagina.desdeJson(Map<String, dynamic> json, T Function(Map<String, dynamic>) convertir) => Pagina(
    contenido: (json['contenido'] as List).map((e) => convertir(e as Map<String, dynamic>)).toList(),
    pagina: json['pagina'] as int,
    tamanio: json['tamanio'] as int,
    totalElementos: json['totalElementos'] as int,
    totalPaginas: json['totalPaginas'] as int,
  );
}
