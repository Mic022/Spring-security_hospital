-- Normalize legacy accounts to one role while preserving their effective permissions.
-- Se ejecuta en cada arranque (UserRoleSchemaMigration); todo es idempotente: si ya se aplicó, no cambia nada.
DO $$
BEGIN
CREATE TABLE IF NOT EXISTS user_role_history (
                                                 username varchar(50) NOT NULL,
    role varchar(50) NOT NULL,
    granted_date timestamp NOT NULL,
    archived_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (username, role, granted_date)
    );

CREATE TABLE IF NOT EXISTS user_permission (
                                               username varchar(50) NOT NULL REFERENCES "user"(username),
    permission_name varchar(50) NOT NULL REFERENCES app_permission(name),
    PRIMARY KEY (username, permission_name)
    );

-- Impide que otra conexión cambie roles mientras dura la migración.
LOCK TABLE user_role IN SHARE ROW EXCLUSIVE MODE;

    -- Rol que conserva cada usuario: ADMIN si lo tiene; si no, el más antiguo.
    -- row_number() numera los roles de cada usuario en ese orden y se queda con el 1.
    -- La tabla temporal se borra sola al terminar la transacción (ON COMMIT DROP).
    CREATE TEMP TABLE migration_keep_role ON COMMIT DROP AS
SELECT
    username,
    role
FROM (
         SELECT
             username,
             role,
             row_number() OVER (
                PARTITION BY username
                ORDER BY
                    CASE WHEN role = 'ADMIN' THEN 0 ELSE 1 END,
                    granted_date,
                    role
            ) AS position
         FROM user_role
     ) ranked
WHERE position = 1;

-- Guarda en el historial los roles que se van a quitar.
INSERT INTO user_role_history (username, role, granted_date)
SELECT
    ur.username,
    ur.role,
    ur.granted_date
FROM user_role ur
         JOIN migration_keep_role kept
              ON kept.username = ur.username
                  AND kept.role <> ur.role
    ON CONFLICT DO NOTHING;

-- Los permisos de los roles quitados pasan a ser permisos individuales,
-- salvo los que el rol conservado ya incluye (NOT EXISTS). Así nadie pierde acceso.
INSERT INTO user_permission (username, permission_name)
SELECT DISTINCT
    ur.username,
    rp.permission_name
FROM user_role ur
         JOIN migration_keep_role kept
              ON kept.username = ur.username
                  AND kept.role <> ur.role
         JOIN role_permission rp
              ON rp.role_name = ur.role
WHERE NOT EXISTS (
    SELECT 1
    FROM role_permission inherited
    WHERE inherited.role_name = kept.role
      AND inherited.permission_name = rp.permission_name
)
    ON CONFLICT DO NOTHING;

-- Borra los roles sobrantes: cada usuario queda solo con el elegido.
DELETE FROM user_role ur
    USING migration_keep_role kept
WHERE ur.username = kept.username
  AND ur.role <> kept.role;

-- Usuarios sin ningún rol reciben CUSTOMER (rol de la plantilla original).
INSERT INTO user_role (username, role, granted_date)
SELECT
    u.username,
    'CUSTOMER',
    CURRENT_TIMESTAMP
FROM "user" u
WHERE NOT EXISTS (
    SELECT 1
    FROM user_role ur
    WHERE ur.username = u.username
);

-- Crea UNIQUE(username) en user_role solo si no existe: lo busca en el catálogo de
-- PostgreSQL (pg_constraint, contype 'u' = unique) sobre la columna username.
IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_attribute a
            ON a.attrelid = c.conrelid
           AND a.attnum = c.conkey[1]
        WHERE c.conrelid = to_regclass('user_role')
          AND c.contype = 'u'
          AND cardinality(c.conkey) = 1
          AND a.attname = 'username'
    ) THEN
ALTER TABLE user_role ADD CONSTRAINT uk_user_role_username UNIQUE (username);
END IF;
END $$;