# Módulos de reportes, alertas y filtros

Implementación del documento *Módulos de Reportes, Alertas y Filtros*. Aquí se
explica cómo quedó cada parte y qué se decidió donde el documento no lo indicaba.

## Diferencia principal con el documento

El documento suponía que ya existían las tablas de pacientes, médicos, citas e
ingresos. En este proyecto no existían, así que se crearon desde cero:

| Tabla | Situación | Campos principales |
| --- | --- | --- |
| `medico` | Nueva | id, nombre, especialidad, **username** (cuenta MEDICO, opcional) |
| `paciente` | Nueva | id, nombre, documento (único), fecha_nacimiento, telefono |
| `ingreso` | Nueva | id_paciente, id_medico, fecha_ingreso, estado, fecha_estimada_recuperacion, area, habitacion |
| `cita` | Nueva | id_paciente, id_medico, fecha_hora, motivo, estado |
| `historial_estado` | Nueva (del documento) | id_ingreso, estado, **detalle**, fecha_cambio, username |
| `alerta` | Nueva (del documento) | id_paciente, tipo, mensaje, fecha, estado, username_atiende |

Los estados, las áreas y los tipos son enums de Java. Hibernate los guarda como
texto y crea una restricción `CHECK` en PostgreSQL, como pide el documento.

## Reportes

`GET /api/reportes/pacientes/{id}` arma el reporte al pedirlo, sin tabla propia.
Usa el **ingreso más reciente**. Los días transcurridos y restantes se calculan
con la zona horaria del hospital (`app.zona-horaria`). `diasRestantes` es
negativo si la fecha estimada ya pasó, y `null` si no hay fecha estimada.

## Alertas

| Tipo | Cuándo se genera |
| --- | --- |
| CAMBIO_ESTADO | `PUT /api/ingresos/{id}` cambia el estado, el médico, el área o la habitación |
| RECUPERADO | El ingreso pasa a RECUPERADO |
| CITA | Se crea o modifica una cita |

El cambio, su historial y su alerta se guardan en **la misma transacción**: si
algo falla, no se guarda nada. Un test lo comprueba.

## Filtros

Son parámetros opcionales de `GET /api/pacientes` y `GET /api/citas` (ver el
README del backend). Para los pacientes:

- Los filtros de ingreso deben cumplirse en **un mismo ingreso** del paciente.
- `recuperacionHasta` excluye a los ya recuperados, salvo que se pida un `estado`.
- Se añadió `nombre` (búsqueda parcial), que no estaba en el documento.

## Seguridad

El documento describía dos middlewares de Node. En Spring quedaron así:

| Documento | Implementación |
| --- | --- |
| `verificarToken` | `JwtFilter` (401 si falta el token, es inválido o venció) |
| `permitirRoles` | `@PreAuthorize` con permisos por rol (403) |
| El médico solo ve sus pacientes | `AccesoMedico`, usado por los cinco endpoints |

El token lleva el usuario y una huella de su contraseña (no el rol). El rol y los
permisos se leen de la base en cada petición, así un cambio de permisos se aplica
al instante; y si la contraseña cambia, los tokens anteriores dejan de servir.

## Decisiones tomadas

| Tema | Decisión | Dónde cambiarlo |
| --- | --- | --- |
| "Sus pacientes" | Los que tienen al menos un ingreso o una cita con ese médico, también los antiguos | `PacienteRepository.esPacienteDe` y `AccesoMedico.pacienteDe` |
| Vínculo usuario–médico | `medico.username`, una cuenta MEDICO por médico | `MedicoService` |
| Cuenta MEDICO sin médico vinculado | No ve nada (403) | `AccesoMedico.medicoActual` |
| Cambios de estado | Se permite cualquier cambio; RECUPERADO cierra el ingreso | `IngresoService.actualizar` |
| Ingresos abiertos | Un paciente solo puede tener uno a la vez | `IngresoService.crear` |
| Quién crea ingresos | ADMIN y MEDICO; el médico solo a su nombre | `UserRoles` |
| Recepción | Registra pacientes y gestiona citas; busca por documento, pero no ve el listado clínico | `UserRoles` |
| Enfermería | Ve y atiende todas las alertas | `UserRoles` |
| Áreas | URGENCIAS, UCI, PEDIATRIA, CIRUGIA, MEDICINA_GENERAL, GINECOLOGIA | enum `Area` |
| Login | Con usuario, no con correo | `AuthController` |
| Avisos de alertas | Se ven en la bandeja y en el contador de Inicio; no hay notificaciones en tiempo real | Frontend |

## Pendiente o fuera de alcance

- No hay registro de auditoría de quién consulta cada reporte. Revisa si la
  normativa de datos de salud de tu país lo exige.
- No se pueden borrar ni desactivar médicos ni pacientes.
- Para producción conviene migrar el esquema con Flyway o Liquibase en lugar de
  `ddl-auto=update`.
