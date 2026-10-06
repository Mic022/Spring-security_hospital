package com.roles.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Base de los tests de integración: levanta la API en un puerto aleatorio con H2 (perfil test). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class ApiTestSupport {
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();

    /** Hace una petición HTTP real a la API; token puede ser null. */
    HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:"
                        + environment.getProperty("local.server.port") + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Inicia sesión con la contraseña de pruebas "secret" y devuelve el JWT. */
    String login(String username) throws Exception {
        var response = request("POST", "/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"secret\"}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().split("\\.")).hasSize(3);
        return response.body();
    }

    /** Crea una cuenta con el rol indicado usando el token de un administrador. */
    void createUser(String admin, String username, String role) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@test.local\","
                + "\"password\":\"secret\",\"role\":\"" + role + "\"}";
        assertThat(request("POST", "/api/user/add", body, admin).statusCode()).isEqualTo(200);
    }

    /** Borra una cuenta directamente en la base de datos (limpieza de tests). */
    void deleteUser(String username) {
        jdbc.update("delete from user_permission where username=?", username);
        jdbc.update("delete from user_role where username=?", username);
        jdbc.update("delete from \"user\" where username=?", username);
    }

    JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }
}
