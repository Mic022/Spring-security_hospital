package com.roles.usermanagement.web.controller;

import com.roles.usermanagement.domain.dto.LoginDto;
import com.roles.usermanagement.domain.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import com.roles.usermanagement.web.config.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Emisión de tokens JWT")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final com.roles.usermanagement.persistance.repository.UserRepository users;
    private final LoginAttemptService attempts;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          com.roles.usermanagement.persistance.repository.UserRepository users, LoginAttemptService attempts) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.users = users;
        this.attempts = attempts;
    }

    @PostMapping("/login")
    @Operation(summary = "Obtener un token JWT", description = "Valida usuario y contraseña y devuelve el token como texto. Tras 5 fallos seguidos responde 429 durante 15 minutos.")
    public ResponseEntity<String> login(@RequestBody LoginDto loginDto, HttpServletRequest request) {
        if (loginDto.getUsername() == null || loginDto.getUsername().isBlank()
                || loginDto.getPassword() == null || loginDto.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body("Usuario y contraseña son obligatorios");
        }
        // Fuerza bruta: tras varios fallos seguidos, este usuario desde esta IP queda bloqueado un tiempo.
        String clave = LoginAttemptService.clave(loginDto.getUsername(), request.getRemoteAddr());
        long minutos = attempts.minutosBloqueado(clave);
        if (minutos > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Demasiados intentos fallidos. Intenta de nuevo en " + minutos + " minuto(s)");
        }
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword()));
            attempts.exito(clave);
            return ResponseEntity.ok(jwtUtil.create(authentication.getName()));
        } catch (AuthenticationException exception) {
            attempts.fallo(clave);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales invalidas o cuenta no disponible");
        }
    }
    @GetMapping("/me")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
    @Operation(summary="Consultar rol y permisos de la sesión actual")
    public com.roles.usermanagement.domain.dto.UserPermissionsDto me(java.security.Principal principal) {
        return users.permissionDetails(principal.getName());
    }
}
