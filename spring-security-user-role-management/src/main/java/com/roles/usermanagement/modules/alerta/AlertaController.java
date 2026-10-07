package com.roles.usermanagement.modules.alerta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alertas")
@Tag(name = "Alertas")
@SecurityRequirement(name = "bearerAuth")
public class AlertaController {
    private final AlertaService service;

    public AlertaController(AlertaService service) {
        this.service = service;
    }

    @Operation(summary = "Listar alertas",
            description = "Filtro opcional estado=PENDIENTE|ATENDIDA. Paginación page/size. El médico solo ve las de sus pacientes.")
    @GetMapping
    @PreAuthorize("hasAuthority('ALERTA_READ')")
    public Page<AlertaResponse> buscar(@RequestParam(required = false) EstadoAlerta estado,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.buscar(estado, page, size);
    }

    @Operation(summary = "Marcar alerta como atendida")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ALERTA_ATENDER')")
    public AlertaResponse atender(@PathVariable Long id, Principal principal) {
        return service.atender(id, principal.getName());
    }
}
