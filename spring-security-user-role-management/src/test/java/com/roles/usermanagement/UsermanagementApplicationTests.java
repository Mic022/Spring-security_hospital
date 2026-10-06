package com.roles.usermanagement;







import java.net.URI;



import java.net.http.HttpClient;



import java.net.http.HttpRequest;



import java.net.http.HttpResponse;



import org.junit.jupiter.api.Test;



import org.springframework.beans.factory.annotation.Autowired;



import org.springframework.boot.test.context.SpringBootTest;



import org.springframework.core.env.Environment;



import org.springframework.jdbc.core.JdbcTemplate;



import org.springframework.security.crypto.password.PasswordEncoder;



import org.springframework.test.context.ActiveProfiles;







import static org.assertj.core.api.Assertions.assertThat;







@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)



@ActiveProfiles("test")



class UsermanagementApplicationTests {



    @Autowired com.roles.usermanagement.domain.service.SecurityBootstrapService bootstrap;



    @Autowired com.roles.usermanagement.domain.service.UserSecurityService userSecurity;



    @Autowired Environment environment;



    @Autowired JdbcTemplate jdbc;



    @Autowired PasswordEncoder passwordEncoder;



    private final HttpClient client = HttpClient.newHttpClient();







    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {



        var builder = HttpRequest.newBuilder(URI.create("http://localhost:"



                + environment.getProperty("local.server.port") + path))



                .header("Content-Type", "application/json");



        if (token != null) builder.header("Authorization", "Bearer " + token);



        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()



                : HttpRequest.BodyPublishers.ofString(body));



        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());



    }







    @Test



    void exposesSwaggerWithBearerSecurityAndPublicLogin() throws Exception {



        var docs = request("GET", "/v3/api-docs", null, null);



        assertThat(docs.statusCode()).isEqualTo(200);



        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(docs.body());



        assertThat(json.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())



                .isEqualTo("bearer");



        var paths = json.path("paths");



        assertThat(paths.size()).isEqualTo(21);



        assertThat(paths.path("/api/auth/login").path("post").path("security").isMissingNode()).isTrue();



        assertThat(paths.path("/api/user/all").path("get").path("security").get(0).has("bearerAuth")).isTrue();



        assertThat(paths.has("/api/user/add")).isTrue();



        assertThat(paths.has("/api/user/update")).isTrue();



        assertThat(paths.has("/api/user/delete/{name}")).isTrue();



        assertThat(paths.has("/api/user/assignRole")).isTrue();



        assertThat(request("GET", "/v3/api-docs/swagger-config", null, null).statusCode()).isEqualTo(200);



        assertThat(request("GET", "/v3/api-docs.yaml", null, null).statusCode()).isEqualTo(200);



        assertThat(request("GET", "/swagger-ui.html", null, null).statusCode()).isIn(301, 302);



        var ui = request("GET", "/swagger-ui/index.html", null, null);



        assertThat(ui.statusCode()).isEqualTo(200);



        assertThat(ui.body()).contains("swagger-ui");



        assertThat(request("GET", "/swagger-ui/swagger-ui-bundle.js", null, null).statusCode()).isEqualTo(200);



        assertThat(request("GET", "/swagger-ui/swagger-ui.css", null, null).statusCode()).isEqualTo(200);



        assertThat(request("GET", "/api/user/all", null, null).statusCode()).isIn(401, 403);



    }



    @Test



    void initializesAdminRolesAndPermissionsWithoutOverwritingExistingAccount() throws Exception {



        assertThat(jdbc.queryForObject("select count(*) from app_role", Integer.class)).isEqualTo(2);



        assertThat(jdbc.queryForObject("select count(*) from app_permission", Integer.class)).isEqualTo(20);



        assertThat(jdbc.queryForObject("select count(*) from role_permission where role_name='ADMIN'", Integer.class))



                .isEqualTo(20);



        assertThat(jdbc.queryForObject("select count(*) from role_permission where role_name='CUSTOMER'", Integer.class))



                .isEqualTo(1);



        String hash = jdbc.queryForObject("select password from \"user\" where username='superadmin'", String.class);



        assertThat(passwordEncoder.matches("secret", hash)).isTrue();



        jdbc.update("update \"user\" set email='changed@test.local' where username='superadmin'");



        bootstrap.initialize();



        bootstrap.initialize();



        assertThat(jdbc.queryForObject("select password from \"user\" where username='superadmin'", String.class))



                .isEqualTo(hash);



        assertThat(jdbc.queryForObject("select email from \"user\" where username='superadmin'", String.class))



                .isEqualTo("changed@test.local");



        assertThat(jdbc.queryForObject("select count(*) from user_role where username='superadmin'", Integer.class))



                .isEqualTo(1);



        assertThat(jdbc.queryForObject("select count(*) from app_role", Integer.class)).isEqualTo(2);



        assertThat(jdbc.queryForObject("select count(*) from app_permission", Integer.class)).isEqualTo(20);



        assertThat(jdbc.queryForObject("select count(*) from role_permission", Integer.class)).isEqualTo(21);



        assertThat(userSecurity.loadUserByUsername("superadmin").getAuthorities())



                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)



                .contains("ROLE_ADMIN", "USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE", "ROLE_ASSIGN", "random_order");



        assertThat(request("POST", "/api/auth/login",



                "{\"username\":\"superadmin\",\"password\":\"wrong\"}", null).statusCode()).isEqualTo(401);



        assertThat(request("POST", "/api/auth/login",



                "{\"username\":\"unknown\",\"password\":\"secret\"}", null).statusCode()).isEqualTo(401);



        assertThat(request("POST", "/api/auth/login", "{}", null).statusCode()).isEqualTo(400);



        assertThat(login("superadmin")).isNotBlank();
        var configuredToken=com.auth0.jwt.JWT.decode(login("superadmin"));
        assertThat(configuredToken.getIssuer()).isEqualTo("user-management-tests");
        assertThat(configuredToken.getExpiresAtAsInstant().getEpochSecond()-configuredToken.getIssuedAtAsInstant().getEpochSecond()).isEqualTo(1800);




    }



    @Test



    void enforcesPersistedPermissionsAndRejectsLockedLogin() throws Exception {



        String token = login("superadmin");



        jdbc.update("delete from role_permission where role_name='ADMIN' and permission_name='USER_READ'");



        try {



            assertThat(request("GET", "/api/user/all", null, token).statusCode()).isEqualTo(403);



        } finally {



            bootstrap.initialize();



        }



        assertThat(request("GET", "/api/user/all", null, token).statusCode()).isEqualTo(200);



        jdbc.update("update \"user\" set locked=true where username='superadmin'");



        try {



            assertThat(request("POST", "/api/auth/login",



                    "{\"username\":\"superadmin\",\"password\":\"secret\"}", null).statusCode()).isEqualTo(401);



        } finally {



            jdbc.update("update \"user\" set locked=false where username='superadmin'");



        }



    }



    @Test



    void updatesExistingRolesWithoutDuplicatesAndEncodesPlainPassword() throws Exception {



        String token = login("superadmin");



        String create = "{\"username\":\"putuser\",\"email\":\"putuser@test.local\","



                + "\"password\":\"old-password\",\"locked\":false,\"disabled\":false,\"roles\":[]}";



        assertThat(request("POST", "/api/user/add", create, token).statusCode()).isEqualTo(200);



        assertThat(request("POST", "/api/user/assignRole",



                "{\"username\":\"putuser\",\"role\":\"CUSTOMER\"}", token).statusCode()).isEqualTo(200);



        String update = "{\"username\":\"putuser\",\"email\":\"changed-putuser@test.local\","



                + "\"locked\":false,\"disabled\":false,\"password\":\"new-password\","



                + "\"roles\":[{\"username\":\"putuser\",\"role\":\"ADMIN\"}]}";



        assertThat(request("PUT", "/api/user/update", update, token).statusCode()).isEqualTo(200);



        var granted = jdbc.queryForObject("select granted_date from user_role where username='putuser' and role='ADMIN'", java.sql.Timestamp.class);



        assertThat(request("PUT", "/api/user/update", update, token).statusCode()).isEqualTo(200);



        assertThat(jdbc.queryForObject("select granted_date from user_role where username='putuser' and role='ADMIN'", java.sql.Timestamp.class)).isEqualTo(granted);



        assertThat(jdbc.queryForObject("select count(*) from user_role where username='putuser'", Integer.class)).isEqualTo(1);



        String hash = jdbc.queryForObject("select password from \"user\" where username='putuser'", String.class);



        assertThat(passwordEncoder.matches("new-password", hash)).isTrue();



        assertThat(request("POST", "/api/auth/login", "{\"username\":\"putuser\",\"password\":\"new-password\"}", null).statusCode()).isEqualTo(200);



        assertThat(request("POST", "/api/auth/login", "{\"username\":\"putuser\",\"password\":\"old-password\"}", null).statusCode()).isEqualTo(401);



        assertThat(request("PUT", "/api/user/update", "{\"username\":\"putuser\",\"disabled\":true}", token).statusCode()).isEqualTo(200);



        assertThat(jdbc.queryForObject("select password from \"user\" where username='putuser'", String.class)).isEqualTo(hash);



        assertThat(jdbc.queryForObject("select count(*) from user_role where username='putuser'", Integer.class)).isEqualTo(1);



        assertThat(request("PUT", "/api/user/update", "{\"username\":\"putuser\",\"disabled\":false,\"roles\":[{\"role\":\"ADMIN\"},{\"role\":\"CUSTOMER\"},{\"role\":\"ADMIN\"}]}", token).statusCode()).isEqualTo(400);



        assertThat(jdbc.queryForObject("select count(*) from user_role where username='putuser'", Integer.class)).isEqualTo(1);



        assertThat(request("PUT", "/api/user/update", "{\"username\":\"putuser\",\"roles\":[]}", token).statusCode()).isEqualTo(400);



        assertThat(jdbc.queryForObject("select count(*) from user_role where username='putuser'", Integer.class)).isEqualTo(1);



        assertThat(request("DELETE", "/api/user/delete/putuser", null, token).statusCode()).isEqualTo(200);



    }







    @Test



    void returnsConflictForDuplicateEmailAndRollsBackInvalidRole() throws Exception {



        String token = login("superadmin");



        assertThat(request("POST", "/api/user/add", "{\"username\":\"conflictuser\",\"email\":\"conflict@test.local\",\"password\":\"secret\",\"locked\":false,\"disabled\":false,\"roles\":[]}", token).statusCode()).isEqualTo(200);



        String email = jdbc.queryForObject("select email from \"user\" where username='superadmin'", String.class);



        assertThat(request("PUT", "/api/user/update", "{\"username\":\"conflictuser\",\"email\":\"" + email + "\"}", token).statusCode()).isEqualTo(409);



        assertThat(request("PUT", "/api/user/update", "{\"username\":\"conflictuser\",\"email\":\"invalid-role-change@test.local\",\"roles\":[{\"role\":\"UNKNOWN\"}]}", token).statusCode()).isEqualTo(400);



        assertThat(jdbc.queryForObject("select email from \"user\" where username='conflictuser'", String.class)).isEqualTo("conflict@test.local");



        assertThat(request("PUT", "/api/user/update", "{\"username\":\"conflictuser\",\"roles\":[{\"username\":\"anotheruser\",\"role\":\"ADMIN\"}]}", token).statusCode()).isEqualTo(400);



        assertThat(request("PUT", "/api/user/update", "{\"username\":\"conflictuser\",\"password\":\"\"}", token).statusCode()).isEqualTo(400);



        assertThat(request("DELETE", "/api/user/delete/conflictuser", null, token).statusCode()).isEqualTo(200);



    }





    @Test

    void grantsIndividualPermissionsWithoutChangingRole() throws Exception {

        String admin = login("superadmin");

        for (String name : new String[]{"extrauser", "plainuser"}) {

            assertThat(request("POST", "/api/user/add", "{\"username\":\""+name+"\",\"email\":\""+name+"@test.local\",\"password\":\"secret\"}", admin).statusCode()).isEqualTo(200);

        }

        String extra = login("extrauser"), plain = login("plainuser");

        assertThat(request("GET", "/api/user/all", null, extra).statusCode()).isEqualTo(403);

        String grant = "{\"username\":\"extrauser\",\"permission\":\"USER_READ\"}";

        assertThat(request("POST", "/api/user/assignPermission", grant, extra).statusCode()).isEqualTo(403);

        for (int i=0;i<2;i++) assertThat(request("POST", "/api/user/assignPermission", grant, admin).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select count(*) from user_permission where username='extrauser'", Integer.class)).isEqualTo(1);

        assertThat(request("GET", "/api/user/all", null, extra).statusCode()).isEqualTo(200);

        assertThat(request("GET", "/api/user/all", null, extra).body()).doesNotContain("password", "$2a$");

        assertThat(request("GET", "/api/user/all", null, plain).statusCode()).isEqualTo(403);

        assertThat(request("POST", "/api/user/add", "{}", extra).statusCode()).isEqualTo(403);

        assertThat(request("GET", "/api/user/extrauser/permissions", null, admin).body()).contains("CUSTOMER", "USER_READ", "random_order");

        assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"extrauser\",\"permission\":\"USER_UPDATE\"}", admin).statusCode()).isEqualTo(200);

        assertThat(request("PUT", "/api/user/update", "{\"username\":\"extrauser\",\"role\":\"ADMIN\"}", extra).statusCode()).isEqualTo(403);

        assertThat(request("PUT", "/api/user/update", "{\"username\":\"extrauser\",\"additionalPermissions\":[\"PERMISSION_ASSIGN\"]}", extra).statusCode()).isEqualTo(403);

        assertThat(request("PUT", "/api/user/update", "{\"username\":\"extrauser\",\"additionalPermissions\":[\"USER_READ\",\"USER_READ\"]}", admin).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select count(*) from user_permission where username='extrauser'", Integer.class)).isEqualTo(1);



        assertThat(request("DELETE", "/api/user/extrauser/permissions/USER_READ", null, admin).statusCode()).isEqualTo(200);

        assertThat(request("GET", "/api/user/all", null, extra).statusCode()).isEqualTo(403);

        assertThat(request("POST", "/api/user/assignPermission", grant, admin).statusCode()).isEqualTo(200);

        String role = "{\"username\":\"extrauser\",\"role\":\"ADMIN\"}";

        for (int i=0;i<2;i++) assertThat(request("POST", "/api/user/assignRole", role, admin).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select count(*) from user_role where username='extrauser'", Integer.class)).isEqualTo(1);

        assertThat(jdbc.queryForObject("select count(*) from user_permission where username='extrauser'", Integer.class)).isEqualTo(1);

        assertThat(request("DELETE", "/api/user/extrauser/permissions/USER_READ", null, admin).statusCode()).isEqualTo(200);

        assertThat(request("GET", "/api/user/all", null, extra).statusCode()).isEqualTo(200);

        assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"extrauser\",\"permission\":\"UNKNOWN\"}", admin).statusCode()).isEqualTo(400);

        for (String name : new String[]{"extrauser", "plainuser"}) assertThat(request("DELETE", "/api/user/delete/"+name, null, admin).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select count(*) from user_permission where username='extrauser'", Integer.class)).isZero();

    }



    private String login(String username) throws Exception {



        // Characterizes the existing login contract, including its password validation limitation.



        var response = request("POST", "/api/auth/login",



                "{\"username\":\"" + username + "\",\"password\":\"secret\"}", null);



        assertThat(response.statusCode()).isEqualTo(200);



        assertThat(response.body().split("\\.")).hasSize(3);



        return response.body();



    }







    @Test



    void preservesUserManagementAndRoleAuthorization() throws Exception {



        String adminToken = login("superadmin");



        assertThat(request("GET", "/api/user/all", null, null).statusCode()).isIn(401, 403);



        assertThat(request("GET", "/api/user/all", null, "invalid-token").statusCode()).isIn(401, 403);



        assertThat(request("GET", "/api/user/all", null, adminToken).statusCode()).isEqualTo(200);







        String user = "{\"username\":\"customer\",\"email\":\"customer@example.com\","



                + "\"password\":\"secret\",\"locked\":false,\"disabled\":false,\"roles\":[]}";



        var created = request("POST", "/api/user/add", user, adminToken);



        assertThat(created.statusCode()).isEqualTo(200);



        assertThat(created.body()).contains("customer@example.com");



        String hash = jdbc.queryForObject("select password from \"user\" where username='customer'", String.class);



        assertThat(passwordEncoder.matches("secret", hash)).isTrue();



        assertThat(request("POST", "/api/user/add", user, adminToken).statusCode()).isEqualTo(409);







        String updated = "{\"username\":\"customer\",\"email\":\"updated@example.com\"}";



        assertThat(request("PUT", "/api/user/update", updated, adminToken).statusCode()).isEqualTo(200);



        assertThat(request("GET", "/api/user/all", null, adminToken).body()).contains("updated@example.com");



        assertThat(request("POST", "/api/user/assignRole",



                "{\"username\":\"customer\",\"role\":\"CUSTOMER\"}", adminToken).statusCode()).isEqualTo(200);



        String customerToken = login("customer");



        assertThat(request("GET", "/api/user/all", null, customerToken).statusCode()).isEqualTo(403);



        assertThat(request("DELETE", "/api/user/delete/customer", null, adminToken).statusCode()).isEqualTo(200);



        assertThat(jdbc.queryForObject("select count(*) from user_role where username='customer'", Integer.class)).isZero();



        assertThat(request("DELETE", "/api/user/delete/customer", null, adminToken).statusCode()).isEqualTo(404);



        assertThat(request("PUT", "/api/user/update", user, adminToken).statusCode()).isEqualTo(404);



        assertThat(request("POST", "/api/user/assignRole",



                "{\"username\":\"customer\",\"role\":\"CUSTOMER\"}", adminToken).statusCode()).isEqualTo(404);



    }




    @Test
    void managesDynamicRolesAndPermissionsWithForeignKeys() throws Exception {
        String admin=login("superadmin");
        try {
            assertThat(request("POST", "/api/permissions", "{\"name\":\"REPORT_EXPORT\"}", admin).statusCode()).isEqualTo(201);
            assertThat(request("POST", "/api/permissions", "{\"name\":\"REPORT_EXPORT\"}", admin).statusCode()).isEqualTo(409);
            assertThat(request("POST", "/api/permissions", "{\"name\":\"invalid name\"}", admin).statusCode()).isEqualTo(400);
            assertThat(request("POST", "/api/roles", "{\"name\":\"INVALID_ROLE\",\"permissions\":[\"DOES_NOT_EXIST\"]}", admin).statusCode()).isEqualTo(404);
            assertThat(jdbc.queryForObject("select count(*) from app_role where name='INVALID_ROLE'", Integer.class)).isZero();
            assertThat(request("POST", "/api/roles", "{\"name\":\"AUDITOR\",\"permissions\":[\"USER_READ\"]}", admin).statusCode()).isEqualTo(201);
            assertThat(request("POST", "/api/roles", "{\"name\":\"AUDITOR\"}", admin).statusCode()).isEqualTo(409);
            for(int i=0;i<2;i++) assertThat(request("PUT", "/api/roles/AUDITOR/permissions/REPORT_EXPORT", null, admin).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("select count(*) from role_permission where role_name='AUDITOR'", Integer.class)).isEqualTo(2);
            assertThat(request("POST", "/api/user/add", "{\"username\":\"audittest\",\"email\":\"audit@test.local\",\"password\":\"secret\",\"role\":\"AUDITOR\"}", admin).statusCode()).isEqualTo(200);
            String auditor=login("audittest");
            assertThat(request("GET", "/api/user/all", null, auditor).statusCode()).isEqualTo(200);
            assertThat(request("POST", "/api/permissions", "{\"name\":\"UNAUTHORIZED\"}", auditor).statusCode()).isEqualTo(403);
            assertThat(request("POST", "/api/roles", "{\"name\":\"UNAUTHORIZED\"}", auditor).statusCode()).isEqualTo(403);
            assertThat(request("GET", "/api/user/audittest/permissions", null, admin).body()).contains("REPORT_EXPORT", "AUDITOR");
            assertThat(request("GET", "/api/roles", null, admin).body()).contains("AUDITOR");
            assertThat(request("GET", "/api/permissions", null, admin).body()).contains("REPORT_EXPORT");
            assertThat(request("DELETE", "/api/roles/AUDITOR/permissions/USER_READ", null, admin).statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/user/all", null, auditor).statusCode()).isEqualTo(403);
            assertThat(request("PUT", "/api/roles/AUDITOR/permissions/UNKNOWN", null, admin).statusCode()).isEqualTo(404);
            assertThat(request("DELETE", "/api/roles/ADMIN/permissions/ROLE_MANAGE", null, admin).statusCode()).isEqualTo(409);
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("insert into role_permission(role_name,permission_name) values ('AUDITOR','UNKNOWN')"));
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("insert into role_permission(role_name,permission_name) values ('UNKNOWN','USER_READ')"));
        } finally {
            jdbc.update("delete from user_role where username='audittest'");
            jdbc.update("delete from \"user\" where username='audittest'");
            jdbc.update("delete from role_permission where role_name='AUDITOR' or permission_name='REPORT_EXPORT'");
            jdbc.update("delete from app_role where name='AUDITOR'");
            jdbc.update("delete from app_permission where name='REPORT_EXPORT'");
        }
    }

    private long responseId(HttpResponse<String> response) throws Exception {
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).path("id").asLong();
    }
    @Test
    void protectsBusinessModulesAndKeepsSalesAndStockConsistent() throws Exception {
        String admin=login("superadmin");
        long customerId=0,productId=0;
        try {
            assertThat(request("GET", "/api/products", null, null).statusCode()).isIn(401,403);
            var customer=request("POST", "/api/customers", "{\"name\":\"Cliente prueba\",\"email\":\"client@test.local\",\"phone\":\"123\"}", admin);
            assertThat(customer.statusCode()).isEqualTo(201);customerId=responseId(customer);
            var product=request("POST", "/api/products", "{\"name\":\"Producto prueba\",\"sku\":\"TEST-SKU\",\"price\":12.50,\"stock\":5}", admin);
            assertThat(product.statusCode()).isEqualTo(201);productId=responseId(product);
            assertThat(request("POST", "/api/products", "{\"name\":\"X\",\"sku\":\"TEST-SKU\",\"price\":12.50,\"stock\":1}", admin).statusCode()).isEqualTo(409);
            assertThat(request("POST", "/api/products", "{\"name\":\"X\",\"sku\":\"INVALID\",\"price\":-1,\"stock\":-2}", admin).statusCode()).isEqualTo(400);
            assertThat(request("PUT", "/api/customers/"+customerId, "{\"name\":\"Actualizado\",\"email\":\"client@test.local\"}", admin).statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/customers/"+customerId,null,admin).body()).contains("Actualizado");
            assertThat(request("GET", "/api/products?page=0&size=10",null,admin).statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/products?size=101",null,admin).statusCode()).isEqualTo(400);
            assertThat(request("POST", "/api/user/add", "{\"username\":\"businessuser\",\"email\":\"business@test.local\",\"password\":\"secret\"}", admin).statusCode()).isEqualTo(200);
            String customerToken=login("businessuser");
            assertThat(request("GET", "/api/auth/me",null,customerToken).body()).contains("businessuser", "CUSTOMER", "effectivePermissions");
            assertThat(request("GET", "/api/auth/me",null,null).statusCode()).isIn(401,403);
            assertThat(request("GET", "/api/products",null,customerToken).statusCode()).isEqualTo(403);
            assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"businessuser\",\"permission\":\"PRODUCT_READ\"}",admin).statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/products",null,customerToken).statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/customers",null,customerToken).statusCode()).isEqualTo(403);
            assertThat(request("POST", "/api/products", "{\"name\":\"X\",\"sku\":\"DENIED\",\"price\":1,\"stock\":1}",customerToken).statusCode()).isEqualTo(403);
            jdbc.update("update \"user\" set disabled=true where username='businessuser'");
            assertThat(request("GET", "/api/products",null,customerToken).statusCode()).isIn(401,403);
            jdbc.update("update \"user\" set disabled=false,locked=true where username='businessuser'");
            assertThat(request("GET", "/api/products",null,customerToken).statusCode()).isIn(401,403);
            jdbc.update("update \"user\" set locked=false where username='businessuser'");
            String saleBody="{\"customerId\":"+customerId+",\"items\":[{\"productId\":"+productId+",\"quantity\":4}]}";
            assertThat(request("POST", "/api/sales",saleBody,customerToken).statusCode()).isEqualTo(403);
            try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var a=executor.submit(()->request("POST", "/api/sales",saleBody,admin));
                var b=executor.submit(()->request("POST", "/api/sales",saleBody,admin));
                var first=a.get();var second=b.get();
                assertThat(java.util.List.of(first.statusCode(),second.statusCode())).containsExactlyInAnyOrder(201,409);
                var success=first.statusCode()==201?first:second;long saleId=responseId(success);
                assertThat(success.body()).contains("50.00", "superadmin", "Actualizado");
                assertThat(jdbc.queryForObject("select stock from business_product where id=?",Integer.class,productId)).isEqualTo(1);
                assertThat(request("PUT", "/api/products/"+productId, "{\"name\":\"Precio nuevo\",\"sku\":\"TEST-SKU\",\"price\":20,\"stock\":1}", admin).statusCode()).isEqualTo(200);
                assertThat(request("GET", "/api/sales/"+saleId,null,admin).body()).contains("12.50", "Producto prueba");
                for(int i=0;i<2;i++)assertThat(request("POST", "/api/sales/"+saleId+"/cancel",null,admin).statusCode()).isEqualTo(200);
                assertThat(jdbc.queryForObject("select stock from business_product where id=?",Integer.class,productId)).isEqualTo(5);
                assertThat(request("GET", "/api/sales",null,admin).statusCode()).isEqualTo(200);
            }
            String rollback="{\"customerId\":"+customerId+",\"items\":[{\"productId\":"+productId+",\"quantity\":1},{\"productId\":9223372036854775807,\"quantity\":1}]}";
            assertThat(request("POST", "/api/sales",rollback,admin).statusCode()).isEqualTo(404);
            assertThat(jdbc.queryForObject("select stock from business_product where id=?",Integer.class,productId)).isEqualTo(5);
            assertThat(request("POST", "/api/sales",saleBody.replace("\"quantity\":4","\"quantity\":0"),admin).statusCode()).isEqualTo(400);
            assertThat(request("DELETE", "/api/products/"+productId,null,admin).statusCode()).isEqualTo(204);
            assertThat(request("POST", "/api/sales",saleBody,admin).statusCode()).isEqualTo(409);
            assertThat(request("DELETE", "/api/customers/"+customerId,null,admin).statusCode()).isEqualTo(204);
            assertThat(request("POST", "/api/sales",saleBody,admin).statusCode()).isEqualTo(409);
        } finally {
            jdbc.update("delete from business_sale_item where sale_id in (select id from business_sale where customer_id=?)",customerId);
            jdbc.update("delete from business_sale where customer_id=?",customerId);
            jdbc.update("delete from business_product where id=?",productId);
            jdbc.update("delete from business_customer where id=?",customerId);
            jdbc.update("delete from user_permission where username='businessuser'");
            jdbc.update("delete from user_role where username='businessuser'");
            jdbc.update("delete from \"user\" where username='businessuser'");
        }
    }

    /** Un usuario con permisos parciales no puede escalar privilegios. */
    @Test
    void preventsPrivilegeEscalation() throws Exception {
        String admin = login("superadmin");
        try {
            assertThat(request("POST", "/api/user/add", "{\"username\":\"helper\",\"email\":\"helper@test.local\",\"password\":\"secret\"}", admin).statusCode()).isEqualTo(200);
            for (String p : new String[]{"USER_UPDATE", "USER_DELETE", "PERMISSION_ASSIGN"}) {
                assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"helper\",\"permission\":\"" + p + "\"}", admin).statusCode()).isEqualTo(200);
            }
            String helper = login("helper");
            // No puede cambiar la contraseña, bloquear ni eliminar al administrador.
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"superadmin\",\"password\":\"hacked\"}", helper).statusCode()).isEqualTo(403);
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"superadmin\",\"locked\":true}", helper).statusCode()).isEqualTo(403);
            assertThat(request("DELETE", "/api/user/delete/superadmin", null, helper).statusCode()).isEqualTo(403);
            // No puede darse un permiso que no tiene.
            assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"helper\",\"permission\":\"ROLE_ASSIGN\"}", helper).statusCode()).isEqualTo(403);
            // Sí puede darse uno que ya tiene (no gana nada nuevo).
            assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"helper\",\"permission\":\"USER_UPDATE\"}", helper).statusCode()).isEqualTo(200);
            // Nadie puede eliminarse ni bloquearse a sí mismo.
            assertThat(request("DELETE", "/api/user/delete/superadmin", null, admin).statusCode()).isEqualTo(409);
            assertThat(request("PUT", "/api/user/update", "{\"username\":\"superadmin\",\"disabled\":true}", admin).statusCode()).isEqualTo(409);
            // Sin token: 401. Con token sin permiso: 403.
            assertThat(request("GET", "/api/user/all", null, null).statusCode()).isEqualTo(401);
            assertThat(request("GET", "/api/user/all", null, "token-invalido").statusCode()).isEqualTo(401);
            assertThat(request("GET", "/api/user/all", null, helper).statusCode()).isEqualTo(403);
        } finally {
            jdbc.update("delete from user_permission where username='helper'");
            jdbc.update("delete from user_role where username='helper'");
            jdbc.update("delete from \"user\" where username='helper'");
        }
    }
}
