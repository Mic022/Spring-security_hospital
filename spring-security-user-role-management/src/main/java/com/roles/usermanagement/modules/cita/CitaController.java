package com.roles.usermanagement.modules.cita;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/citas")
@Tag(name = "Citas")
@SecurityRequirement(name = "bearerAuth")
public class CitaController {
    private final CitaService service;

    public CitaController(CitaService service) {
        this.service = service;
    }

    @Operation(summary = "Buscar citas (filtros)",
            description = "Parámetros opcionales: fecha (AAAA-MM-DD) y medico (id). El médico solo ve citas de sus pacientes.")
    @GetMapping
    @PreAuthorize("hasAuthority('CITA_READ')")
    public Page<CitaResponse> buscar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                     @RequestParam(required = false) Long medico,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.buscar(fecha, medico, page, size);
    }

    @Operation(summary = "Agendar cita", description = "Genera una alerta de tipo CITA.")
    @PostMapping
    @PreAuthorize("hasAuthority('CITA_MANAGE')")
    public ResponseEntity<CitaResponse> crear(@Valid @RequestBody CitaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @Operation(summary = "Modificar cita", description = "Reemplaza sus datos. Genera una alerta de tipo CITA.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CITA_MANAGE')")
    public CitaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CitaRequest request) {
        return service.actualizar(id, request);
    }
}
