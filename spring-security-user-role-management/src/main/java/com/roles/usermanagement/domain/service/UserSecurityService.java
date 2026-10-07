package com.roles.usermanagement.domain.service;

import com.roles.usermanagement.domain.dto.UserDto;
import com.roles.usermanagement.persistance.crud.RoleCrudRepository;
import com.roles.usermanagement.persistance.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserSecurityService implements UserDetailsService {
    public static final String ROLE_PREFIX = "ROLE_";
    private final UserRepository userRepository;
    private final RoleCrudRepository roleRepository;

    public UserSecurityService(UserRepository userRepository, RoleCrudRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDto user = userRepository.loadUserByUsername(username);
        if (user == null) throw new UsernameNotFoundException("User " + username + " not found.");
        // Autoridades de Spring Security: "ROLE_<rol>" (para hasRole), los permisos del rol
        // y los permisos individuales (para hasAuthority). El Set evita repetidos.
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(assignment -> {
                authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + assignment.getRole()));
                roleRepository.findById(assignment.getRole()).ifPresent(role ->
                        role.getPermissions().forEach(permission ->
                                authorities.add(new SimpleGrantedAuthority(permission.getName()))));
            });
        }
        if (user.getAdditionalPermissions() != null) {
            user.getAdditionalPermissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        // password es el hash BCrypt; Spring lo compara con la contraseña del login.
        return User.builder().username(user.getUsername()).password(user.getPassword())
                .authorities(authorities).accountLocked(Boolean.TRUE.equals(user.getLocked()))
                .disabled(Boolean.TRUE.equals(user.getDisabled())).build();
    }
}
