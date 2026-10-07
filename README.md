# NEXO · Gestión hospitalaria

Plataforma web con **backend Java 21 / Spring Boot 4.1.1 / PostgreSQL** y
**frontend HTML, CSS y JavaScript**. Implementa los módulos de **reportes,
alertas y filtros** sobre pacientes, médicos, ingresos y citas, con
autenticación JWT y permisos por rol (administrador, médico, enfermero y recepción).

Basada en la plantilla de seguridad de Cristian Díaz (ver autoría al final).

## Estructura

```text
├── docker-compose.yml                   PostgreSQL, backend y frontend con un solo comando
├── spring-security-user-role-management/
│   ├── Dockerfile                       Imagen del backend (compila el jar dentro de Docker)
│   ├── README.md                        Guía del backend: configuración y endpoints
│   ├── docs/MODULOS_HOSPITAL.md         Diseño de los módulos y decisiones tomadas
│   └── src/                             Código y tests
└── Frontend/
    ├── README.md                        Guía de la interfaz
    └── index.html, app.js, styles.css
```

## Inicio rápido

Solo necesitas **Docker** (no hace falta instalar Java). Desde la raíz del repositorio:

```bash
docker compose up --build
```

La primera vez tarda unos minutos (descarga imágenes y dependencias). Cuando el
backend muestre `Started UsermanagementApplication`, abre:

| Recurso | Dirección |
| --- | --- |
| Frontend | http://localhost:5500 |
| Swagger UI | http://localhost:8050/swagger-ui.html |
| API | http://localhost:8050 |

Al arrancar por primera vez se cargan **datos de demostración**: médicos,
4 pacientes, ingresos en distintos estados, citas y alertas. Cuentas disponibles:

| Usuario | Contraseña | Rol | Qué ve |
| --- | --- | --- | --- |
| `superadmin` | `SuperAdmin123!` | ADMIN | Todo |
| `druiz` | `Medico123!` | MEDICO | Solo sus 2 pacientes (Ana y Carla) |
| `dgomez` | `Medico123!` | MEDICO | Solo sus pacientes (Bruno y Diego) |
| `enfermero` | `Enfermero123!` | ENFERMERO | Pacientes con filtros y alertas |
| `recepcion` | `Recepcion123!` | RECEPCION | Citas; registra pacientes |

Para detener: `Ctrl+C`, o `docker compose down`. Para borrar también los datos y
volver a empezar con la demo: `docker compose down -v`.

### Sin Docker para el backend (desarrollo)

Con JDK 21 instalado, puedes levantar solo la base en Docker y el backend con Gradle:

```bash
docker compose up -d postgres            # PostgreSQL en localhost:5433
cd spring-security-user-role-management
./gradlew bootRun                        # en Windows: .\gradlew.bat bootRun
```

Y servir el frontend con cualquier servidor estático, por ejemplo
`python3 -m http.server 5500` dentro de `Frontend`.

## Qué puede hacer cada rol

| Rol | Reportes | Alertas | Filtros de pacientes | Citas |
| --- | --- | --- | --- | --- |
| Administrador | Sí | Sí | Sí | Sí |
| Médico | Sus pacientes | Sus pacientes | Sus pacientes | Sus pacientes (consulta) |
| Enfermero | No | Sí | Sí | No |
| Recepción | No | No | No (registra pacientes y los busca por documento) | Sí |

Además, el administrador gestiona cuentas, roles, permisos y médicos. Los
médicos registran ingresos a su nombre y actualizan su estado.

## Flujo para probar

1. Entra como `druiz`: en **Pacientes** solo aparecen sus pacientes. Abre el
   **Reporte** de Ana (días transcurridos y restantes, citas, historial).
2. Cambia el estado del ingreso de Ana con **Actualizar ingreso**.
3. Entra como `enfermero`: en **Alertas** aparece ese cambio; márcalo como atendido.
4. Entra como `recepcion`: agenda una cita con el documento `1004` (genera otra alerta).
5. Como `superadmin`, en **Pacientes** prueba los filtros, por ejemplo
   *Recuperación hasta* = hoy + 7 días (pacientes próximos a recuperarse).

## Verificación

```bash
cd spring-security-user-role-management && ./gradlew test
```

Los tests cubren la seguridad (incluida la escalada de privilegios) y el flujo
completo de los módulos del hospital por rol. Además, se probó contra PostgreSQL
real y en el navegador con los cuatro roles.

![NEXO: pantalla de pacientes con datos de prueba](Frontend/vista-previa.png)

---
## 👨‍💻 Autor

**Ing. Cristian Díaz**

<p align="center">
  <img width="300" src="https://i.imgur.com/a7YBcsp.png">
</p>
