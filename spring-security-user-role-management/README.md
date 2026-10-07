# NEXO · Backend de gestión hospitalaria

API REST con **Java 21, Spring Boot 4.1.1 y PostgreSQL**. Incluye autenticación
JWT, un rol por usuario con permisos individuales, y los módulos de **reportes,
alertas y filtros** sobre pacientes, médicos, ingresos y citas.

El diseño de los módulos y las decisiones tomadas están en
[docs/MODULOS_HOSPITAL.md](docs/MODULOS_HOSPITAL.md).

## Inicio rápido

Requisitos: JDK 21 y Docker (o un PostgreSQL propio). Gradle Wrapper incluido.

```bash
# Desde la raíz del repositorio: PostgreSQL con la base ya creada
docker compose up -d

# Backend (perfil dev, puerto 8050)
cd spring-security-user-role-management
./gradlew bootRun          # en Windows: .\gradlew.bat bootRun
```

Hibernate crea las tablas al arrancar (`ddl-auto=update`). El bootstrap crea los
roles, los permisos y la cuenta inicial `superadmin` / `SuperAdmin123!` (solo en dev).

| Recurso | Dirección |
| --- | --- |
| API | http://localhost:8050 |
| Swagger UI | http://localhost:8050/swagger-ui.html |

En Swagger ejecuta `POST /api/auth/login`, copia el token (texto) y pégalo en
**Authorize** sin escribir `Bearer`.

## Configuración

| Archivo | Uso |
| --- | --- |
| [application.properties](src/main/resources/application.properties) | Perfil por defecto: `dev` |
| [application-dev.properties](src/main/resources/application-dev.properties) | Desarrollo, con valores por defecto |
| [application-prod.properties](src/main/resources/application-prod.properties) | Producción: todo por variables de entorno |
| [application-test.properties](src/test/resources/application-test.properties) | Tests con H2 |

| Variable | Uso | ¿Obligatoria en prod? |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión PostgreSQL | Sí |
| `JWT_SECRET` | Clave HMAC256, mínimo 32 caracteres | Sí |
| `CORS_ORIGINS` | Dominios del frontend, separados por comas | Sí |
| `JWT_EXPIRATION` | Vigencia del token (`8h` por defecto) | No |
| `JWT_ISSUER` | Emisor del token | No |
| `APP_ZONA_HORARIA` | Zona del hospital (`America/Bogota` por defecto) | No |
| `BOOTSTRAP_ADMIN_ENABLED`, `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Cuenta inicial (en prod desactivada por defecto) | No |
| `PORT` | Puerto (8050 por defecto) | No |

Producción: `SPRING_PROFILES_ACTIVE=prod`. Si falta una variable obligatoria,
la aplicación no arranca. En prod, Swagger y `show-sql` están desactivados.

## Seguridad

1. `POST /api/auth/login` devuelve un JWT (8 h) que solo lleva el usuario.
2. En cada petición, `JwtFilter` valida el token y **vuelve a cargar de la base**
   el rol, los permisos y el estado de la cuenta. Bloquear una cuenta o quitarle
   un permiso tiene efecto inmediato.
3. Cada endpoint exige su permiso con `@PreAuthorize("hasAuthority('...')")`.
4. Si el usuario es médico, `AccesoMedico` limita los datos a sus pacientes.

| Situación | Respuesta |
| --- | --- |
| Sin token, token inválido o vencido | 401 |
| Token válido sin el permiso | 403 |
| Médico consultando un paciente ajeno | 403 |

**Contraseñas:** se guardan con BCrypt y deben tener entre 8 y 72 caracteres,
con al menos una letra y un número.

**Fuerza bruta:** tras 5 intentos de login fallidos seguidos, ese usuario desde
esa IP recibe 429 durante 15 minutos (`LOGIN_MAX_INTENTOS`, `LOGIN_BLOQUEO`).

**Protección contra escalada de privilegios** (`PrivilegeGuard`): nadie puede
conceder un rol o permiso que no tiene, ni modificar una cuenta con más permisos
que la suya. Tampoco puede eliminarse, bloquearse ni deshabilitarse a sí mismo.
Los permisos nuevos se añaden automáticamente a ADMIN.

### Roles

| Rol | Permisos base |
| --- | --- |
| ADMIN | Todos |
| MEDICO | MEDICO_READ, PACIENTE_READ, INGRESO_MANAGE, CITA_READ, REPORTE_READ, ALERTA_READ, ALERTA_ATENDER (**solo sus pacientes**) |
| ENFERMERO | MEDICO_READ, PACIENTE_READ, ALERTA_READ, ALERTA_ATENDER |
| RECEPCION | MEDICO_READ, PACIENTE_MANAGE, CITA_READ, CITA_MANAGE |

Los permisos efectivos son los del rol más los individuales. El rol es
obligatorio al crear una cuenta. Para que un médico vea sus pacientes, su cuenta
(rol MEDICO) debe estar vinculada a un médico (`username` en `/api/medicos`).

## Endpoints

### Módulos del documento

| Método | Ruta | Permiso | Descripción |
| --- | --- | --- | --- |
| GET | `/api/reportes/pacientes/{id}` | REPORTE_READ | Reporte del paciente |
| GET | `/api/pacientes` | PACIENTE_READ | Búsqueda con filtros |
| GET | `/api/citas` | CITA_READ | Búsqueda por fecha y médico |
| GET | `/api/alertas` | ALERTA_READ | Listado, filtro `estado` |
| PUT | `/api/alertas/{id}` | ALERTA_ATENDER | Marcar como atendida |

Filtros de `GET /api/pacientes` (opcionales y combinables): `nombre`, `estado`,
`medico`, `area`, `ingresoDesde`, `ingresoHasta`, `recuperacionHasta`, con fechas
en formato `AAAA-MM-DD`. Ejemplos:

```text
/api/pacientes?recuperacionHasta=2026-10-13          próximos a recuperarse
/api/pacientes?ingresoDesde=2026-10-01&ingresoHasta=2026-10-31   ingresos del mes
/api/citas?fecha=2026-10-07&medico=1
```

Los listados se paginan con `?page=0&size=20` (tamaño máximo 100).

### Datos del hospital

| Método | Ruta | Permiso |
| --- | --- | --- |
| GET | `/api/medicos` | MEDICO_READ |
| POST, PUT | `/api/medicos`, `/api/medicos/{id}` | MEDICO_MANAGE |
| GET | `/api/pacientes/documento/{documento}` | PACIENTE_READ o PACIENTE_MANAGE |
| POST, PUT | `/api/pacientes`, `/api/pacientes/{id}` | PACIENTE_MANAGE |
| POST | `/api/ingresos` | INGRESO_MANAGE |
| PUT | `/api/ingresos/{id}` | INGRESO_MANAGE (genera historial y alerta) |
| POST, PUT | `/api/citas`, `/api/citas/{id}` | CITA_MANAGE (genera alerta) |

Ejemplo del flujo básico:

```json
POST /api/medicos    {"nombre":"Dra. Ruiz","especialidad":"Medicina interna","username":"druiz"}
POST /api/pacientes  {"nombre":"Ana Pérez","documento":"100","fechaNacimiento":"1990-05-01"}
POST /api/ingresos   {"pacienteId":1,"medicoId":1,"area":"UCI","habitacion":"101","fechaEstimadaRecuperacion":"2026-10-11"}
PUT  /api/ingresos/1 {"estado":"EN_TRATAMIENTO"}
POST /api/citas      {"pacienteId":1,"medicoId":1,"fechaHora":"2026-10-07T10:00:00","motivo":"Control"}
```

### Seguridad y cuentas

| Método | Ruta | Permiso |
| --- | --- | --- |
| POST | `/api/auth/login` | Público |
| GET | `/api/auth/me` | Autenticado |
| GET | `/api/user/all` | USER_READ |
| POST | `/api/user/add` | USER_CREATE y ROLE_ASSIGN |
| PUT | `/api/user/update` | USER_UPDATE (+ ROLE_ASSIGN / PERMISSION_ASSIGN según el body) |
| DELETE | `/api/user/delete/{name}` | USER_DELETE |
| POST | `/api/user/assignRole` | ROLE_ASSIGN |
| POST | `/api/user/assignPermission` | PERMISSION_ASSIGN |
| DELETE | `/api/user/{username}/permissions/{permission}` | PERMISSION_ASSIGN |
| GET | `/api/user/{username}/permissions` | USER_READ |
| GET, POST | `/api/roles` | ROLE_MANAGE |
| GET, POST | `/api/permissions` | PERMISSION_MANAGE |
| PUT, DELETE | `/api/roles/{role}/permissions/{permission}` | ROLE_MANAGE y PERMISSION_MANAGE |

## Estructura

```text
src/main/java/com/roles/usermanagement/
├── domain/        DTO y servicios de seguridad (UserRoles, PrivilegeGuard, bootstrap)
├── persistance/   Entidades y repositorios de cuentas, roles y permisos
├── web/           Controladores de seguridad y configuración (JWT, CORS, Swagger, reloj)
└── modules/
    ├── medico/    Médicos y AccesoMedico (regla "solo sus pacientes")
    ├── paciente/  Pacientes y búsqueda con filtros
    ├── ingreso/   Ingresos e historial de estados
    ├── cita/      Citas y búsqueda
    ├── alerta/    Alertas automáticas
    └── reporte/   Reporte del paciente (calculado, sin tabla)
```

Cada módulo sigue el mismo patrón: entidad, repositorio, request/response
(records), servicio `@Transactional` y controlador con `@PreAuthorize`.

## Tests

```bash
./gradlew test
```

Usan H2 en modo PostgreSQL y no tocan la base de desarrollo:

- [UsermanagementApplicationTests](src/test/java/com/roles/usermanagement/UsermanagementApplicationTests.java):
  login, JWT, bootstrap, cuentas, roles, permisos y escalada de privilegios.
- [HospitalModulesTests](src/test/java/com/roles/usermanagement/HospitalModulesTests.java):
  flujo completo por rol, restricción del médico, filtros, reporte, alertas y
  atomicidad (si un cambio falla, no se guarda ni el cambio ni la alerta).
