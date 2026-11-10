package com.libreria.servidor.model;

import org.springframework.data.domain.Page;

import java.util.List;

/** Una página de resultados de un Listar (la paginación la hace MongoDB con skip/limit). */
public record Pagina<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static <T> Pagina<T> de(Page<T> page) {
        return new Pagina<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
