package com.roles.usermanagement.persistance.repository;

import com.roles.usermanagement.domain.dto.*;
import com.roles.usermanagement.domain.repository.IUserRepository;
import com.roles.usermanagement.persistance.crud.*;
import com.roles.usermanagement.persistance.entity.*;
import com.roles.usermanagement.persistance.mapper.UserMapper;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class UserRepository implements IUserRepository {
  private final UserCrudRepository users;
  private final UserRoleCrudRepository userRoles;
  private final RoleCrudRepository roles;
  private final PermissionCrudRepository permissions;
  private final UserMapper mapper;
  private final PasswordEncoder encoder;
  private final EntityManager em;

  public UserRepository(UserCrudRepository users, UserRoleCrudRepository userRoles,
      RoleCrudRepository roles, PermissionCrudRepository permissions, UserMapper mapper,
      PasswordEncoder encoder, EntityManager em) {
    this.users=users; this.userRoles=userRoles; this.roles=roles; this.permissions=permissions;
    this.mapper=mapper; this.encoder=encoder; this.em=em;
  }
  private ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
  /**
   * Política de contraseñas: de 8 a 72 caracteres (72 es el límite de BCrypt),
   * con al menos una letra y un número.
   */
  private void validatePassword(String password) {
    if(password==null || password.length()<8 || password.length()>72
        || !password.matches(".*\\p{L}.*") || !password.matches(".*\\d.*"))
      throw invalid("La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número");
  }
  private UserEntity account(String username) {
    if(username == null || username.isBlank()) throw invalid("El usuario es obligatorio");
    return users.findForUpdate(username).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND,"El usuario no existe"));
  }
  private String requestedRole(UserDto dto, boolean creating) {
    String result=dto.getRole();
    if(dto.getRoles()!=null && !dto.getRoles().isEmpty()) {
      if(dto.getRoles().size()!=1) throw invalid("Cada usuario debe tener exactamente un rol");
      UserRoleDto item=dto.getRoles().get(0);
      if(item==null || item.getRole()==null) throw invalid("El rol es obligatorio");
      if(item.getUsername()!=null && !item.getUsername().equals(dto.getUsername()))
        throw invalid("El usuario del rol debe coincidir con el usuario actualizado");
      if(result!=null && !result.equals(item.getRole())) throw invalid("role y roles deben coincidir");
      result=item.getRole();
    } else if(dto.getRoles()!=null && !creating && result==null) {
      throw invalid("El usuario debe conservar un rol; utiliza role para cambiarlo");
    }
    // El rol es obligatorio al crear: no hay rol por defecto con acceso a datos clínicos.
    if(result==null && creating) throw invalid("El rol es obligatorio: ADMIN, MEDICO, ENFERMERO o RECEPCION");
    if(result!=null && !roles.existsById(result)) throw invalid("El rol debe existir en el catálogo");
    return result;
  }
  private void setRole(UserEntity user,String role) {
    if(role==null) return;
    if(user.getRoles()==null) user.setRoles(new ArrayList<>());
    for(UserRoleEntity assignment:new ArrayList<>(user.getRoles())) {
      if(!role.equals(assignment.getRole())) {
        user.getRoles().remove(assignment); userRoles.delete(assignment);
      }
    }
    em.flush();
    if(user.getRoles().stream().noneMatch(assignment -> role.equals(assignment.getRole()))) {
      UserRoleEntity assignment=new UserRoleEntity();
      assignment.setUsername(user.getUsername()); assignment.setRole(role); assignment.setUser(user);
      user.getRoles().add(userRoles.save(assignment));
    }
  }
  private PermissionEntity permission(String name) {
    if(name==null || name.isBlank()) throw invalid("El permiso es obligatorio");
    return permissions.findById(name).orElseThrow(() -> invalid("El permiso debe existir en el catálogo"));
  }
  private void setPermissions(UserEntity user,List<String> names) {
    if(names==null) return;
    Set<PermissionEntity> selected=new LinkedHashSet<>();
    for(String name:new LinkedHashSet<>(names)) selected.add(permission(name));
    user.getAdditionalPermissions().clear(); user.getAdditionalPermissions().addAll(selected);
  }
  @Override
  @Transactional
  public UserDto save(UserDto dto) {
    if(dto.getUsername()==null || dto.getUsername().isBlank() || dto.getUsername().length()>50)
      throw invalid("El usuario es obligatorio y debe tener como máximo 50 caracteres");
    if(users.existsById(dto.getUsername())) throw new ResponseStatusException(HttpStatus.CONFLICT,"El usuario ya existe");
    if(dto.getEmail()==null || dto.getEmail().isBlank()) throw invalid("El correo es obligatorio");
    if(dto.getPassword()==null || dto.getPassword().isBlank()) throw invalid("La contraseña es obligatoria");
    validatePassword(dto.getPassword());
    String role=requestedRole(dto,true);
    UserEntity user=new UserEntity(); user.setUsername(dto.getUsername()); user.setEmail(dto.getEmail());
    user.setPassword(encoder.encode(dto.getPassword())); user.setLocked(Boolean.TRUE.equals(dto.getLocked()));
    user.setDisabled(Boolean.TRUE.equals(dto.getDisabled())); user.setRoles(new ArrayList<>());
    setPermissions(user,dto.getAdditionalPermissions());
    user=users.save(user); setRole(user,role); em.flush(); return mapper.toUserDto(user);
  }
  @Transactional
  public UserDto update(UserDto dto) {
    UserEntity user=account(dto.getUsername());
    String role=requestedRole(dto,false);
    if(dto.getEmail()!=null) {
      if(dto.getEmail().isBlank()) throw invalid("El correo no puede estar vacío");
      if(users.existsByEmailAndUsernameNot(dto.getEmail(),dto.getUsername()))
        throw new ResponseStatusException(HttpStatus.CONFLICT,"El correo ya pertenece a otro usuario");
      user.setEmail(dto.getEmail());
    }
    if(dto.getLocked()!=null) user.setLocked(dto.getLocked());
    if(dto.getDisabled()!=null) user.setDisabled(dto.getDisabled());
    if(dto.getPassword()!=null) {
      if(dto.getPassword().isBlank()) throw invalid("La contraseña no puede estar vacía");
      validatePassword(dto.getPassword());
      user.setPassword(encoder.encode(dto.getPassword()));
    }
    setRole(user,role); setPermissions(user,dto.getAdditionalPermissions());
    em.flush(); return mapper.toUserDto(user);
  }
  /**
   * El propio usuario cambia su contraseña. Devuelve false si la actual no es correcta.
   * La nueva cumple la misma política; al cambiar, los tokens anteriores dejan de servir.
   */
  @Transactional
  public boolean changeOwnPassword(String username, String current, String newPassword) {
    UserEntity user=account(username);
    if(current==null || !encoder.matches(current,user.getPassword())) return false;
    validatePassword(newPassword);
    if(encoder.matches(newPassword,user.getPassword())) throw invalid("La nueva contraseña debe ser diferente de la actual");
    user.setPassword(encoder.encode(newPassword));
    return true;
  }
  @Transactional
  public UserRoleDto assignRole(UserRoleDto dto) {
    UserEntity user=account(dto.getUsername());
    if(dto.getRole()==null || !roles.existsById(dto.getRole())) throw invalid("El rol debe existir en el catálogo");
    setRole(user,dto.getRole()); em.flush();
    UserRoleDto result=new UserRoleDto(); result.setUsername(user.getUsername()); result.setRole(dto.getRole()); return result;
  }
  @Transactional
  public UserDto grantPermission(UserPermissionDto dto) {
    UserEntity user=account(dto.getUsername()); PermissionEntity selected=permission(dto.getPermission());
    if(user.getAdditionalPermissions().stream().noneMatch(item -> item.getName().equals(selected.getName())))
      user.getAdditionalPermissions().add(selected);
    em.flush(); return mapper.toUserDto(user);
  }
  @Transactional
  public UserDto revokePermission(String username,String name) {
    UserEntity user=account(username); permission(name);
    user.getAdditionalPermissions().removeIf(item -> item.getName().equals(name));
    em.flush(); return mapper.toUserDto(user);
  }
  @Transactional(readOnly=true)
  public UserPermissionsDto permissionDetails(String username) {
    UserEntity user=users.findById(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"El usuario no existe"));
    String role=mapper.roleName(user);
    List<String> inherited=role==null ? List.of() : roles.findById(role).map(item ->
        item.getPermissions().stream().map(PermissionEntity::getName).sorted().toList()).orElse(List.of());
    List<String> additional=mapper.permissionNames(user);
    Set<String> effective=new TreeSet<>(inherited); effective.addAll(additional);
    return new UserPermissionsDto(username,role,inherited,additional,List.copyOf(effective));
  }
  public List<String> permissionCatalog() {
    List<String> names=new ArrayList<>(); permissions.findAll().forEach(item -> names.add(item.getName()));
    return names.stream().sorted().toList();
  }
  @Override public UserDto loadUserByUsername(String username) {return mapper.toUserDto(users.findById(username).orElse(null));}
  @Override public List<UserDto> getAllUsers() {
    List<UserEntity> result=new ArrayList<>(); users.findAll().forEach(result::add); return mapper.toUsersDto(result);
  }
  @Override public void deleteUser(String username) {users.deleteById(username);}
  @Override public boolean existsByUsername(String username) {return users.existsById(username);}
}
