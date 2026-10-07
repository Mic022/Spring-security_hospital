package com.roles.usermanagement.domain.service;

import com.roles.usermanagement.persistance.crud.*;
import com.roles.usermanagement.persistance.entity.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityBootstrapService {
    private final PermissionCrudRepository permissions;
    private final RoleCrudRepository roles;
    private final UserCrudRepository users;
    private final UserRoleCrudRepository userRoles;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String username;
    private final String email;
    private final String password;

    public SecurityBootstrapService(PermissionCrudRepository permissions, RoleCrudRepository roles,
            UserCrudRepository users, UserRoleCrudRepository userRoles, PasswordEncoder encoder,
            @Value("${app.bootstrap.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.username:superadmin}") String username,
            @Value("${app.bootstrap.email:superadmin@localhost}") String email,
            @Value("${app.bootstrap.password:}") String password) {
        this.permissions = permissions;
        this.roles = roles;
        this.users = users;
        this.userRoles = userRoles;
        this.encoder = encoder;
        this.enabled = enabled;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Transactional
    public void initialize() {
        Map<String, PermissionEntity> catalog = new LinkedHashMap<>();
        for (UserRoles.Authority authority : UserRoles.Authority.values()) {
            String name = authority.value();
            PermissionEntity permission = permissions.findById(name).orElseGet(() -> {
                PermissionEntity created = new PermissionEntity();
                created.setName(name);
                return permissions.save(created);
            });
            catalog.put(name, permission);
        }
        for (UserRoles.Role roleName : UserRoles.Role.values()) {
            RoleEntity role = roles.findById(roleName.name()).orElseGet(() -> {
                RoleEntity created = new RoleEntity();
                created.setName(roleName.name());
                return created;
            });
            // Agrega los permisos base que falten; no quita los que un administrador haya añadido.
            for (UserRoles.Authority authority : roleName.permissions()) {
                PermissionEntity permission = catalog.get(authority.value());
                boolean present = role.getPermissions().stream()
                        .anyMatch(existing -> existing.getName().equals(permission.getName()));
                if (!present) role.getPermissions().add(permission);
            }
            roles.save(role);
        }
        if (!enabled) return;
        if (username.isBlank() || username.length() > 50 || email.isBlank() || email.length() > 200) {
            throw new IllegalStateException("Configura ADMIN_USERNAME y ADMIN_EMAIL válidos.");
        }
        UserRoleId adminRole = new UserRoleId(username, UserRoles.Role.ADMIN.name());
        if (users.existsById(username)) {
            if (!userRoles.existsById(adminRole)) {
                throw new IllegalStateException("El usuario inicial ya existe sin ADMIN; elige otro ADMIN_USERNAME.");
            }
            // Never replace an existing account's password, email or status.
            return;
        }
        if (password.isBlank()) {
            throw new IllegalStateException("Configura ADMIN_PASSWORD para crear el administrador inicial.");
        }
        UserEntity admin = new UserEntity();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPassword(encoder.encode(password));
        admin.setLocked(false);
        admin.setDisabled(false);
        admin.setRoles(new ArrayList<>());
        users.save(admin);
        UserRoleEntity assignment = new UserRoleEntity();
        assignment.setUsername(username);
        assignment.setRole(UserRoles.Role.ADMIN.name());
        userRoles.save(assignment);
    }
}