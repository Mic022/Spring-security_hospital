package com.roles.usermanagement.modules.paciente;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pacientes")
@Tag(name = "Pacientes")
@SecurityRequirement(name = "bearerAuth")
public class PacienteController {
    private final PacienteService service;

    public PacienteController(PacienteService service) {
        this.service = service;
    }

    /** Los filtros llegan como parámetros opcionales de la URL y Spring los agrupa en FiltroPacientes. */
    @Operation(summary = "Buscar pacientes (filtros)",
            description = "Parámetros opcionales combinables: nombre, estado, medico, area, ingresoDesde, ingresoHasta, "
                    + "recuperacionHasta (AAAA-MM-DD). Ej.: próximos a recuperarse = recuperacionHasta=hoy+7. "
                    + "El médico solo ve sus pacientes.")
    @GetMapping
    @PreAuthorize("hasAuthority('PACIENTE_READ')")
    public Page<PacienteResponse> buscar(FiltroPacientes filtro,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.buscar(filtro, page, size);
    }

    @Operation(summary = "Buscar paciente por documento")
    @GetMapping("/documento/{documento}")
    @PreAuthorize("hasAnyAuthority('PACIENTE_READ','PACIENTE_MANAGE')")
    public PacienteResponse porDocumento(@PathVariable String documento) {
        return service.porDocumento(documento);
    }

    @Operation(summary = "Registrar paciente")
    @PostMapping
    @PreAuthorize("hasAuthority('PACIENTE_MANAGE')")
    public ResponseEntity<PacienteResponse> crear(@Valid @RequestBody PacienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @Operation(summary = "Actualizar datos del paciente")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PACIENTE_MANAGE')")
    public PacienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody PacienteRequest request) {
        return service.actualizar(id, request);
    }
}
