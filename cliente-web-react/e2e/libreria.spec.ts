import { expect, test } from "@playwright/test";
import {
  abrirMenu, capturar, codigoUnico, crearEjemplar, crearLibro, elegir, esperarAviso, graphql, isbnUnico
} from "./utilidades";

// Recorre TODOS los casos de uso de Libro y de Ejemplar contra el servidor real con MongoDB.
// Cada prueba crea sus propios datos (con un ISBN/código único) o solo lee los datos de prueba.

type PaginaTotal = { totalElementos: number; totalPaginas: number };

test.describe("Menú principal y Ayuda", () => {
  test("el inicio muestra exactamente lo que calcula el servidor (3 tareas)", async ({ page, request }, info) => {
    await page.goto("/");
    const { resumenInventario: r } = await graphql<{ resumenInventario: { totalLibros: number; totalEjemplares: number } }>(
      request, "{ resumenInventario { totalLibros totalEjemplares } }"
    );
    const cifras = page.getByRole("region", { name: "Inventario" });
    await expect(cifras.getByText(String(r.totalLibros), { exact: true })).toBeVisible();
    await expect(cifras.getByText(String(r.totalEjemplares), { exact: true })).toBeVisible();
    await expect(page.getByText(`${r.totalEjemplares} copias · calculado por el servidor`)).toBeVisible();
    await expect(page.getByText(/3 tareas en paralelo en el servidor/)).toContainText("tarea-");
    await capturar(page, info, "01-inicio", true);
  });

  test("el menú tiene cada caso de uso y Ayuda > Acerca de... con integrantes y versión", async ({ page }, info) => {
    await page.goto("/");
    const menu = await abrirMenu(page);
    for (const opcion of ["Listar", "Consultar", "Adicionar", "Actualizar", "Eliminar"]) {
      await expect(menu.getByRole("link", { name: opcion, exact: true })).toHaveCount(2);
    }
    await expect(menu.getByRole("link", { name: "Detalle", exact: true })).toHaveCount(1);
    await capturar(page, info, "02-menu");
    await menu.getByRole("link", { name: "Acerca de..." }).click();

    await expect(page.getByRole("heading", { level: 1, name: "Acerca de" })).toBeVisible();
    await expect(page.getByText("3.0.0", { exact: true })).toBeVisible();
    for (const integrante of ["Misael Gallo Tangarife", "Sara Zambrano", "Santiago Guimel", "Alejandra González"]) {
      await expect(page.getByText(integrante)).toBeVisible();
    }
    await expect(page.getByText("Colecciones libros y ejemplares")).toBeVisible();
    await expect(page.getByText(/Servidor conectado · \d+ ms/)).toBeVisible();
    await capturar(page, info, "03-acerca-de", true);
  });
});

test.describe("Libro", () => {
  test("adicionar: válido, con los opcionales", async ({ page, request }, info) => {
    const isbn = isbnUnico(info);
    await page.goto("/libros/agregar");
    await page.getByLabel("ISBN").fill(isbn);
    await page.getByLabel("Título").fill("Cóndores no entierran todos los días");
    await page.getByLabel("Autor").fill("Gustavo Álvarez Gardeazábal");
    await page.getByLabel("Fecha de publicación").fill("1971-06-15T09:45");
    await elegir(page, "Género", "Ficción");
    await page.getByLabel("Precio").fill("47000");
    await page.getByLabel("Número de páginas").fill("246");
    await page.getByLabel("Editorial (opcional)").fill("Plaza & Janés");
    await page.getByLabel("Sinopsis (opcional)").fill("La violencia partidista en Tuluá.");
    await capturar(page, info, "10-libro-adicionar-formulario", true);
    await page.getByRole("button", { name: "Adicionar libro" }).click();

    await esperarAviso(page, "Libro adicionado");
    await capturar(page, info, "11-libro-adicionar-exito");
    const { libroPorIsbn } = await graphql<{ libroPorIsbn: Record<string, unknown> }>(
      request,
      `query ($isbn: ID!) { libroPorIsbn(isbn: $isbn) { titulo numeroPaginas fechaPublicacion editorial sinopsis version } }`,
      { isbn }
    );
    expect(libroPorIsbn).toEqual({
      titulo: "Cóndores no entierran todos los días", numeroPaginas: 246, fechaPublicacion: "1971-06-15T09:45:00",
      editorial: "Plaza & Janés", sinopsis: "La violencia partidista en Tuluá.", version: 0
    });
  });

  test("adicionar: inválido (formato en el cliente y reglas del servidor)", async ({ page }, info) => {
    await page.goto("/libros/agregar");
    await page.getByRole("button", { name: "Adicionar libro" }).click();
    await expect(page.getByText("Este campo es obligatorio.")).toHaveCount(5);
    await expect(page.getByText("Elige la fecha y la hora.")).toBeVisible();
    await page.getByLabel("ISBN").fill("12345");
    await page.getByRole("button", { name: "Adicionar libro" }).click();
    await expect(page.getByText("El ISBN debe tener de 1 a 4 dígitos (solo números).")).toBeVisible();
    await capturar(page, info, "12-libro-adicionar-validacion", true);

    // ISBN repetido: lo rechaza el servidor (índice único de MongoDB).
    await page.getByLabel("ISBN").fill("1001");
    await page.getByLabel("Título").fill("Copia");
    await page.getByLabel("Autor").fill("Alguien");
    await page.getByLabel("Fecha de publicación").fill("2001-01-01T10:00");
    await page.getByLabel("Precio").fill("1000");
    await page.getByLabel("Número de páginas").fill("10");
    await page.getByRole("button", { name: "Adicionar libro" }).click();
    await expect(page.getByRole("alert")).toContainText("Ya existe un libro con el ISBN 1001");
    await capturar(page, info, "13-libro-adicionar-rechazo");
  });

  test("consultar: encontrado por ISBN y por título, y no encontrado", async ({ page }, info) => {
    await page.goto("/libros/consultar");
    await page.getByLabel("ISBN", { exact: true }).fill("1001");
    await page.getByRole("button", { name: "Buscar" }).click();
    const ficha = page.getByRole("article", { name: "Libro Cien años de soledad" });
    await expect(ficha).toContainText("30/05/1967, 00:00");
    await expect(ficha).toContainText("Sudamericana");
    await expect(ficha).toContainText("ID en MongoDB (_id)");
    await capturar(page, info, "20-libro-consultar-isbn", true);

    await page.getByText("Por título").click();
    await page.getByRole("textbox", { name: "Título" }).fill("la voragine");
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByRole("heading", { name: "La vorágine" })).toBeVisible();
    await capturar(page, info, "21-libro-consultar-titulo");

    await page.getByRole("textbox", { name: "Título" }).fill("Libro que no existe");
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByText("No hay ningún libro titulado «Libro que no existe».")).toBeVisible();
    await capturar(page, info, "22-libro-consultar-no-encontrado");
  });

  test("detalle: maestro (libro) + detalle (ejemplares) con conteos del servidor", async ({ page, request }, info) => {
    const { detalleLibro } = await graphql<{ detalleLibro: { totalEjemplares: number } }>(
      request, `{ detalleLibro(isbn: "1001") { totalEjemplares } }`
    );
    await page.goto("/libros/detalle");
    await page.getByLabel("ISBN del libro").fill("1001");
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByText("Maestro: libro")).toBeVisible();
    const tabla = page.getByRole("table", { name: "Ejemplares del libro" });
    for (const codigo of ["EJ-001", "EJ-002", "EJ-004"]) {
      await expect(tabla.getByText(codigo, { exact: true })).toBeVisible();
    }
    await expect(page.getByText(`Detalle: ${detalleLibro.totalEjemplares} ejemplares`)).toBeVisible();
    await capturar(page, info, "23-libro-detalle", true);
  });

  test("actualizar: busca, muestra todo, guarda y detecta el conflicto de versión", async ({ page, request }, info) => {
    const isbn = isbnUnico(info);
    await crearLibro(request, isbn);
    await page.goto("/libros/actualizar");
    await page.getByLabel("ISBN del libro").fill(isbn);
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByText("Datos actuales", { exact: true })).toBeVisible();
    await expect(page.getByText("10/05/2020, 08:30")).toBeVisible();
    await expect(page.getByLabel("Precio")).toHaveValue("50000");
    await expect(page.getByLabel("Editorial (opcional)")).toHaveValue("Editorial E2E");
    await page.getByLabel("Precio").fill("52500");
    await elegir(page, "Género", "Historia");
    await capturar(page, info, "30-libro-actualizar-formulario", true);
    await page.getByRole("button", { name: "Guardar cambios" }).click();

    await esperarAviso(page, "Cambios guardados");
    await expect(page.getByText("Datos guardados", { exact: true })).toBeVisible();
    await capturar(page, info, "31-libro-actualizar-guardado");
    const { libroPorIsbn } = await graphql<{ libroPorIsbn: { precio: number; genero: string; version: number } }>(
      request, `query ($isbn: ID!) { libroPorIsbn(isbn: $isbn) { precio genero version } }`, { isbn }
    );
    expect(libroPorIsbn).toEqual({ precio: 52500, genero: "HISTORIA", version: 1 });

    // Otro "usuario" cambia el libro mientras esta pantalla lo tiene abierto: al guardar, conflicto.
    await page.getByRole("button", { name: "Actualizar otro libro" }).click();
    await page.getByLabel("ISBN del libro").fill(isbn);
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByText("Datos actuales", { exact: true })).toBeVisible();
    await graphql(request, `mutation ($input: LibroInput!) { actualizarLibro(input: $input) { version } }`, {
      input: {
        isbn, titulo: "Cambiado por otro usuario", autor: "Autora E2E", precio: 1000, numeroPaginas: 200,
        fechaPublicacion: "2020-05-10T08:30:00", genero: "FICCION", version: 1
      }
    });
    await page.getByLabel("Precio").fill("60000");
    await page.getByRole("button", { name: "Guardar cambios" }).click();
    await expect(page.getByRole("alert")).toContainText("Otro usuario modificó este libro; vuelva a buscarlo.");
    await capturar(page, info, "32-libro-actualizar-conflicto");
  });

  test("eliminar: cancelar no borra y confirmar sí", async ({ page, request }, info) => {
    const isbn = isbnUnico(info);
    await crearLibro(request, isbn, "Libro para eliminar");
    await page.goto("/libros/eliminar");
    await page.getByLabel("ISBN del libro").fill(isbn);
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByText("Libro a eliminar", { exact: true })).toBeVisible();
    await expect(page.getByText("Editorial E2E")).toBeVisible();
    await capturar(page, info, "40-libro-eliminar-ficha", true);

    await page.getByRole("button", { name: "Eliminar libro" }).click();
    const dialogo = page.getByRole("alertdialog");
    await expect(dialogo).toContainText("¿Eliminar «Libro para eliminar»?");
    await capturar(page, info, "41-libro-eliminar-dialogo");
    await dialogo.getByRole("button", { name: "Cancelar" }).click();
    await expect(dialogo).toBeHidden();
    const antes = await graphql<{ libroPorIsbn: unknown }>(request, `query ($isbn: ID!) { libroPorIsbn(isbn: $isbn) { isbn } }`, { isbn });
    expect(antes.libroPorIsbn).not.toBeNull();

    await page.getByRole("button", { name: "Eliminar libro" }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Eliminar", exact: true }).click();
    await esperarAviso(page, "Libro eliminado");
    await capturar(page, info, "42-libro-eliminar-exito");
    const despues = await graphql<{ libroPorIsbn: unknown }>(request, `query ($isbn: ID!) { libroPorIsbn(isbn: $isbn) { isbn } }`, { isbn });
    expect(despues.libroPorIsbn).toBeNull();
  });

  test("eliminar un libro con ejemplares: el servidor lo rechaza", async ({ page, request }, info) => {
    await page.goto("/libros/eliminar");
    await page.getByLabel("ISBN del libro").fill("1001");
    await page.getByRole("button", { name: "Buscar" }).click();
    await page.getByRole("button", { name: "Eliminar libro" }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Eliminar", exact: true }).click();

    await expect(page.getByRole("alert")).toContainText("porque tiene");
    await expect(page.getByRole("alert")).toContainText("ejemplar(es) registrado(s)");
    await capturar(page, info, "43-libro-eliminar-rechazo");
    const { libroPorIsbn } = await graphql<{ libroPorIsbn: unknown }>(request, `{ libroPorIsbn(isbn: "1001") { isbn } }`);
    expect(libroPorIsbn).not.toBeNull();
  });

  test("listar: todos paginado y por filtro de género y precio (conteos del servidor)", async ({ page, request }, info) => {
    await page.goto("/libros/listar");
    await expect(page.getByRole("table", { name: "Libros del inventario" })).toBeVisible();
    const { libros } = await graphql<{ libros: PaginaTotal }>(request, "{ libros(pagina: 0, tamanio: 10) { totalElementos totalPaginas } }");
    await expect(page.getByText(`${libros.totalElementos} libros`, { exact: true })).toBeVisible();
    await expect(page.getByText(`Página 1 de ${libros.totalPaginas}`)).toBeVisible();
    await capturar(page, info, "50-libro-listar");
    if (libros.totalPaginas > 1) {
      await page.getByRole("button", { name: "Página siguiente" }).click();
      await expect(page.getByText(`Página 2 de ${libros.totalPaginas}`)).toBeVisible();
      await capturar(page, info, "51-libro-listar-pagina-2");
    }

    const total = async (genero: string | null, min: number | null, max: number | null) =>
      (await graphql<{ librosPorPrecioYGenero: PaginaTotal }>(
        request,
        `query ($g: Genero, $min: Float, $max: Float) { librosPorPrecioYGenero(genero: $g, precioMin: $min, precioMax: $max) { totalElementos } }`,
        { g: genero, min, max }
      )).librosPorPrecioYGenero.totalElementos;
    const conteo = (n: number) => (n === 1 ? "1 libro" : `${n} libros`);

    await page.getByLabel("Precio mínimo").fill("40000");
    await page.getByLabel("Precio máximo").fill("60000");
    await page.getByRole("button", { name: "Filtrar" }).click();
    await expect(page.getByText(conteo(await total(null, 40000, 60000)), { exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: /Pedro Páramo/ })).toBeVisible();
    await capturar(page, info, "52-libro-listar-precio");

    const generos: [string, string][] = [
      ["Ficción", "FICCION"], ["No ficción", "NO_FICCION"], ["Ciencia", "CIENCIA"], ["Historia", "HISTORIA"],
      ["Infantil", "INFANTIL"], ["Poesía", "POESIA"], ["Otro", "OTRO"]
    ];
    for (const [texto, valor] of generos) {
      await elegir(page, "Género", texto);
      await expect(page.getByText(conteo(await total(valor, 40000, 60000)), { exact: true })).toBeVisible();
    }
    await page.getByRole("button", { name: "Limpiar filtros" }).first().click();
    await elegir(page, "Género", "Poesía");
    await expect(page.getByText(conteo(await total("POESIA", null, null)), { exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: /Veinte poemas de amor/ })).toBeVisible();
    await capturar(page, info, "53-libro-listar-genero");
  });

  test("Observer: lo que se adiciona en otra pestaña aparece solo en Listar (≤ 15 s)", async ({ page, context, request }, info) => {
    const isbn = isbnUnico(info);
    const precio = 31000 + Math.floor(Math.random() * 999);
    await page.goto("/libros/listar");
    await page.getByLabel("Precio mínimo").fill(String(precio));
    await page.getByLabel("Precio máximo").fill(String(precio));
    await page.getByRole("button", { name: "Filtrar" }).click();
    await expect(page.getByText("Ningún libro coincide con el filtro")).toBeVisible();

    const otra = await context.newPage();
    await otra.goto("/libros/agregar");
    await otra.getByLabel("ISBN").fill(isbn);
    await otra.getByLabel("Título").fill("Observado desde otra pestaña");
    await otra.getByLabel("Autor").fill("Autora E2E");
    await otra.getByLabel("Fecha de publicación").fill("2024-01-01T12:00");
    await otra.getByLabel("Precio").fill(String(precio));
    await otra.getByLabel("Número de páginas").fill("100");
    await otra.getByRole("button", { name: "Adicionar libro" }).click();
    await expect(otra.getByText("Libro adicionado", { exact: true })).toBeVisible();
    await otra.close();

    // Sin recargar: el Observer entre clientes revisa la revisión de la colección cada 15 s.
    const nuevo = page.getByRole("cell", { name: /Observado desde otra pestaña/ });
    await expect(nuevo).toBeVisible({ timeout: 20_000 });
    await capturar(page, info, "54-libro-listar-observer");
    await graphql(request, `mutation ($isbn: ID!) { eliminarLibro(isbn: $isbn) { isbn } }`, { isbn });
  });
});

test.describe("Ejemplar", () => {
  test("adicionar: válido (libro elegido por ISBN) e inválido", async ({ page, request }, info) => {
    const codigo = codigoUnico(info);
    await page.goto("/ejemplares/agregar");
    await page.getByLabel("Código del ejemplar").fill(codigo);
    await page.getByLabel("ISBN del libro").fill("1009");
    await expect(page.getByText("Ficciones")).toBeVisible(); // el libro elegido
    await elegir(page, "Estado", "Disponible");
    await page.getByLabel("Ubicación", { exact: true }).fill("Estante D2");
    await page.getByLabel("Fecha de ingreso").fill("2026-03-02T16:10");
    await page.getByLabel("Precio de reposición").fill("52000");
    await page.getByLabel("Número de préstamos").fill("-1");
    await page.getByRole("button", { name: "Adicionar ejemplar" }).click();
    await expect(page.getByText("No puede ser negativo.")).toBeVisible();
    await capturar(page, info, "60-ejemplar-adicionar-validacion", true);

    await page.getByLabel("Número de préstamos").fill("0");
    await page.getByLabel("Observaciones (opcional)").fill("Donado por un lector");
    await page.getByRole("button", { name: "Adicionar ejemplar" }).click();
    await esperarAviso(page, "Ejemplar adicionado");
    await capturar(page, info, "61-ejemplar-adicionar-exito");
    const { ejemplarPorCodigo } = await graphql<{ ejemplarPorCodigo: Record<string, unknown> }>(
      request, `query ($c: ID!) { ejemplarPorCodigo(codigo: $c) { isbnLibro numeroPrestamos observaciones libro { titulo } } }`, { c: codigo }
    );
    expect(ejemplarPorCodigo).toEqual({
      isbnLibro: "1009", numeroPrestamos: 0, observaciones: "Donado por un lector", libro: { titulo: "Ficciones" }
    });

    // Libro inexistente: lo rechaza el servidor.
    await page.getByLabel("Código del ejemplar").fill(codigoUnico(info));
    await page.getByLabel("ISBN del libro").fill("9999");
    await expect(page.getByText("No hay ningún libro con ese ISBN.")).toBeVisible();
    await page.getByLabel("Ubicación", { exact: true }).fill("Estante D2");
    await page.getByLabel("Fecha de ingreso").fill("2026-03-02T16:10");
    await page.getByLabel("Precio de reposición").fill("52000");
    await page.getByRole("button", { name: "Adicionar ejemplar" }).click();
    await expect(page.getByRole("alert")).toContainText("No existe un libro con el ISBN 9999");
    await capturar(page, info, "62-ejemplar-adicionar-rechazo");
  });

  test("consultar: por código, por libro + ubicación, y no encontrado", async ({ page }, info) => {
    await page.goto("/ejemplares/consultar");
    await page.getByLabel("Código del ejemplar").fill("ej-007");
    await page.getByRole("button", { name: "Buscar" }).click();
    const ficha = page.getByRole("article", { name: "Ejemplar EJ-007" });
    await expect(ficha).toContainText("Dañado");
    await expect(ficha).toContainText("14/11/2023, 15:10");
    await expect(ficha).toContainText("Lomo despegado");
    await expect(ficha).toContainText("Jorge Isaacs");
    await capturar(page, info, "70-ejemplar-consultar", true);

    await page.getByLabel("Código del ejemplar").fill("EJ-999");
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByText("No hay ningún ejemplar con el código «EJ-999».")).toBeVisible();
    await capturar(page, info, "71-ejemplar-consultar-no-encontrado");

    await page.getByText("Por libro y ubicación").click();
    await page.getByLabel("ISBN del libro").fill("1001");
    await page.getByLabel("Ubicación", { exact: true }).fill("estante a1");
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByText("3 ejemplares de ese libro en esa ubicación")).toBeVisible();
    for (const codigo of ["EJ-001", "EJ-002", "EJ-004"]) {
      await expect(page.getByRole("article", { name: `Ejemplar ${codigo}` })).toBeVisible();
    }
    await capturar(page, info, "72-ejemplar-consultar-libro-ubicacion");
  });

  test("actualizar: al pasar a Prestado el servidor suma un préstamo", async ({ page, request }, info) => {
    const codigo = codigoUnico(info);
    await crearEjemplar(request, codigo, "1001", 4);
    await page.goto("/ejemplares/actualizar");
    await page.getByLabel("Código del ejemplar").fill(codigo);
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByText("Datos actuales", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Número de préstamos")).toHaveValue("4");
    await expect(page.getByLabel("Número de préstamos")).toHaveAttribute("readonly", "");
    await elegir(page, "Estado", "Prestado");
    await page.getByLabel("Observaciones (opcional)").fill("Prestado en la prueba E2E");
    await capturar(page, info, "80-ejemplar-actualizar-formulario", true);
    await page.getByRole("button", { name: "Guardar cambios" }).click();

    await esperarAviso(page, "Cambios guardados");
    const guardado = page.getByRole("article", { name: `Ejemplar ${codigo}` });
    await expect(guardado).toContainText("Datos guardados");
    await expect(guardado).toContainText("Prestado");
    await expect(guardado).toContainText("Prestado en la prueba E2E");
    await capturar(page, info, "81-ejemplar-actualizar-guardado", true);
    const { ejemplarPorCodigo } = await graphql<{ ejemplarPorCodigo: { numeroPrestamos: number; version: number } }>(
      request, `query ($c: ID!) { ejemplarPorCodigo(codigo: $c) { numeroPrestamos version } }`, { c: codigo }
    );
    expect(ejemplarPorCodigo).toEqual({ numeroPrestamos: 5, version: 1 });
  });

  test("eliminar: cancelar no borra y confirmar sí", async ({ page, request }, info) => {
    const codigo = codigoUnico(info);
    await crearEjemplar(request, codigo, "1011", 0);
    await page.goto("/ejemplares/eliminar");
    await page.getByLabel("Código del ejemplar").fill(codigo);
    await page.getByRole("button", { name: "Buscar" }).click();
    await expect(page.getByText("Ejemplar a eliminar", { exact: true })).toBeVisible();
    await capturar(page, info, "90-ejemplar-eliminar-ficha", true);

    await page.getByRole("button", { name: "Eliminar ejemplar" }).click();
    await capturar(page, info, "91-ejemplar-eliminar-dialogo");
    await page.getByRole("alertdialog").getByRole("button", { name: "Cancelar" }).click();
    expect((await graphql<{ ejemplarPorCodigo: unknown }>(
      request, `query ($c: ID!) { ejemplarPorCodigo(codigo: $c) { codigo } }`, { c: codigo }
    )).ejemplarPorCodigo).not.toBeNull();

    await page.getByRole("button", { name: "Eliminar ejemplar" }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Eliminar", exact: true }).click();
    await esperarAviso(page, "Ejemplar eliminado");
    await capturar(page, info, "92-ejemplar-eliminar-exito");
    expect((await graphql<{ ejemplarPorCodigo: unknown }>(
      request, `query ($c: ID!) { ejemplarPorCodigo(codigo: $c) { codigo } }`, { c: codigo }
    )).ejemplarPorCodigo).toBeNull();
  });

  test("listar: todos los atributos + título y autor del libro, con filtros y paginación", async ({ page, request }, info) => {
    await page.goto("/ejemplares/listar");
    const tabla = page.getByRole("table", { name: "Ejemplares del inventario" });
    await expect(tabla).toBeVisible();
    for (const columna of ["Código", "Título del libro", "Autor del libro", "Estado", "Ubicación", "Fecha de ingreso",
      "Préstamos", "Reposición", "Observaciones", "Versión", "ID (_id)", "ID del libro (libroId)"]) {
      await expect(tabla.getByRole("columnheader", { name: columna, exact: true })).toBeAttached();
    }
    await expect(tabla.getByRole("cell", { name: "Cien años de soledad" }).first()).toBeVisible();
    await capturar(page, info, "95-ejemplar-listar");

    const total = async (estado: string | null, isbn: string | null) =>
      (await graphql<{ ejemplaresConLibro: PaginaTotal }>(
        request,
        `query ($e: EstadoEjemplar, $i: String) { ejemplaresConLibro(estado: $e, isbn: $i) { totalElementos } }`,
        { e: estado, i: isbn }
      )).ejemplaresConLibro.totalElementos;
    const conteo = (n: number) => (n === 1 ? "1 ejemplar" : `${n} ejemplares`);

    await expect(page.getByText(conteo(await total(null, null)), { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Página siguiente" }).click();
    await expect(page.getByText(/Página 2 de \d+/)).toBeVisible();

    for (const [texto, valor] of [["Disponible", "DISPONIBLE"], ["Prestado", "PRESTADO"], ["Dañado", "DANIADO"], ["Dado de baja", "DADO_DE_BAJA"]]) {
      await page.getByRole("radio", { name: texto, exact: true }).click({ force: true });
      await expect(page.getByText(conteo(await total(valor, null)), { exact: true })).toBeVisible();
    }
    await capturar(page, info, "96-ejemplar-listar-estado");

    await page.getByLabel("ISBN del libro").fill("1001");
    await page.getByRole("button", { name: "Filtrar" }).click();
    await expect(page.getByText(conteo(await total("DADO_DE_BAJA", "1001")), { exact: true })).toBeVisible();
    await page.getByRole("radio", { name: "Todos", exact: true }).click({ force: true });
    await expect(page.getByText(conteo(await total(null, "1001")), { exact: true })).toBeVisible();
    await capturar(page, info, "97-ejemplar-listar-isbn");
  });
});
