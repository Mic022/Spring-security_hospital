package com.roles.usermanagement.domain.dto;

/** Cambio de la propia contraseña: se exige la actual para confirmar que es el titular. */
public record CambioPasswordDto(String passwordActual, String passwordNueva) {}
