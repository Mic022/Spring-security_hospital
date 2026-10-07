package com.roles.usermanagement.web.config;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class UserRoleSchemaMigration {
    @Bean
    // Se ejecuta antes que los demás ApplicationRunner, como el bootstrap de seguridad.
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationRunner migrateUserRoleSchema(DataSource dataSource, JdbcTemplate jdbc) {
        return args -> {
            try (var connection = dataSource.getConnection()) {
                // El script usa sintaxis de PostgreSQL; con H2 (tests) no se ejecuta.
                if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) return;
            }
            String sql = new ClassPathResource("db/postgresql/single-role-and-user-permissions.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            jdbc.execute(sql.replace("\uFEFF", "")); // quita la marca BOM que algunos editores a\u00F1aden al inicio
        };
    }
}
