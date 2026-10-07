package com.roles.usermanagement.domain.service;

import com.roles.usermanagement.persistance.crud.PermissionCrudRepository;
import com.roles.usermanagement.persistance.crud.RoleCrudRepository;
import com.roles.usermanagement.persistance.repository.UserRepository;
import com.roles.usermanagement.persistance.entity.PermissionEntity;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Evita la escalada de privilegios en la administración de usuarios y roles.
 * Regla general: nadie puede conceder ni modificar más permisos de los que ya tiene.
 */
@Component
public class PrivilegeGuard {
    private final UserRepository users;
    private final RoleCrudRepository roles;
    private final PermissionCrudRepository catalog;

    public PrivilegeGuard(UserRepository users, RoleCrudRepository roles, PermissionCrudRepository catalog) {
        this.users = users;
        this.roles = roles;
        this.catalog = catalog;
    }

    /** Nombre del usuario autenticado en la petición actual. */
    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    /**
     * Permisos efectivos (rol + individuales) de una cuenta, leídos de la base de datos.
     * No se usan las autoridades del token porque mezclan ROLE_ADMIN con permisos como ROLE_ASSIGN.
     */
    private Set<String> permissionsOf(String username) {
        if (username == null) return Set.of();
        try {
            return new HashSet<>(users.permissionDetails(username).effectivePermissions());
        } catch (ResponseStatusException notFound) {
            return Set.of();
        }
    }

    /** Falla con 403 si el usuario actual no tiene todos los permisos indicados. */
    public void requireAll(Collection<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return;
        // Los permisos que no existen se ignoran aquí: el repositorio los rechaza con 400/404.
        List<String> existing = permissions.stream().filter(p -> p != null && catalog.existsById(p)).toList();
        if (!permissionsOf(currentUsername()).containsAll(existing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes conceder permisos que tu cuenta no tiene");
        }
    }

    /** Asignar un rol equivale a conceder todos sus permisos. */
    public void requireRole(String role) {
        if (role == null) return;
        List<String> permissions = roles.findById(role)
                .map(r -> r.getPermissions().stream().map(PermissionEntity::getName).toList())
                .orElse(List.of());
        requireAll(permissions);
    }

    /**
     * Solo se puede modificar una cuenta cuyos permisos sean un subconjunto de los propios.
     * Así, quien no es administrador no puede cambiar la contraseña ni bloquear a un ADMIN.
     */
    public void requireCanManage(String username) {
        if (username == null) return;
        // Si la cuenta no existe, permissionsOf devuelve vacío y el repositorio responde 404 más adelante.
        if (!permissionsOf(currentUsername()).containsAll(permissionsOf(username))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar una cuenta con más permisos que la tuya");
        }
    }

    /**
     * Impide cambiar el propio rol: un ADMIN que se quitara el rol podría dejar el sistema sin administradores.
     * Enviar el mismo rol que ya se tiene no es un cambio y se permite.
     */
    public void requireNotOwnRoleChange(String username, String role) {
        if (role == null || username == null || !username.equals(currentUsername())) return;
        if (!role.equals(users.permissionDetails(username).role())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes cambiar tu propio rol");
        }
    }

    /** Impide eliminar, bloquear o deshabilitar la propia cuenta (evita quedarse sin administrador). */
    public void requireNotSelf(String username, String message) {
        if (username != null && username.equals(currentUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }
}
