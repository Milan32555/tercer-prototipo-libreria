import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../modelos/libro.dart';
import '../../servicios/cliente_graphql.dart';
import '../../servicios/servicio_libro.dart';
import '../../tema/tema.dart';
import '../../utilidades/validaciones.dart';

/// El ejemplar se asocia a su libro por el ISBN: al escribirlo se busca el libro en el servidor
/// y debajo del campo se muestra su título y autor (o que no existe). El servidor traduce el ISBN
/// al _id del libro (llave foránea libroId).
class CampoLibroPorIsbn extends StatefulWidget {
  const CampoLibroPorIsbn({super.key, required this.controller});

  final TextEditingController controller;

  @override
  State<CampoLibroPorIsbn> createState() => _CampoLibroPorIsbnState();
}

class _CampoLibroPorIsbnState extends State<CampoLibroPorIsbn> {
  Timer? _espera;
  Libro? _libro;
  bool _buscando = false;
  bool _noExiste = false;
  int _consulta = 0;
  String _ultimo = '';

  @override
  void initState() {
    super.initState();
    // Se escucha el controlador (y no solo lo que se teclea) para reaccionar también cuando el
    // texto se pone desde el código o se limpia el formulario.
    widget.controller.addListener(_alCambiar);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_alCambiar);
    _espera?.cancel();
    super.dispose();
  }

  void _alCambiar() {
    final isbn = widget.controller.text.trim();
    if (isbn == _ultimo) return;
    _ultimo = isbn;
    _espera?.cancel();
    setState(() {
      _libro = null;
      _noExiste = false;
      _buscando = RegExp(r'^\d{1,4}$').hasMatch(isbn);
    });
    if (!_buscando) return;
    final consulta = ++_consulta;
    _espera = Timer(const Duration(milliseconds: 350), () async {
      try {
        final libro = await buscarLibroPorIsbn(isbn);
        if (!mounted || consulta != _consulta) return;
        setState(() {
          _libro = libro;
          _noExiste = libro == null;
          _buscando = false;
        });
      } on ErrorGraphQL {
        if (mounted && consulta == _consulta) setState(() => _buscando = false);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextFormField(
          controller: widget.controller,
          decoration: const InputDecoration(labelText: 'ISBN del libro', hintText: '1001'),
          autocorrect: false,
          enableSuggestions: false,
          textInputAction: TextInputAction.next,
          validator: validarIsbn,
        ),
        const SizedBox(height: 4),
        if (_buscando) const Text('Buscando el libro…', style: TextStyle(fontSize: 12, color: kGris500)),
        if (_libro != null)
          Row(
            children: [
              const Icon(LucideIcons.bookCheck, size: 14, color: kMarca600),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  '${_libro!.titulo} · ${_libro!.autor}',
                  style: const TextStyle(fontSize: 12, color: kMarca600, fontWeight: FontWeight.w500),
                ),
              ),
            ],
          ),
        if (_noExiste)
          const Row(
            children: [
              Icon(LucideIcons.bookX, size: 14, color: kPeligro600),
              SizedBox(width: 4),
              Text('No hay ningún libro con ese ISBN.', style: TextStyle(fontSize: 12, color: kPeligro600)),
            ],
          ),
      ],
    );
  }
}
