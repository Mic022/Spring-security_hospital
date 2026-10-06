package com.roles.usermanagement.domain.service;

import com.roles.usermanagement.domain.dto.UserDto;
import com.roles.usermanagement.domain.dto.UserPermissionDto;
import com.roles.usermanagement.domain.dto.UserPermissionsDto;
import com.roles.usermanagement.domain.dto.UserRoleDto;
import com.roles.usermanagement.persistance.repository.UserRepository;
import com.roles.usermanagement.persistance.repository.UserRoleRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de cuentas. Antes de cada cambio, PrivilegeGuard comprueba que
 * el usuario autenticado no conceda ni modifique más permisos de los que tiene.
 */
@Service
public class UserService {

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final PrivilegeGuard guard;

  @Autowired
  public UserService(UserRepository userRepository, UserRoleRepository userRoleRepository, PrivilegeGuard guard) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.guard = guard;
  }

  /** Rol pedido en el DTO: el campo role o, en el formato anterior, el primer elemento de roles. */
  private static String requestedRole(UserDto dto) {
    if (dto.getRole() != null) return dto.getRole();
    if (dto.getRoles() != null && !dto.getRoles().isEmpty() && dto.getRoles().get(0) != null) return dto.getRoles().get(0).getRole();
    return null;
  }

  public UserDto saveUser(UserDto userDto) {
    // Crear una cuenta concede su rol y sus permisos individuales.
    guard.requireRole(requestedRole(userDto));
    guard.requireAll(userDto.getAdditionalPermissions());
    return userRepository.save(userDto);
  }

  public UserDto updateUser(UserDto userDto) {
    guard.requireCanManage(userDto.getUsername());
    if (Boolean.TRUE.equals(userDto.getLocked()) || Boolean.TRUE.equals(userDto.getDisabled())) {
      guard.requireNotSelf(userDto.getUsername(), "No puedes bloquear ni deshabilitar tu propia cuenta");
    }
    guard.requireRole(requestedRole(userDto));
    guard.requireAll(userDto.getAdditionalPermissions());
    return userRepository.update(userDto);
  }

  public List<UserDto> getAllUsers() {
    return userRepository.getAllUsers();
  }

  public void deleteUser(String name) {
    guard.requireNotSelf(name, "No puedes eliminar tu propia cuenta");
    guard.requireCanManage(name);
    userRepository.deleteUser(name);
  }

  public boolean exists(String name) {
    return userRepository.existsByUsername(name);
  }

  public UserDto grantPermission(UserPermissionDto dto) {
    guard.requireCanManage(dto.getUsername());
    guard.requireAll(dto.getPermission() == null ? null : List.of(dto.getPermission()));
    return userRepository.grantPermission(dto);
  }

  public UserDto revokePermission(String username, String permission) {
    guard.requireCanManage(username);
    return userRepository.revokePermission(username, permission);
  }

  public UserPermissionsDto permissionDetails(String username) {return userRepository.permissionDetails(username);}
  public List<String> permissionCatalog() {return userRepository.permissionCatalog();}

  public UserRoleDto assignRoleToUser(UserRoleDto userRoleDto) {
    guard.requireCanManage(userRoleDto.getUsername());
    guard.requireRole(userRoleDto.getRole());
    return userRoleRepository.save(userRoleDto);
  }
}
