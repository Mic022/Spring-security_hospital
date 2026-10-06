package com.roles.usermanagement.modules.ingreso;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingresos")
@Tag(name = "Ingresos")
@SecurityRequirement(name = "bearerAuth")
public class IngresoController {
    private final IngresoService service;

    public IngresoController(IngresoService service) {
        this.service = service;
    }

    @Operation(summary = "Registrar ingreso", description = "Estado inicial INGRESADO. Un paciente solo puede tener un ingreso abierto.")
    @PostMapping
    @PreAuthorize("hasAuthority('INGRESO_MANAGE')")
    public ResponseEntity<IngresoResponse> crear(@Valid @RequestBody IngresoRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request, principal.getName()));
    }

    @Operation(summary = "Cambiar estado, médico, área, habitación o fecha estimada",
            description = "Campos opcionales. Los cambios quedan en el historial y generan una alerta. "
                    + "Un ingreso RECUPERADO ya no se puede modificar.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INGRESO_MANAGE')")
    public IngresoResponse actualizar(@PathVariable Long id, @Valid @RequestBody IngresoUpdateRequest request, Principal principal) {
        return service.actualizar(id, request, principal.getName());
    }
}
