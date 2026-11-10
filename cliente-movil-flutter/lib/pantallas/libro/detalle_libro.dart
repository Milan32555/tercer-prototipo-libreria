import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/ejemplar.dart';
import '../../modelos/resumen_inventario.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_libro.dart';
import '../../tema/tema.dart';
import '../../utilidades/formato.dart';
import '../comunes/componentes.dart';
import '../comunes/fichas.dart';
import '../comunes/tabla_datos.dart';

final _columnas = <ColumnaTabla<Ejemplar>>[
  ColumnaTabla(titulo: 'Código', ancho: 104, celda: (e) => Text(e.codigo, style: const TextStyle(fontWeight: FontWeight.w600))),
  ColumnaTabla(titulo: 'Estado', ancho: 136, celda: (e) => InsigniaEstado(e.estado)),
  ColumnaTabla(titulo: 'Ubicación', ancho: 116, celda: (e) => Text(e.ubicacion)),
  ColumnaTabla(titulo: 'Fecha de ingreso', ancho: 150, celda: (e) => Text(formatearFechaIso(e.fechaIngreso), style: const TextStyle(color: kGris600))),
  ColumnaTabla(titulo: 'Préstamos', ancho: 96, numerica: true, celda: (e) => Text('${e.numeroPrestamos}')),
  ColumnaTabla(titulo: 'Reposición', ancho: 112, numerica: true, celda: (e) => Text(formatearPrecio(e.precioReposicion))),
  ColumnaTabla(titulo: 'Observaciones', ancho: 180, celda: (e) => Text(e.observaciones ?? '—', style: const TextStyle(color: kGris600))),
];

/// Caso de uso: Detalle de un libro (consulta propia que muestra datos del MAESTRO y del DETALLE):
/// el libro con toda su información, sus ejemplares y cuántos hay en cada estado. Lo arma el
/// servidor con la query detalleLibro.
class DetalleLibroPantalla extends StatefulWidget {
  const DetalleLibroPantalla({super.key});

  @override
  State<DetalleLibroPantalla> createState() => _DetalleLibroPantallaState();
}

class _DetalleLibroPantallaState extends State<DetalleLibroPantalla> {
  final _isbn = TextEditingController();
  DetalleLibro? _detalle;
  String? _sinResultado;
  bool _cargando = false;
  String? _error;

  @override
  void dispose() {
    _isbn.dispose();
    super.dispose();
  }

  Future<void> _buscar() async {
    FocusManager.instance.primaryFocus?.unfocus();
    final isbn = _isbn.text.trim();
    setState(() {
      _cargando = true;
      _error = null;
      _detalle = null;
      _sinResultado = null;
    });
    try {
      final resultado = await consultarDetalleLibro(isbn);
      if (!mounted) return;
      setState(() {
        _detalle = resultado;
        if (resultado == null) _sinResultado = 'No hay ningún libro con el ISBN «$isbn».';
      });
    } on ErrorGraphQL catch (e) {
      if (mounted) setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final detalle = _detalle;
    return Scaffold(
      appBar: AppBar(title: const Text('Libros')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Espacio.e4, Espacio.e4, Espacio.e4, Espacio.e8),
        children: [
          const EncabezadoPantalla(
            titulo: 'Detalle del libro',
            descripcion: 'Un libro (maestro) con todos sus ejemplares (detalle) y cuántos hay en cada estado.',
          ),
          BuscadorClave(
            controller: _isbn,
            etiqueta: 'ISBN del libro',
            pista: '978-0307474728',
            onBuscar: _buscar,
            cargando: _cargando,
          ),
          if (_error != null) Aviso(_error!, titulo: 'No se pudo consultar'),
          if (_sinResultado != null)
            EstadoVacio(icono: LucideIcons.searchX, titulo: 'Sin resultados', descripcion: _sinResultado),
          if (detalle != null) ...[
            FichaLibro(libro: detalle.libro, titulo: 'Maestro: libro'),
            const SizedBox(height: Espacio.e4),
            Panel(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Semantics(header: true, child: Text('Resumen de sus ejemplares', style: Theme.of(context).textTheme.titleSmall)),
                  const SizedBox(height: Espacio.e3),
                  Wrap(
                spacing: Espacio.e6,
                runSpacing: Espacio.e3,
                children: [
                  _Cifra(etiqueta: 'Ejemplares', valor: '${detalle.totalEjemplares}'),
                  _Cifra(etiqueta: 'Préstamos acumulados', valor: '${detalle.totalPrestamos}'),
                  _Cifra(etiqueta: 'Valor de reposición', valor: formatearPrecio(detalle.valorReposicion)),
                  for (final c in detalle.conteoPorEstado)
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        InsigniaEstado(c.estado),
                        const SizedBox(height: 4),
                        Text('${c.cantidad} · ${c.porcentaje.toStringAsFixed(c.porcentaje % 1 == 0 ? 0 : 1)} %'),
                      ],
                    ),
                ],
              ),
                ],
              ),
            ),
            const SizedBox(height: Espacio.e4),
            Text(
              'Detalle: ${detalle.totalEjemplares == 1 ? '1 ejemplar' : '${detalle.totalEjemplares} ejemplares'}',
              style: Theme.of(context).textTheme.titleSmall,
            ),
            const SizedBox(height: Espacio.e2),
            if (detalle.ejemplares.isEmpty)
              const EstadoVacio(
                icono: LucideIcons.library,
                titulo: 'Este libro no tiene ejemplares',
                descripcion: 'Se puede eliminar.',
              )
            else
              SizedBox(
                height: 60.0 * detalle.ejemplares.length + 44,
                child: TablaDatos<Ejemplar>(etiqueta: 'Ejemplares del libro', columnas: _columnas, filas: detalle.ejemplares),
              ),
          ],
        ],
      ),
    );
  }
}

class _Cifra extends StatelessWidget {
  const _Cifra({required this.etiqueta, required this.valor});

  final String etiqueta;
  final String valor;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(etiqueta, style: Theme.of(context).textTheme.labelSmall!.copyWith(color: kGris500)),
        Text(valor, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w600, fontFeatures: cifrasTabulares)),
      ],
    );
  }
}
