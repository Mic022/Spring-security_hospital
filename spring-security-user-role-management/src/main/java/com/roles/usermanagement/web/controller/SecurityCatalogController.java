package com.roles.usermanagement.web.controller;

import com.roles.usermanagement.domain.dto.*;
import com.roles.usermanagement.domain.service.SecurityCatalogService;
import com.roles.usermanagement.web.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name="Roles y permisos", description="Catálogos y permisos heredados por rol")
@SecurityRequirement(name=OpenApiConfig.BEARER_AUTH)
public class SecurityCatalogController {
    private final SecurityCatalogService catalog;
    public SecurityCatalogController(SecurityCatalogService catalog) {this.catalog=catalog;}
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary="Listar roles y sus permisos")
    public List<RoleCatalogDto> roles() {return catalog.roles();}
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE') and (#dto.permissions == null or #dto.permissions.isEmpty() or hasAuthority('PERMISSION_MANAGE'))")
    @Operation(summary="Crear un rol", description="Permisos opcionales deben existir. Un rol puede comenzar sin permisos.")
    public ResponseEntity<RoleCatalogDto> createRole(@RequestBody RoleCatalogDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createRole(dto));
    }
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(summary="Listar permisos disponibles")
    public List<String> permissions() {return catalog.permissions();}
    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Operation(summary="Crear un permiso", description="Registrar el permiso no crea reglas de autorización ni endpoints automáticamente.")
    public ResponseEntity<PermissionCatalogDto> createPermission(@RequestBody PermissionCatalogDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createPermission(dto));
    }
    @PutMapping("/roles/{role}/permissions/{permission}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE') and hasAuthority('PERMISSION_MANAGE')")
    @Operation(summary="Agregar un permiso a un rol", description="Se aplica a todos sus usuarios. Repetir no duplica la relación.")
    public RoleCatalogDto link(@PathVariable String role,@PathVariable String permission) {return catalog.link(role,permission,true);}
    @DeleteMapping("/roles/{role}/permissions/{permission}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE') and hasAuthority('PERMISSION_MANAGE')")
    @Operation(summary="Retirar un permiso de un rol", description="Concesiones individuales permanecen. Los permisos base de los roles del sistema están reservados.")
    public RoleCatalogDto unlink(@PathVariable String role,@PathVariable String permission) {return catalog.link(role,permission,false);}
}
