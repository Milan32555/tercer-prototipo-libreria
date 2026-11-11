import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/ejemplar.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_ejemplar.dart';
import '../../tema/tema.dart';
import '../../utilidades/validaciones.dart';
import '../comunes/componentes.dart';
import '../comunes/fichas.dart';

enum _Criterio { codigo, libro }

/// Caso de uso: Consultar Ejemplar con dos criterios: por su código (un solo ejemplar) o por el
/// ISBN de su libro + la ubicación (si hay varias copias del libro en ese lugar, se muestran todas).
class ConsultarEjemplar extends StatefulWidget {
  const ConsultarEjemplar({super.key});

  @override
  State<ConsultarEjemplar> createState() => _ConsultarEjemplarState();
}

class _ConsultarEjemplarState extends State<ConsultarEjemplar> {
  final _formLibro = GlobalKey<FormState>();
  final _codigo = TextEditingController();
  final _isbn = TextEditingController();
  final _ubicacion = TextEditingController();
  _Criterio _criterio = _Criterio.codigo;
  List<Ejemplar> _encontrados = [];
  String? _sinResultado;
  bool _cargando = false;
  String? _error;

  @override
  void dispose() {
    for (final c in [_codigo, _isbn, _ubicacion]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _ejecutar(Future<List<Ejemplar>> Function() busqueda, String mensajeVacio) async {
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _cargando = true;
      _error = null;
      _encontrados = [];
      _sinResultado = null;
    });
    try {
      final resultado = await busqueda();
      if (!mounted) return;
      setState(() {
        _encontrados = resultado;
        if (resultado.isEmpty) _sinResultado = mensajeVacio;
      });
    } on ErrorGraphQL catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  void _buscarPorCodigo() {
    final codigo = _codigo.text.trim();
    _ejecutar(() async {
      final ejemplar = await buscarEjemplarPorCodigo(codigo);
      return ejemplar == null ? <Ejemplar>[] : [ejemplar];
    }, 'No hay ningún ejemplar con el código «$codigo».');
  }

  void _buscarPorLibro() {
    if (!_formLibro.currentState!.validate()) return;
    _ejecutar(
      () => buscarEjemplaresPorLibroYUbicacion(_isbn.text, _ubicacion.text),
      'No hay ejemplares del libro «${_isbn.text.trim()}» en «${_ubicacion.text.trim()}».',
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ejemplares')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e4, Espacio.e4, Espacio.e8),
        children: [
          const EncabezadoPantalla(
            titulo: 'Consultar ejemplar',
            descripcion: 'Busca una copia por su código, o por el ISBN de su libro y la ubicación.',
          ),
          SegmentedButton<_Criterio>(
            segments: const [
              ButtonSegment(value: _Criterio.codigo, label: Text('Por código')),
              ButtonSegment(value: _Criterio.libro, label: Text('Por libro y ubicación')),
            ],
            selected: {_criterio},
            showSelectedIcon: false,
            onSelectionChanged: (s) => setState(() {
              _criterio = s.first;
              _encontrados = [];
              _sinResultado = null;
              _error = null;
            }),
          ),
          const SizedBox(height: Espacio.e4),
          if (_criterio == _Criterio.codigo)
            BuscadorClave(
              controller: _codigo,
              etiqueta: 'Código del ejemplar',
              pista: 'EJ-001',
              onBuscar: _buscarPorCodigo,
              cargando: _cargando,
            )
          else
            Form(
              key: _formLibro,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextFormField(
                    controller: _isbn,
                    decoration: const InputDecoration(labelText: 'ISBN del libro', hintText: '1001'),
                    autocorrect: false,
                    validator: validarObligatorio,
                  ),
                  const SizedBox(height: Espacio.e3),
                  TextFormField(
                    controller: _ubicacion,
                    decoration: const InputDecoration(labelText: 'Ubicación', hintText: 'Estante A1'),
                    validator: validarObligatorio,
                    onFieldSubmitted: (_) => _buscarPorLibro(),
                  ),
                  const SizedBox(height: Espacio.e3),
                  FilledButton.icon(
                    onPressed: _cargando ? null : _buscarPorLibro,
                    icon: const Icon(LucideIcons.search, size: 18),
                    label: const Text('Buscar'),
                  ),
                ],
              ),
            ),
          if (_error != null) Aviso(_error!, titulo: 'No se pudo consultar'),
          if (_sinResultado != null)
            EstadoVacio(icono: LucideIcons.searchX, titulo: 'Sin resultados', descripcion: _sinResultado),
          if (_encontrados.length > 1)
            Padding(
              padding: const EdgeInsets.only(top: Espacio.e4),
              child: Text('${_encontrados.length} ejemplares de ese libro en esa ubicación',
                  style: const TextStyle(fontWeight: FontWeight.w600)),
            ),
          for (final ejemplar in _encontrados) FichaEjemplar(ejemplar: ejemplar),
        ],
      ),
    );
  }
}
