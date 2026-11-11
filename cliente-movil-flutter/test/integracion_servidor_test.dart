// Prueba de integración contra el servidor GraphQL REAL con MongoDB (debe estar en ejecución).
// Se omite si no se indica la URL:
//   flutter test test/integracion_servidor_test.dart --dart-define=URL_GRAPHQL=http://localhost:8081/graphql

import 'package:cliente_movil_libreria/servicios/cliente_graphql.dart';
import 'package:cliente_movil_libreria/servicios/servicio_ejemplar.dart';
import 'package:cliente_movil_libreria/servicios/servicio_libro.dart';
import 'package:cliente_movil_libreria/utilidades/formato.dart';
import 'package:flutter_test/flutter_test.dart';

const _url = String.fromEnvironment('URL_GRAPHQL');

void main() {
  final sufijo = DateTime.now().millisecondsSinceEpoch;
  // ISBN de 4 dígitos y código EJ-000 válidos y distintos en cada ejecución.
  final isbn = '${7000 + sufijo % 1000}';
  final codigo = 'EJ-${900000 + sufijo % 99999}';

  test(
    'CRUD completo de Libro y Ejemplar contra el servidor real (MongoDB)',
    () async {
      final revisionInicial = await consultarRevisionLibros();

      final creado = await crearLibro(
        isbn: isbn,
        titulo: 'Libro desde Flutter',
        autor: 'App Móvil',
        precio: 12345.5,
        numeroPaginas: 99,
        fechaPublicacion: aFechaServidor(DateTime(2020, 1, 2, 3, 4)),
        genero: 'CIENCIA',
        editorial: 'Editorial Móvil',
      );
      expect(creado.fechaPublicacion, '2020-01-02T03:04:00');
      expect(creado.id, hasLength(24)); // _id de MongoDB
      expect(creado.version, 0);
      expect(creado.sinopsis, isNull);
      expect(await consultarRevisionLibros(), greaterThan(revisionInicial));

      expect((await buscarLibroPorTitulo('libro desde flutter'))!.isbn, isbn);
      final filtrados = await listarLibros(pagina: 0, tamanio: 100, genero: 'CIENCIA', precioMin: 12000, precioMax: 13000);
      expect(filtrados.contenido.map((l) => l.isbn), contains(isbn));

      // Conflicto de versión: se actualiza con la versión leída y luego otra vez con la vieja.
      final actualizado = await actualizarLibro(
        isbn: isbn,
        titulo: 'Libro desde Flutter',
        autor: 'App Móvil',
        precio: 13000,
        numeroPaginas: 99,
        fechaPublicacion: creado.fechaPublicacion,
        genero: 'CIENCIA',
        version: creado.version,
      );
      expect(actualizado.version, 1);
      await expectLater(
        actualizarLibro(
          isbn: isbn,
          titulo: 'Cambio con la versión vieja',
          autor: 'App Móvil',
          precio: 1,
          numeroPaginas: 1,
          fechaPublicacion: creado.fechaPublicacion,
          genero: 'CIENCIA',
          version: creado.version,
        ),
        throwsA(isA<ErrorGraphQL>().having((e) => e.mensaje, 'mensaje', 'Otro usuario modificó este libro; vuelva a buscarlo.')),
      );

      final ejemplar = await crearEjemplar(
        codigo: codigo,
        estado: 'PRESTADO',
        ubicacion: 'Estante F',
        fechaIngreso: aFechaServidor(DateTime(2024, 6, 1)),
        precioReposicion: 5000,
        numeroPrestamos: 2,
        isbnLibro: isbn,
        observaciones: 'Creado desde la app',
      );
      expect(ejemplar.libroId, creado.id); // llave foránea = _id del libro
      expect(ejemplar.tituloLibro, 'Libro desde Flutter');
      await expectLater(
        eliminarLibro(isbn),
        throwsA(isA<ErrorGraphQL>().having((e) => e.mensaje, 'mensaje', contains('ejemplar(es)'))),
      );

      final devuelto = await actualizarEjemplar(
        codigo: codigo,
        isbnLibro: isbn,
        estado: 'DISPONIBLE',
        ubicacion: 'Mostrador',
        fechaIngreso: aFechaServidor(DateTime(2024, 6, 1, 8, 15)),
        precioReposicion: 5500,
        version: ejemplar.version,
      );
      expect(devuelto.estado, 'DISPONIBLE');
      expect(devuelto.fechaIngreso, '2024-06-01T08:15:00');
      expect(devuelto.numeroPrestamos, 2); // devolverlo no es un préstamo
      final disponibles = await listarEjemplaresConLibro(pagina: 0, tamanio: 10, estado: 'DISPONIBLE', isbn: isbn);
      expect(disponibles.contenido.single.codigo, codigo);
      expect(disponibles.contenido.single.autorLibro, 'App Móvil');
      expect(disponibles.totalElementos, 1);
      expect((await buscarEjemplaresPorLibroYUbicacion(isbn, 'mostrador')).single.codigo, codigo);

      final prestado = await actualizarEjemplar(
        codigo: codigo,
        isbnLibro: isbn,
        estado: 'PRESTADO',
        ubicacion: 'Mostrador',
        fechaIngreso: aFechaServidor(DateTime(2024, 6, 1, 8, 15)),
        precioReposicion: 5500,
      );
      expect(prestado.numeroPrestamos, 3); // el servidor sumó el nuevo préstamo

      final detalle = await consultarDetalleLibro(isbn);
      expect(detalle!.totalEjemplares, 1);
      expect(detalle.totalPrestamos, 3);

      await eliminarEjemplar(codigo);
      expect(await buscarEjemplarPorCodigo(codigo), isNull);
      expect((await eliminarLibro(isbn)).isbn, isbn);
      expect(await buscarLibroPorIsbn(isbn), isNull);
    },
    skip: _url.isEmpty ? 'Requiere el servidor: use --dart-define=URL_GRAPHQL=...' : null,
  );
}
