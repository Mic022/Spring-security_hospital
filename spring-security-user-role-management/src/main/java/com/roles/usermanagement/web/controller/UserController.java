package com.roles.usermanagement.web.controller;

import com.roles.usermanagement.domain.dto.*;
import com.roles.usermanagement.domain.service.UserService;
import com.roles.usermanagement.web.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Tag(name="Usuarios", description="Cada operación requiere su permiso específico")
@SecurityRequirement(name=OpenApiConfig.BEARER_AUTH)
public class UserController {
    private final UserService users;
    public UserController(UserService users) {this.users=users;}

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary="Listar usuarios")
    public ResponseEntity<List<UserDto>> all() {return ResponseEntity.ok(users.getAllUsers());}

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('USER_CREATE') and hasAuthority('ROLE_ASSIGN') and (#dto.additionalPermissions == null or #dto.additionalPermissions.isEmpty() or hasAuthority('PERMISSION_ASSIGN'))")
    @Operation(summary="Crear un usuario", description="El rol es obligatorio. Asignar rol o permisos requiere ROLE_ASSIGN / PERMISSION_ASSIGN y no se puede conceder más de lo que uno tiene.")
    public ResponseEntity<UserDto> add(@RequestBody UserDto dto) {return ResponseEntity.ok(users.saveUser(dto));}

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('USER_UPDATE') and ((#dto.role == null and #dto.roles == null) or hasAuthority('ROLE_ASSIGN')) and (#dto.additionalPermissions == null or hasAuthority('PERMISSION_ASSIGN'))")
    @Operation(summary="Actualizar un usuario", description="Campos omitidos se conservan. role cambia el rol único. additionalPermissions reemplaza los permisos individuales. password se recibe en texto.")
    public ResponseEntity<UserDto> update(@RequestBody UserDto dto) {return ResponseEntity.ok(users.updateUser(dto));}

    @PostMapping("/assignRole")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    @Operation(summary="Cambiar el rol único del usuario", description="Reemplaza el rol anterior. Conserva los permisos individuales.")
    public ResponseEntity<UserRoleDto> assignRole(@RequestBody UserRoleDto dto) {return ResponseEntity.ok(users.assignRoleToUser(dto));}

    @PostMapping("/assignPermission")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN')")
    @Operation(summary="Agregar un permiso individual", description="Se suma a los permisos del rol sin cambiarlo. Repetir la asignación no crea duplicados.")
    public ResponseEntity<UserDto> grant(@RequestBody UserPermissionDto dto) {return ResponseEntity.ok(users.grantPermission(dto));}

    @DeleteMapping("/{username}/permissions/{permission}")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN')")
    @Operation(summary="Retirar un permiso individual", description="Los permisos heredados del rol permanecen.")
    public ResponseEntity<UserDto> revoke(@PathVariable String username,@PathVariable String permission) {
        return ResponseEntity.ok(users.revokePermission(username,permission));
    }
    @GetMapping("/{username}/permissions")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary="Consultar permisos heredados individuales y efectivos")
    public ResponseEntity<UserPermissionsDto> details(@PathVariable String username) {return ResponseEntity.ok(users.permissionDetails(username));}

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_ASSIGN')")
    @Operation(summary="Listar el catálogo de permisos")
    public ResponseEntity<List<String>> catalog() {return ResponseEntity.ok(users.permissionCatalog());}

    @DeleteMapping("/delete/{name}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @Operation(summary="Eliminar un usuario")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        if(!users.exists(name)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"El usuario no existe");
        users.deleteUser(name); return ResponseEntity.ok().build();
    }
}
