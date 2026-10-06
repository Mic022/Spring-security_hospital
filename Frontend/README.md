# NEXO · Frontend de gestión hospitalaria

Interfaz hecha solo con **HTML, CSS y JavaScript**, sin frameworks, npm ni
compilación. Se conecta a la API del backend con JWT.

## Inicio rápido

1. Arranca PostgreSQL y el backend (ver el [README principal](../README.md)).
2. Sirve esta carpeta:

   ```bash
   python3 -m http.server 5500 --bind 127.0.0.1
   ```

3. Abre http://127.0.0.1:5500 e inicia sesión. Si la API no está en
   `http://localhost:8050`, cámbiala en **Conexión al servidor** (sin `/api`).

También puedes abrir `index.html` directamente en el navegador.

## Pantallas

| Pantalla | Funciones | Permiso para verla |
| --- | --- | --- |
| Inicio | Totales de pacientes y citas, y alertas pendientes | Ninguno |
| Pacientes | Filtros, reporte, registrar ingreso, actualizar ingreso, editar paciente | PACIENTE_READ, PACIENTE_MANAGE o INGRESO_MANAGE |
| Citas | Filtros por fecha y médico, agendar y modificar | CITA_READ o CITA_MANAGE |
| Alertas | Filtro por estado, marcar como atendida | ALERTA_READ |
| Médicos | Directorio, crear y vincular la cuenta MEDICO | MEDICO_READ o MEDICO_MANAGE |
| Usuarios, Roles, Permisos | Administración de cuentas y accesos | USER_*, ROLE_MANAGE, PERMISSION_MANAGE |

Cada usuario ve en el menú solo lo que su rol permite. Un médico solo ve a sus
pacientes porque el backend filtra los datos. Recepción no ve el listado
clínico: registra pacientes y los busca por documento (**Buscar por documento**,
o el campo *Documento del paciente* al agendar una cita).

El panel de filtros envía los campos como parámetros al backend, así que buscan
en toda la base. En cambio, el cuadro **Buscar en esta página** solo filtra las
filas cargadas.

## Sesión

- El token se guarda en `sessionStorage` y la URL de la API en `localStorage`.
  No se guardan contraseñas.
- Los menús salen de `GET /api/auth/me`. El backend vuelve a comprobar los
  permisos en cada petición: ocultar un botón no sustituye esa comprobación.
- Un 401 cierra la sesión (el token dura 8 horas). Un 403 muestra el rechazo.

## Código

| Archivo | Contenido |
| --- | --- |
| [index.html](index.html) | Login, estructura y diálogo reutilizable |
| [styles.css](styles.css) | Diseño y adaptación a pantallas pequeñas |
| [app.js](app.js) | Cliente de la API, sesión, permisos, listados y formularios |

En `app.js`:
- `sections` define las pantallas y los permisos que las hacen visibles.
- `render()` carga los datos.
- `table()` define las columnas.
- `entityForm()` arma los formularios.
- `handle()` atiende los botones.

Todos los datos del servidor se escapan con `esc()` antes de insertarlos en el HTML.

Comprobación de sintaxis, opcional: `node --check app.js`.
