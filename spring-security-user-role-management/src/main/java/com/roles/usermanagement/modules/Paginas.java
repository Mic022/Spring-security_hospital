package com.roles.usermanagement.modules;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validación común de la paginación de los listados (?page=0&size=20). */
public final class Paginas {
    private Paginas() {}

    /** page empieza en 0 y size va de 1 a 100; si no, responde 400. */
    public static PageRequest of(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page >= 0; size entre 1 y 100");
        }
        return PageRequest.of(page, size, sort);
    }
}
