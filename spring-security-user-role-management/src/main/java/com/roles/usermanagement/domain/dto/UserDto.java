package com.roles.usermanagement.domain.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UserDto {
  private String username;
  private String email;
  private Boolean locked;
  private Boolean disabled;
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String password;
  @Schema(description = "Rol único del usuario. Obligatorio al crear.", example = "MEDICO")
  private String role;
  @Schema(description = "Formato anterior compatible: como máximo un rol.", deprecated = true)
  private List<UserRoleDto> roles;
  @Schema(description = "Permisos individuales adicionales al rol")
  private List<String> additionalPermissions;
}
