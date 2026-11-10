import 'ejemplar.dart';
import 'libro.dart';

/// Cuántos ejemplares hay en un estado y qué porcentaje (0 a 100) del inventario
/// representan. Lo calcula el servidor; la app solo lo muestra.
class ConteoEstado {
  final String estado;
  final int cantidad;
  final double porcentaje;

  ConteoEstado({required this.estado, required this.cantidad, required this.porcentaje});

  factory ConteoEstado.desdeJson(Map<String, dynamic> json) => ConteoEstado(
    estado: json['estado'] as String,
    cantidad: json['cantidad'] as int,
    porcentaje: (json['porcentaje'] as num).toDouble(),
  );
}

/// Cómo terminó una de las 3 tareas concurrentes del resumen en el servidor.
class ResultadoTarea {
  final String nombre;
  final String hilo;
  final int duracionMs;
  final bool exitosa;

  ResultadoTarea({required this.nombre, required this.hilo, required this.duracionMs, required this.exitosa});

  factory ResultadoTarea.desdeJson(Map<String, dynamic> json) => ResultadoTarea(
    nombre: json['nombre'] as String,
    hilo: json['hilo'] as String,
    duracionMs: json['duracionMs'] as int,
    exitosa: json['exitosa'] as bool,
  );
}

/// Resumen del inventario para la ventana principal (query resumenInventario).
class ResumenInventario {
  final int totalLibros;
  final int totalEjemplares;
  final List<ConteoEstado> ejemplaresPorEstado;
  final double valorInventario;
  final int duracionMs;
  final List<ResultadoTarea> tareas;

  ResumenInventario({
    required this.totalLibros,
    required this.totalEjemplares,
    required this.ejemplaresPorEstado,
    this.valorInventario = 0,
    this.duracionMs = 0,
    this.tareas = const [],
  });

  factory ResumenInventario.desdeJson(Map<String, dynamic> json) => ResumenInventario(
    totalLibros: json['totalLibros'] as int,
    totalEjemplares: json['totalEjemplares'] as int,
    ejemplaresPorEstado: (json['ejemplaresPorEstado'] as List)
        .map((c) => ConteoEstado.desdeJson(c as Map<String, dynamic>))
        .toList(),
    valorInventario: ((json['valorInventario'] as num?) ?? 0).toDouble(),
    duracionMs: (json['duracionMs'] as int?) ?? 0,
    tareas: ((json['tareas'] as List?) ?? const [])
        .map((t) => ResultadoTarea.desdeJson(t as Map<String, dynamic>))
        .toList(),
  );
}

/// Maestro + detalle: un libro, sus ejemplares y cuántos hay en cada estado (query detalleLibro).
class DetalleLibro {
  final Libro libro;
  final List<Ejemplar> ejemplares;
  final List<ConteoEstado> conteoPorEstado;
  final int totalEjemplares;
  final int totalPrestamos;
  final double valorReposicion;

  DetalleLibro({
    required this.libro,
    required this.ejemplares,
    required this.conteoPorEstado,
    required this.totalEjemplares,
    required this.totalPrestamos,
    required this.valorReposicion,
  });

  factory DetalleLibro.desdeJson(Map<String, dynamic> json) => DetalleLibro(
    libro: Libro.desdeJson(json['libro'] as Map<String, dynamic>),
    ejemplares: (json['ejemplares'] as List).map((e) => Ejemplar.desdeJson(e as Map<String, dynamic>)).toList(),
    conteoPorEstado: (json['conteoPorEstado'] as List)
        .map((c) => ConteoEstado.desdeJson(c as Map<String, dynamic>))
        .toList(),
    totalEjemplares: json['totalEjemplares'] as int,
    totalPrestamos: json['totalPrestamos'] as int,
    valorReposicion: (json['valorReposicion'] as num).toDouble(),
  );
}
