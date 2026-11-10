/// Validaciones de FORMATO de los formularios: devuelven el mensaje para mostrar debajo del
/// campo, o null si el valor sirve. Son las mismas del cliente web (src/validaciones.ts).
///
/// Las reglas de negocio (ISBN único, libro con ejemplares, versión…) las decide el
/// servidor; esto solo evita enviar un formulario incompleto.
library;

const _obligatorio = 'Este campo es obligatorio.';

String? validarObligatorio(String? valor) => (valor == null || valor.trim().isEmpty) ? _obligatorio : null;

String? validarDecimalPositivo(String? valor) {
  final texto = valor?.trim() ?? '';
  if (texto.isEmpty) return _obligatorio;
  final numero = double.tryParse(texto);
  if (numero == null) return 'Escribe un número válido.';
  return numero > 0 ? null : 'Debe ser mayor que cero.';
}

String? validarEnteroPositivo(String? valor) {
  final texto = valor?.trim() ?? '';
  if (texto.isEmpty) return _obligatorio;
  final numero = int.tryParse(texto);
  if (numero == null) return 'Escribe un número entero.';
  return numero > 0 ? null : 'Debe ser mayor que cero.';
}

String? validarEnteroNoNegativo(String? valor) {
  final texto = valor?.trim() ?? '';
  if (texto.isEmpty) return _obligatorio;
  final numero = int.tryParse(texto);
  if (numero == null) return 'Escribe un número entero.';
  return numero >= 0 ? null : 'No puede ser negativo.';
}

String? validarFecha(DateTime? valor) => valor == null ? 'Elige la fecha y la hora.' : null;

/// ISBN-10 o ISBN-13: solo dígitos y guiones (el ISBN-10 puede terminar en X).
String? validarIsbn(String? valor) {
  final texto = valor?.trim() ?? '';
  if (texto.isEmpty) return _obligatorio;
  final digitos = texto.replaceAll('-', '');
  final formato = RegExp(r'^\d[\d-]*[\dXx]$').hasMatch(texto) && !texto.contains('--');
  return formato && RegExp(r'^(\d{9}[\dXx]|\d{13})$').hasMatch(digitos)
      ? null
      : 'El ISBN debe tener 10 o 13 dígitos (se permiten guiones).';
}

/// Código del ejemplar: EJ- seguido de 3 a 6 dígitos (EJ-000).
String? validarCodigoEjemplar(String? valor) {
  final texto = valor?.trim() ?? '';
  if (texto.isEmpty) return _obligatorio;
  return RegExp(r'^EJ-\d{3,6}$', caseSensitive: false).hasMatch(texto)
      ? null
      : 'Usa el formato EJ-000 (EJ- y de 3 a 6 dígitos).';
}

/// Fecha obligatoria que no puede ser futura.
String? validarFechaNoFutura(DateTime? valor, {DateTime? ahora}) {
  if (valor == null) return 'Elige la fecha y la hora.';
  return valor.isAfter(ahora ?? DateTime.now()) ? 'La fecha no puede ser futura.' : null;
}

/// Texto opcional con un largo máximo.
String? Function(String?) validarLargoMaximo(int maximo) =>
    (valor) => (valor ?? '').trim().length > maximo ? 'Máximo $maximo caracteres.' : null;
