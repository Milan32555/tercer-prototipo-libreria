import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/ejemplar.dart';
import '../../observador/listado_observable.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_ejemplar.dart';
import '../../tema/tema.dart';
import '../../utilidades/formato.dart';
import '../comunes/componentes.dart';
import '../comunes/tabla_datos.dart';

const _tamanioPagina = 10;
const _gris = TextStyle(color: kGris600);

/// TODOS los atributos del ejemplar + título y autor de su libro (colección A).
final _columnas = <ColumnaTabla<Ejemplar>>[
  ColumnaTabla(titulo: 'Código', ancho: 104, celda: (e) => Text(e.codigo, style: const TextStyle(fontWeight: FontWeight.w600))),
  ColumnaTabla(titulo: 'Título del libro', ancho: 176, celda: (e) => Text(
    e.tituloLibro ?? '—',
    maxLines: 1,
    overflow: TextOverflow.ellipsis,
    style: const TextStyle(fontFamily: fuenteLibro, fontSize: 15, fontWeight: FontWeight.w500, color: kGris900),
  )),
  ColumnaTabla(titulo: 'Autor del libro', ancho: 168, celda: (e) => Text(e.autorLibro ?? '—')),
  ColumnaTabla(titulo: 'ISBN del libro', ancho: 150, celda: (e) => Text(e.isbnLibro, style: _gris)),
  ColumnaTabla(titulo: 'Estado', ancho: 136, celda: (e) => InsigniaEstado(e.estado)),
  ColumnaTabla(titulo: 'Ubicación', ancho: 116, celda: (e) => Text(e.ubicacion)),
  ColumnaTabla(titulo: 'Fecha de ingreso', ancho: 150, celda: (e) => Text(formatearFechaIso(e.fechaIngreso), style: _gris)),
  ColumnaTabla(titulo: 'Préstamos', ancho: 96, numerica: true, celda: (e) => Text('${e.numeroPrestamos}')),
  ColumnaTabla(titulo: 'Reposición', ancho: 112, numerica: true, celda: (e) => Text(formatearPrecio(e.precioReposicion))),
  ColumnaTabla(titulo: 'Observaciones', ancho: 180, celda: (e) => Text(e.observaciones ?? '—', style: _gris, overflow: TextOverflow.ellipsis)),
  ColumnaTabla(titulo: 'Versión', ancho: 80, numerica: true, celda: (e) => Text('${e.version ?? '—'}')),
  ColumnaTabla(titulo: 'ID (_id)', ancho: 210, celda: (e) => Text(e.id ?? '', style: const TextStyle(fontSize: 12, color: kGris600))),
  ColumnaTabla(titulo: 'ID del libro (libroId)', ancho: 210, celda: (e) => Text(e.libroId ?? '', style: const TextStyle(fontSize: 12, color: kGris600))),
];

/// Caso de uso: Listar Ejemplares. Usa la consulta personalizada ejemplaresConLibro ($lookup en
/// MongoDB): cada fila trae TODOS los atributos del ejemplar más el título y el autor de su
/// libro. Filtro por estado y/o ISBN del libro y paginación en el servidor. Observer igual que
/// en Listar Libros.
class ListarEjemplares extends StatefulWidget {
  const ListarEjemplares({super.key});

  @override
  State<ListarEjemplares> createState() => _ListarEjemplaresState();
}

class _ListarEjemplaresState extends State<ListarEjemplares> {
  final _isbnLibro = TextEditingController();
  String? _estado;
  List<Ejemplar> _ejemplares = [];
  int _total = 0;
  int _pagina = 0;
  int _totalPaginas = 0;
  bool _cargando = true;
  bool _cargado = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
    ejemplarObservable.suscribir(_cargar); // Observer
  }

  @override
  void dispose() {
    ejemplarObservable.desuscribir(_cargar);
    _isbnLibro.dispose();
    super.dispose();
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      var resultado = await listarEjemplaresConLibro(
        pagina: _pagina,
        tamanio: _tamanioPagina,
        estado: _estado,
        isbn: _isbnLibro.text,
      );
      if (resultado.contenido.isEmpty && _pagina > 0 && resultado.totalPaginas > 0) {
        _pagina = resultado.totalPaginas - 1;
        resultado = await listarEjemplaresConLibro(
          pagina: _pagina,
          tamanio: _tamanioPagina,
          estado: _estado,
          isbn: _isbnLibro.text,
        );
      }
      if (mounted) {
        setState(() {
          _ejemplares = resultado.contenido;
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
    _pagina = 0;
    _cargar();
  }

  void _limpiar() {
    _isbnLibro.clear();
    setState(() => _estado = null);
    _pagina = 0;
    _cargar();
  }

  void _irAPagina(int pagina) {
    _pagina = pagina;
    _cargar();
  }

  bool get _hayFiltros => _isbnLibro.text.trim().isNotEmpty || _estado != null;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Listar ejemplares')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.pushNamed(context, '/ejemplares/agregar'),
        icon: const Icon(LucideIcons.plus, size: 20),
        label: const Text('Adicionar ejemplar'),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e3, Espacio.e4, Espacio.e2),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SearchBar(
                  controller: _isbnLibro,
                  hintText: 'Filtrar por ISBN del libro',
                  leading: const Icon(LucideIcons.search, size: 18, color: kGris500),
                  trailing: [
                    if (_hayFiltros)
                      IconButton(tooltip: 'Limpiar filtros', onPressed: _limpiar, icon: const Icon(LucideIcons.x, size: 18)),
                    IconButton(tooltip: 'Filtrar', onPressed: _filtrar, icon: const Icon(LucideIcons.arrowRight, size: 18)),
                  ],
                  textInputAction: TextInputAction.search,
                  onSubmitted: (_) => _filtrar(),
                ),
                const SizedBox(height: Espacio.e3),
                FiltroChips(
                  opciones: estadosEjemplar,
                  seleccionado: _estado,
                  etiquetaDe: etiquetaEstado,
                  textoTodos: 'Todos',
                  adorno: puntoEstado,
                  alSeleccionar: (estado) {
                    setState(() => _estado = estado);
                    _filtrar();
                  },
                ),
                const SizedBox(height: Espacio.e3),
                ConteoServidor(texto: _total == 1 ? '1 ejemplar' : '$_total ejemplares'),
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
                    child: _ejemplares.isEmpty
                        ? (_cargado && _error == null ? _vacio() : const SizedBox.shrink())
                        : TablaDatos<Ejemplar>(etiqueta: 'Ejemplares del inventario', columnas: _columnas, filas: _ejemplares),
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
          titulo: 'Ningún ejemplar coincide con el filtro',
          descripcion: 'Prueba con otro estado o revisa el ISBN.',
          accion: OutlinedButton(onPressed: _limpiar, child: const Text('Limpiar filtros')),
        )
      : const EstadoVacio(
          icono: LucideIcons.library,
          titulo: 'Todavía no hay ejemplares',
          descripcion: 'Las copias que registres aparecerán aquí.',
        );
}
