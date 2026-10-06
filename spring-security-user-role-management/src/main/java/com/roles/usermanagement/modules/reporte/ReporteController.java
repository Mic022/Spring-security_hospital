package com.roles.usermanagement.modules.reporte;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes")
@Tag(name = "Reportes")
@SecurityRequirement(name = "bearerAuth")
public class ReporteController {
    private final ReporteService service;

    public ReporteController(ReporteService service) {
        this.service = service;
    }

    @Operation(summary = "Reporte completo de un paciente",
            description = "Ingreso más reciente, médico, área, habitación, estado, días transcurridos y restantes, "
                    + "citas e historial. El médico solo puede ver el de sus pacientes.")
    @GetMapping("/pacientes/{id}")
    @PreAuthorize("hasAuthority('REPORTE_READ')")
    public ReportePaciente reporte(@PathVariable Long id) {
        return service.reporte(id);
    }
}
