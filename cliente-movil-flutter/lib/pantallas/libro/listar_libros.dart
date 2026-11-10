import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/libro.dart';
import '../../observador/listado_observable.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_libro.dart';
import '../../tema/tema.dart';
import '../../utilidades/formato.dart';
import '../comunes/componentes.dart';
import '../comunes/tabla_datos.dart';

const _tamanioPagina = 10;

final _columnas = <ColumnaTabla<Libro>>[
  ColumnaTabla(titulo: 'Título', ancho: 176, celda: (l) => CeldaDoble(principal: l.titulo, secundario: l.autor, esLibro: true)),
  ColumnaTabla(titulo: 'Género', ancho: 120, celda: (l) => InsigniaGenero(l.genero)),
  ColumnaTabla(titulo: 'ISBN', ancho: 164, celda: (l) => Text(l.isbn, style: const TextStyle(color: kGris600))),
  ColumnaTabla(titulo: 'Editorial', ancho: 160, celda: (l) => Text(l.editorial ?? '—', style: const TextStyle(color: kGris600))),
  ColumnaTabla(titulo: 'Publicación', ancho: 112, celda: (l) => Text(formatearFechaCortaIso(l.fechaPublicacion), style: const TextStyle(color: kGris600))),
  ColumnaTabla(titulo: 'Páginas', ancho: 84, numerica: true, celda: (l) => Text('${l.numeroPaginas}')),
  ColumnaTabla(titulo: 'Ejemplares', ancho: 100, numerica: true, celda: (l) => Text('${l.cantidadEjemplares ?? '—'}')),
  ColumnaTabla(titulo: 'Precio', ancho: 104, numerica: true, celda: (l) => Text(formatearPrecio(l.precio))),
];

/// Caso de uso: Listar Libros en una grilla paginada. Sin filtro muestra todos (query libros); con
/// filtro por género y/o rango de precio usa la consulta personalizada librosPorPrecioYGenero. El
/// filtro, el total y la paginación los resuelve el servidor en MongoDB.
///
/// Patrón Observer: la pantalla se SUSCRIBE a `libroObservable` en initState y se desuscribe en
/// dispose. Cada notificación (un cambio en esta app o en otro cliente) vuelve a pedir la página.
class ListarLibros extends StatefulWidget {
  const ListarLibros({super.key});

  @override
  State<ListarLibros> createState() => _ListarLibrosState();
}

class _ListarLibrosState extends State<ListarLibros> {
  final _precioMin = TextEditingController();
  final _precioMax = TextEditingController();
  String? _genero;
  List<Libro> _libros = [];
  int _total = 0;
  int _pagina = 0;
  int _totalPaginas = 0;
  bool _cargando = true;
  bool _cargado = false;
  String? _error;
  String? _errorFiltro;

  @override
  void initState() {
    super.initState();
    _cargar();
    libroObservable.suscribir(_cargar); // Observer
  }

  @override
  void dispose() {
    libroObservable.desuscribir(_cargar); // evita fugas de memoria
    _precioMin.dispose();
    _precioMax.dispose();
    super.dispose();
  }

  double? _numero(TextEditingController c) => c.text.trim().isEmpty ? null : double.tryParse(c.text.trim());

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      var resultado = await listarLibros(
        pagina: _pagina,
        tamanio: _tamanioPagina,
        genero: _genero,
        precioMin: _numero(_precioMin),
        precioMax: _numero(_precioMax),
      );
      // Si se borraron libros y la página quedó vacía, se vuelve a la última que existe.
      if (resultado.contenido.isEmpty && _pagina > 0 && resultado.totalPaginas > 0) {
        _pagina = resultado.totalPaginas - 1;
        resultado = await listarLibros(
          pagina: _pagina,
          tamanio: _tamanioPagina,
          genero: _genero,
          precioMin: _numero(_precioMin),
          precioMax: _numero(_precioMax),
        );
      }
      if (mounted) {
        setState(() {
          _libros = resultado.contenido;
          _total = resultado.totalElementos;
          _totalPaginas = resultado.totalPaginas;
        });
      }
    } on ErrorGraphQL catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) {
        setState(() {
          _cargando = false;
          _cargado = true;
        });
      }
    }
  }

  void _filtrar() {
    FocusManager.instance.primaryFocus?.unfocus();
    final textoMin = _precioMin.text.trim();
    final textoMax = _precioMax.text.trim();
    final min = _numero(_precioMin);
    final max = _numero(_precioMax);
    String? error;
    if ((textoMin.isNotEmpty && (min == null || min < 0)) || (textoMax.isNotEmpty && (max == null || max < 0))) {
      error = 'Los precios deben ser números mayores o iguales a cero.';
    } else if (min != null && max != null && min > max) {
      error = 'El precio mínimo no puede ser mayor que el máximo.';
    }
    setState(() => _errorFiltro = error);
    if (error != null) return;
    _pagina = 0;
    _cargar();
  }

  void _limpiar() {
    _precioMin.clear();
    _precioMax.clear();
    setState(() {
      _genero = null;
      _errorFiltro = null;
    });
    _pagina = 0;
    _cargar();
  }

  void _irAPagina(int pagina) {
    _pagina = pagina;
    _cargar();
  }

  bool get _hayFiltros => _precioMin.text.trim().isNotEmpty || _precioMax.text.trim().isNotEmpty || _genero != null;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Listar libros')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.pushNamed(context, '/libros/agregar'),
        icon: const Icon(LucideIcons.plus, size: 20),
        label: const Text('Adicionar libro'),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e3, Espacio.e4, Espacio.e2),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _precioMin,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(labelText: 'Precio mínimo', prefixText: '\$ ', isDense: true),
                        onSubmitted: (_) => _filtrar(),
                      ),
                    ),
                    const SizedBox(width: Espacio.e2),
                    Expanded(
                      child: TextField(
                        controller: _precioMax,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(labelText: 'Precio máximo', prefixText: '\$ ', isDense: true),
                        onSubmitted: (_) => _filtrar(),
                      ),
                    ),
                    const SizedBox(width: Espacio.e1),
                    IconButton.filledTonal(
                      tooltip: 'Filtrar',
                      onPressed: _filtrar,
                      icon: const Icon(LucideIcons.search, size: 18),
                    ),
                    if (_hayFiltros)
                      IconButton(tooltip: 'Limpiar filtros', onPressed: _limpiar, icon: const Icon(LucideIcons.x, size: 18)),
                  ],
                ),
                if (_errorFiltro != null)
                  Padding(
                    padding: const EdgeInsets.only(top: Espacio.e1),
                    child: Text(_errorFiltro!, style: const TextStyle(color: kPeligro600, fontSize: 12)),
                  ),
                const SizedBox(height: Espacio.e3),
                FiltroChips(
                  opciones: generos,
                  seleccionado: _genero,
                  etiquetaDe: etiquetaGenero,
                  textoTodos: 'Todos',
                  alSeleccionar: (genero) {
                    setState(() => _genero = genero);
                    _filtrar();
                  },
                ),
                const SizedBox(height: Espacio.e3),
                ConteoServidor(texto: _total == 1 ? '1 libro' : '$_total libros'),
              ],
            ),
          ),
          SizedBox(height: 2, child: _cargando ? const LinearProgressIndicator() : null),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: Espacio.e4),
              child: Aviso(
                _error!,
                titulo: 'No se pudo cargar la lista',
                accion: OutlinedButton.icon(
                  onPressed: _cargar,
                  icon: const Icon(LucideIcons.rotateCw, size: 16),
                  label: const Text('Reintentar'),
                ),
              ),
            ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e2, Espacio.e4, 80),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    child: _libros.isEmpty
                        ? (_cargado && _error == null ? _vacio() : const SizedBox.shrink())
                        : TablaDatos<Libro>(etiqueta: 'Libros del inventario', columnas: _columnas, filas: _libros),
                  ),
                  Paginador(pagina: _pagina, totalPaginas: _totalPaginas, alCambiar: _irAPagina, ocupado: _cargando),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _vacio() => _hayFiltros
      ? EstadoVacio(
          icono: LucideIcons.filterX,
          titulo: 'Ningún libro coincide con el filtro',
          descripcion: 'Prueba con otro género o con otro rango de precio.',
          accion: OutlinedButton(onPressed: _limpiar, child: const Text('Limpiar filtros')),
        )
      : const EstadoVacio(
          icono: LucideIcons.bookOpen,
          titulo: 'Todavía no hay libros',
          descripcion: 'Los libros que adiciones aparecerán aquí.',
        );
}
