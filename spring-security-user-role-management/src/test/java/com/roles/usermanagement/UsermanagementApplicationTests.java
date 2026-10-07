package com.roles.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.roles.usermanagement.domain.service.SecurityBootstrapService;
import com.roles.usermanagement.domain.service.UserRoles;
import com.roles.usermanagement.domain.service.UserSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Tests de la capa de seguridad: login, JWT, cuentas, roles y permisos. */
class UsermanagementApplicationTests extends ApiTestSupport {
    @Autowired SecurityBootstrapService bootstrap;
    @Autowired UserSecurityService userSecurity;
    @Autowired PasswordEncoder passwordEncoder;

    private static final int PERMISSIONS = UserRoles.Authority.values().length;

    @Test
    void exposesSwaggerWithBearerSecurityAndPublicLogin() throws Exception {
        var docs = request("GET", "/v3/api-docs", null, null);
        assertThat(docs.statusCode()).isEqualTo(200);
        var json = json(docs);
        assertThat(json.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        var paths = json.path("paths");
        assertThat(paths.path("/api/auth/login").path("post").path("security").isMissingNode()).isTrue();
        assertThat(paths.path("/api/user/all").path("get").path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(request("GET", "/swagger-ui/index.html", null, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/api/user/all", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void initializesRolesAndPermissionsWithoutOverwritingExistingAccount() throws Exception {
        // 4 roles del hospital; ADMIN tiene todos los permisos.
        assertThat(jdbc.queryForObject("select count(*) from app_role", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from app_permission", Integer.class)).isEqualTo(PERMISSIONS);
        assertThat(jdbc.queryForObject("select count(*) from role_permission where role_name='ADMIN'", Integer.class)).isEqualTo(PERMISSIONS);
        assertThat(jdbc.queryForObject("select count(*) from role_permission where role_name='MEDICO'", Integer.class))
                .isEqualTo(UserRoles.Role.MEDICO.permissions().size());

        String hash = jdbc.queryForObject("select password from \"user\" where username='superadmin'", String.class);
        assertThat(passwordEncoder.matches("Secret123", hash)).isTrue();
        jdbc.update("update \"user\" set email='changed@test.local' where username='superadmin'");
        // Volver a ejecutar el bootstrap no cambia la cuenta existente ni duplica datos.
        bootstrap.initialize();
        bootstrap.initialize();
        assertThat(jdbc.queryForObject("select password from \"user\" where username='superadmin'", String.class)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("select email from \"user\" where username='superadmin'", String.class)).isEqualTo("changed@test.local");
        assertThat(jdbc.queryForObject("select count(*) from app_role", Integer.class)).isEqualTo(4);
        assertThat(userSecurity.loadUserByUsername("superadmin").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN", "USER_READ", "ROLE_ASSIGN", "REPORTE_READ", "ALERTA_ATENDER");

        assertThat(request("POST", "/api/auth/login", "{\"username\":\"superadmin\",\"password\":\"wrong\"}", null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/auth/login", "{\"username\":\"unknown\",\"password\":\"Secret123\"}", null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/auth/login", "{}", null).statusCode()).isEqualTo(400);
        var token = com.auth0.jwt.JWT.decode(login("superadmin"));
        assertThat(token.getIssuer()).isEqualTo("user-management-tests");
        assertThat(token.getExpiresAtAsInstant().getEpochSecond() - token.getIssuedAtAsInstant().getEpochSecond()).isEqualTo(1800);
    }

    @Test
    void enforcesPersistedPermissionsAndRejectsLockedLogin() throws Exception {
        String token = login("superadmin");
        // Los permisos se leen de la base en cada petición: quitar uno tiene efecto inmediato.
        jdbc.update("delete from role_permission where role_name='ADMIN' and permission_name='USER_READ'");
        try {
            assertThat(request("GET", "/api/user/all", null, token).statusCode()).isEqualTo(403);
        } finally {
            bootstrap.initialize();
        }
        assertThat(request("GET", "/api/user/all", null, token).statusCode()).isEqualTo(200);
        jdbc.update("update \"user\" set locked=true where username='superadmin'");
        try {
            assertThat(request("POST", "/api/auth/login", "{\"username\":\"superadmin\",\"password\":\"Secret123\"}", null).statusCode()).isEqualTo(401);
            assertThat(request("GET", "/api/user/all", null, token).statusCode()).isEqualTo(401);
        } finally {
            jdbc.update("update \"user\" set locked=false where username='superadmin'");
        }
    }

    @Test
    void createsAndUpdatesAccountsWithASingleRole() throws Exception {
        String admin = login("superadmin");
        try {
            // El rol es obligatorio al crear.
            assertThat(request("POST", "/api/user/add", "{\"username\":\"norole\",\"email\":\"norole@test.local\",\"password\":\"Secret123\"}", admin).statusCode()).isEqualTo(400);
            createUser(admin, "putuser", "RECEPCION");
            assertThat(request("POST", "/api/user/add", "{\"username\":\"putuser\",\"email\":\"x@test.local\",\"password\":\"Secret123\",\"role\":\"RECEPCION\"}", admin).statusCode()).isEqualTo(409);
            assertThat(request("POST", "/api/user/assignRole", "{\"username\":\"putuser\",\"role\":\"ENFERMERO\"}", admin).statusCode()).isEqualTo(200);
            String update = "{\"username\":\"putuser\",\"email\":\"changed@test.local\",\"password\":\"NewPassword1\",\"role\":\"MEDICO\"}";
            assertThat(request("PUT", "/api/user/update", update, admin).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("select count(*) from user_role where username='putuser'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select role from user_role where username='putuser'", String.class)).isEqualTo("MEDICO");
            String hash = jdbc.queryForObject("select password from \"user\" where username='putuser'", String.class);
            assertThat(passwordEncoder.matches("NewPassword1", hash)).isTrue();
            assertThat(request("POST", "/api/auth/login", "{\"username\":\"putuser\",\"password\":\"NewPassword1\"}", null).statusCode()).isEqualTo(200);
            // Campos omitidos se conservan.
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"putuser\",\"disabled\":true}", admin).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("select password from \"user\" where username='putuser'", String.class)).isEqualTo(hash);
            // Validaciones.
            String superEmail = jdbc.queryForObject("select email from \"user\" where username='superadmin'", String.class);
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"putuser\",\"email\":\"" + superEmail + "\"}", admin).statusCode()).isEqualTo(409);
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"putuser\",\"role\":\"UNKNOWN\"}", admin).statusCode()).isEqualTo(400);
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"putuser\",\"password\":\"\"}", admin).statusCode()).isEqualTo(400);
            // Política de contraseñas: mínimo 8 caracteres con letras y números.
            for (String debil : new String[]{"corta1", "solotexto", "12345678"}) {
                assertThat(request("PUT", "/api/user/update", "{\"username\":\"putuser\",\"password\":\"" + debil + "\"}", admin).statusCode()).isEqualTo(400);
                assertThat(request("POST", "/api/user/add", "{\"username\":\"weak\",\"email\":\"weak@test.local\",\"password\":\"" + debil + "\",\"role\":\"RECEPCION\"}", admin).statusCode()).isEqualTo(400);
            }
            assertThat(request("GET", "/api/user/all", null, admin).body()).doesNotContain("password", "$2a$");
            assertThat(request("DELETE", "/api/user/delete/putuser", null, admin).statusCode()).isEqualTo(200);
            assertThat(request("DELETE", "/api/user/delete/putuser", null, admin).statusCode()).isEqualTo(404);
        } finally {
            deleteUser("putuser");
        }
    }

    @Test
    void grantsIndividualPermissionsWithoutChangingRole() throws Exception {
        String admin = login("superadmin");
        try {
            createUser(admin, "extrauser", "RECEPCION");
            String extra = login("extrauser");
            assertThat(request("GET", "/api/user/all", null, extra).statusCode()).isEqualTo(403);
            String grant = "{\"username\":\"extrauser\",\"permission\":\"USER_READ\"}";
            assertThat(request("POST", "/api/user/assignPermission", grant, extra).statusCode()).isEqualTo(403);
            for (int i = 0; i < 2; i++) assertThat(request("POST", "/api/user/assignPermission", grant, admin).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("select count(*) from user_permission where username='extrauser'", Integer.class)).isEqualTo(1);
            assertThat(request("GET", "/api/user/all", null, extra).statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/user/extrauser/permissions", null, admin).body()).contains("RECEPCION", "USER_READ", "CITA_MANAGE");
            assertThat(request("DELETE", "/api/user/extrauser/permissions/USER_READ", null, admin).statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/user/all", null, extra).statusCode()).isEqualTo(403);
            assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"extrauser\",\"permission\":\"UNKNOWN\"}", admin).statusCode()).isEqualTo(400);
        } finally {
            deleteUser("extrauser");
        }
    }

    @Test
    void managesDynamicRolesAndPermissions() throws Exception {
        String admin = login("superadmin");
        try {
            assertThat(request("POST", "/api/permissions", "{\"name\":\"REPORT_EXPORT\"}", admin).statusCode()).isEqualTo(201);
            assertThat(request("POST", "/api/permissions", "{\"name\":\"REPORT_EXPORT\"}", admin).statusCode()).isEqualTo(409);
            assertThat(request("POST", "/api/permissions", "{\"name\":\"invalid name\"}", admin).statusCode()).isEqualTo(400);
            assertThat(request("POST", "/api/roles", "{\"name\":\"INVALID_ROLE\",\"permissions\":[\"DOES_NOT_EXIST\"]}", admin).statusCode()).isEqualTo(404);
            assertThat(request("POST", "/api/roles", "{\"name\":\"AUDITOR\",\"permissions\":[\"USER_READ\"]}", admin).statusCode()).isEqualTo(201);
            // El permiso nuevo se agregó a ADMIN, por eso el administrador puede repartirlo.
            assertThat(request("PUT", "/api/roles/AUDITOR/permissions/REPORT_EXPORT", null, admin).statusCode()).isEqualTo(200);
            createUser(admin, "audittest", "AUDITOR");
            String auditor = login("audittest");
            assertThat(request("GET", "/api/user/all", null, auditor).statusCode()).isEqualTo(200);
            assertThat(request("POST", "/api/roles", "{\"name\":\"UNAUTHORIZED\"}", auditor).statusCode()).isEqualTo(403);
            assertThat(request("DELETE", "/api/roles/AUDITOR/permissions/USER_READ", null, admin).statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/user/all", null, auditor).statusCode()).isEqualTo(403);
            // Los permisos base de los roles del sistema están reservados.
            assertThat(request("DELETE", "/api/roles/ADMIN/permissions/ROLE_MANAGE", null, admin).statusCode()).isEqualTo(409);
            assertThat(request("DELETE", "/api/roles/MEDICO/permissions/REPORTE_READ", null, admin).statusCode()).isEqualTo(409);
        } finally {
            deleteUser("audittest");
            jdbc.update("delete from role_permission where role_name='AUDITOR' or permission_name='REPORT_EXPORT'");
            jdbc.update("delete from app_role where name='AUDITOR'");
            jdbc.update("delete from app_permission where name='REPORT_EXPORT'");
        }
    }

    /** Un usuario con permisos parciales no puede escalar privilegios. */
    @Test
    void preventsPrivilegeEscalation() throws Exception {
        String admin = login("superadmin");
        try {
            createUser(admin, "helper", "RECEPCION");
            for (String p : new String[]{"USER_UPDATE", "USER_DELETE", "PERMISSION_ASSIGN"}) {
                assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"helper\",\"permission\":\"" + p + "\"}", admin).statusCode()).isEqualTo(200);
            }
            String helper = login("helper");
            // No puede cambiar la contraseña, bloquear ni eliminar al administrador.
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"superadmin\",\"password\":\"Hacked123\"}", helper).statusCode()).isEqualTo(403);
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"superadmin\",\"locked\":true}", helper).statusCode()).isEqualTo(403);
            assertThat(request("DELETE", "/api/user/delete/superadmin", null, helper).statusCode()).isEqualTo(403);
            // No puede darse un permiso que no tiene; sí uno que ya tiene.
            assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"helper\",\"permission\":\"ROLE_ASSIGN\"}", helper).statusCode()).isEqualTo(403);
            assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"helper\",\"permission\":\"USER_UPDATE\"}", helper).statusCode()).isEqualTo(200);
            // Nadie puede eliminarse ni bloquearse a sí mismo.
            assertThat(request("DELETE", "/api/user/delete/superadmin", null, admin).statusCode()).isEqualTo(409);
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"superadmin\",\"disabled\":true}", admin).statusCode()).isEqualTo(409);
            // Sin token o con token inválido: 401. Con token sin permiso: 403.
            assertThat(request("GET", "/api/user/all", null, null).statusCode()).isEqualTo(401);
            assertThat(request("GET", "/api/user/all", null, "token-invalido").statusCode()).isEqualTo(401);
            assertThat(request("GET", "/api/user/all", null, helper).statusCode()).isEqualTo(403);
        } finally {
            deleteUser("helper");
        }
    }
}
