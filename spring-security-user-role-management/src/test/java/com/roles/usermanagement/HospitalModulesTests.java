package com.roles.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Tests de los módulos del hospital: reportes, alertas, filtros y la restricción del médico. */
class HospitalModulesTests extends ApiTestSupport {
    @Autowired Clock clock;

    private static final String[] USUARIOS = {"medico1", "medico2", "medico3", "enfermero", "recepcion"};

    @AfterEach
    void limpiar() {
        for (String tabla : new String[]{"alerta", "historial_estado", "cita", "ingreso", "paciente", "medico"}) {
            jdbc.update("delete from " + tabla);
        }
        for (String u : USUARIOS) deleteUser(u);
    }

    /** Total de registros de una respuesta paginada (admite ambos formatos de Spring Data). */
    private long total(HttpResponse<String> response) {
        JsonNode json = json(response);
        return json.has("totalElements") ? json.path("totalElements").asLong() : json.path("page").path("totalElements").asLong();
    }

    private long crear(String path, String body, String token) throws Exception {
        var response = request("POST", path, body, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response).path("id").asLong();
    }

    private int alertas() {
        return jdbc.queryForObject("select count(*) from alerta", Integer.class);
    }

    @Test
    void flujoCompletoConPermisosPorRol() throws Exception {
        String admin = login("superadmin");
        createUser(admin, "medico1", "MEDICO");
        createUser(admin, "medico2", "MEDICO");
        createUser(admin, "medico3", "MEDICO");
        createUser(admin, "enfermero", "ENFERMERO");
        createUser(admin, "recepcion", "RECEPCION");
        String med1 = login("medico1"), med2 = login("medico2"), med3 = login("medico3");
        String enf = login("enfermero"), rec = login("recepcion");
        LocalDate hoy = LocalDate.now(clock);

        // --- Médicos: solo se puede vincular una cuenta con rol MEDICO ---
        long m1 = crear("/api/medicos", "{\"nombre\":\"Dra. Ruiz\",\"especialidad\":\"Medicina interna\",\"username\":\"medico1\"}", admin);
        long m2 = crear("/api/medicos", "{\"nombre\":\"Dr. Gómez\",\"especialidad\":\"Pediatría\",\"username\":\"medico2\"}", admin);
        assertThat(request("POST", "/api/medicos", "{\"nombre\":\"X\",\"especialidad\":\"Y\",\"username\":\"recepcion\"}", admin).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/medicos", "{\"nombre\":\"X\",\"especialidad\":\"Y\",\"username\":\"medico1\"}", admin).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/medicos", "{\"nombre\":\"X\",\"especialidad\":\"Y\"}", rec).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/medicos", null, rec).statusCode()).isEqualTo(200);

        // --- Pacientes: recepción registra, pero no usa los filtros clínicos ---
        long a = crear("/api/pacientes", "{\"nombre\":\"Ana Pérez\",\"documento\":\"100\",\"fechaNacimiento\":\"1990-05-01\"}", rec);
        long b = crear("/api/pacientes", "{\"nombre\":\"Bruno Díaz\",\"documento\":\"200\"}", rec);
        assertThat(request("POST", "/api/pacientes", "{\"nombre\":\"Otro\",\"documento\":\"100\"}", rec).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/pacientes", "{\"nombre\":\"\",\"documento\":\"300\"}", rec).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/pacientes", null, rec).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/pacientes/documento/100", null, rec).statusCode()).isEqualTo(200);

        // --- Ingresos: un ingreso abierto por paciente; el médico solo ingresa a su nombre ---
        String fecha = hoy.plusDays(5).toString();
        long ia = crear("/api/ingresos", "{\"pacienteId\":" + a + ",\"medicoId\":" + m1 + ",\"area\":\"UCI\",\"habitacion\":\"101\",\"fechaEstimadaRecuperacion\":\"" + fecha + "\"}", admin);
        assertThat(request("POST", "/api/ingresos", "{\"pacienteId\":" + a + ",\"medicoId\":" + m1 + ",\"area\":\"UCI\",\"habitacion\":\"102\"}", admin).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/ingresos", "{\"pacienteId\":" + b + ",\"medicoId\":" + m2 + ",\"area\":\"PEDIATRIA\",\"habitacion\":\"7\"}", med1).statusCode()).isEqualTo(403);
        crear("/api/ingresos", "{\"pacienteId\":" + b + ",\"medicoId\":" + m2 + ",\"area\":\"PEDIATRIA\",\"habitacion\":\"7\"}", admin);
        assertThat(request("POST", "/api/ingresos", "{\"pacienteId\":" + b + ",\"medicoId\":" + m2 + ",\"area\":\"OTRA\",\"habitacion\":\"7\"}", admin).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/ingresos", "{\"pacienteId\":" + b + ",\"medicoId\":" + m2 + ",\"area\":\"UCI\",\"habitacion\":\"1\"}", enf).statusCode()).isEqualTo(403);

        // --- Recepción solo ve datos personales, nunca el ingreso (información clínica) ---
        assertThat(json(request("GET", "/api/pacientes/documento/100", null, rec)).path("ultimoIngreso").isNull()).isTrue();
        var editado = request("PUT", "/api/pacientes/" + a, "{\"nombre\":\"Ana Pérez\",\"documento\":\"100\",\"telefono\":\"300\"}", rec);
        assertThat(editado.statusCode()).isEqualTo(200);
        assertThat(json(editado).path("ultimoIngreso").isNull()).isTrue();
        assertThat(json(request("GET", "/api/pacientes/documento/100", null, admin)).path("ultimoIngreso").path("area").asText()).isEqualTo("UCI");

        // --- Restricción del médico: cada uno ve solo a sus pacientes ---
        assertThat(total(request("GET", "/api/pacientes", null, med1))).isEqualTo(1);
        assertThat(request("GET", "/api/pacientes", null, med1).body()).contains("Ana Pérez").doesNotContain("Bruno");
        assertThat(total(request("GET", "/api/pacientes", null, med2))).isEqualTo(1);
        assertThat(total(request("GET", "/api/pacientes", null, enf))).isEqualTo(2);
        assertThat(request("GET", "/api/pacientes", null, med3).statusCode()).isEqualTo(403); // MEDICO sin médico vinculado

        // --- Filtros combinables ---
        assertThat(total(request("GET", "/api/pacientes?estado=INGRESADO", null, admin))).isEqualTo(2);
        assertThat(total(request("GET", "/api/pacientes?area=UCI", null, admin))).isEqualTo(1);
        assertThat(total(request("GET", "/api/pacientes?medico=" + m2, null, admin))).isEqualTo(1);
        assertThat(total(request("GET", "/api/pacientes?recuperacionHasta=" + hoy.plusDays(7), null, admin))).isEqualTo(1);
        assertThat(total(request("GET", "/api/pacientes?ingresoDesde=" + hoy + "&ingresoHasta=" + hoy, null, admin))).isEqualTo(2);
        assertThat(total(request("GET", "/api/pacientes?ingresoHasta=" + hoy.minusDays(1), null, admin))).isZero();
        assertThat(total(request("GET", "/api/pacientes?nombre=bru&area=PEDIATRIA", null, admin))).isEqualTo(1);
        assertThat(request("GET", "/api/pacientes?size=101", null, admin).statusCode()).isEqualTo(400);

        // --- Reporte: días calculados, y el médico solo ve el de sus pacientes ---
        var reporte = request("GET", "/api/reportes/pacientes/" + a, null, med1);
        assertThat(reporte.statusCode()).isEqualTo(200);
        assertThat(json(reporte).path("diasRestantes").asLong()).isEqualTo(5);
        assertThat(json(reporte).path("diasTranscurridos").asLong()).isZero();
        assertThat(json(reporte).path("medicoResponsable").asText()).isEqualTo("Dra. Ruiz");
        assertThat(request("GET", "/api/reportes/pacientes/" + b, null, med1).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/reportes/pacientes/" + a, null, enf).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/reportes/pacientes/999999", null, admin).statusCode()).isEqualTo(404);

        // --- Cambio de ingreso: historial + alerta CAMBIO_ESTADO; otro médico no puede modificarlo ---
        assertThat(request("PUT", "/api/ingresos/" + ia, "{\"estado\":\"EN_TRATAMIENTO\",\"habitacion\":\"205\"}", med1).statusCode()).isEqualTo(200);
        assertThat(request("PUT", "/api/ingresos/" + ia, "{\"estado\":\"EN_RECUPERACION\"}", med2).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from historial_estado where id_ingreso=?", Integer.class, ia)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select tipo from alerta", String.class)).isEqualTo("CAMBIO_ESTADO");

        // --- Atomicidad: si una parte falla, no se guarda nada (ni el cambio ni la alerta) ---
        assertThat(request("PUT", "/api/ingresos/" + ia, "{\"estado\":\"EN_RECUPERACION\",\"medicoId\":999999}", admin).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select estado from ingreso where id=?", String.class, ia)).isEqualTo("EN_TRATAMIENTO");
        assertThat(alertas()).isEqualTo(1);

        // --- Citas: recepción agenda (alerta CITA); filtros por fecha y médico ---
        String manana = hoy.plusDays(1).toString();
        long cita = crear("/api/citas", "{\"pacienteId\":" + a + ",\"medicoId\":" + m1 + ",\"fechaHora\":\"" + manana + "T10:00:00\",\"motivo\":\"Control\"}", rec);
        assertThat(alertas()).isEqualTo(2);
        assertThat(total(request("GET", "/api/citas?fecha=" + manana, null, rec))).isEqualTo(1);
        assertThat(total(request("GET", "/api/citas?fecha=" + hoy, null, rec))).isZero();
        assertThat(total(request("GET", "/api/citas?medico=" + m2, null, rec))).isZero();
        assertThat(total(request("GET", "/api/citas", null, med1))).isEqualTo(1);
        assertThat(total(request("GET", "/api/citas", null, med2))).isZero();
        assertThat(request("GET", "/api/citas", null, enf).statusCode()).isEqualTo(403);
        assertThat(request("PUT", "/api/citas/" + cita, "{\"pacienteId\":" + a + ",\"medicoId\":" + m1 + ",\"fechaHora\":\"" + manana + "T11:30:00\",\"estado\":\"PROGRAMADA\"}", rec).statusCode()).isEqualTo(200);
        assertThat(alertas()).isEqualTo(3);

        // --- Alertas: el médico solo ve y atiende las de sus pacientes ---
        assertThat(total(request("GET", "/api/alertas?estado=PENDIENTE", null, enf))).isEqualTo(3);
        assertThat(total(request("GET", "/api/alertas", null, med1))).isEqualTo(3);
        assertThat(total(request("GET", "/api/alertas", null, med2))).isZero();
        assertThat(request("GET", "/api/alertas", null, rec).statusCode()).isEqualTo(403);
        long alerta = jdbc.queryForObject("select min(id) from alerta", Long.class);
        assertThat(request("PUT", "/api/alertas/" + alerta, null, med2).statusCode()).isEqualTo(403);
        var atendida = request("PUT", "/api/alertas/" + alerta, null, enf);
        assertThat(atendida.statusCode()).isEqualTo(200);
        assertThat(json(atendida).path("estado").asText()).isEqualTo("ATENDIDA");
        assertThat(json(atendida).path("usernameAtiende").asText()).isEqualTo("enfermero");
        assertThat(total(request("GET", "/api/alertas?estado=PENDIENTE", null, enf))).isEqualTo(2);

        // --- Recuperado: alerta RECUPERADO y el ingreso queda cerrado ---
        assertThat(request("PUT", "/api/ingresos/" + ia, "{\"estado\":\"RECUPERADO\"}", med1).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from alerta where tipo='RECUPERADO'", Integer.class)).isEqualTo(1);
        assertThat(request("PUT", "/api/ingresos/" + ia, "{\"habitacion\":\"300\"}", med1).statusCode()).isEqualTo(409);
        assertThat(total(request("GET", "/api/pacientes?recuperacionHasta=" + hoy.plusDays(7), null, admin))).isZero();
        var historial = json(request("GET", "/api/reportes/pacientes/" + a, null, admin)).path("historial");
        assertThat(historial.size()).isEqualTo(3);
        assertThat(historial.get(2).path("estado").asText()).isEqualTo("RECUPERADO");

        // --- Un médico no puede apropiarse de un paciente ajeno creándole un ingreso ---
        long e = crear("/api/pacientes", "{\"nombre\":\"Elena Ríos\",\"documento\":\"500\"}", rec);
        assertThat(request("POST", "/api/ingresos", "{\"pacienteId\":" + e + ",\"medicoId\":" + m1 + ",\"area\":\"UCI\",\"habitacion\":\"9\"}", med1).statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/reportes/pacientes/" + e, null, med1).statusCode()).isEqualTo(403);
        // Con una cita agendada por recepción, el paciente pasa a ser suyo y ya puede ingresarlo.
        crear("/api/citas", "{\"pacienteId\":" + e + ",\"medicoId\":" + m1 + ",\"fechaHora\":\"" + manana + "T15:00:00\"}", rec);
        crear("/api/ingresos", "{\"pacienteId\":" + e + ",\"medicoId\":" + m1 + ",\"area\":\"UCI\",\"habitacion\":\"9\"}", med1);
        // Un paciente con ingreso abierto de otro médico: 403, sin revelar que está hospitalizado (no 409).
        assertThat(request("POST", "/api/ingresos", "{\"pacienteId\":" + b + ",\"medicoId\":" + m1 + ",\"area\":\"UCI\",\"habitacion\":\"9\"}", med1).statusCode()).isEqualTo(403);
    
        // --- Un médico con permisos extra sigue limitado a sus pacientes ---
        assertThat(request("POST", "/api/user/assignPermission", "{\"username\":\"medico1\",\"permission\":\"PACIENTE_MANAGE\"}", admin).statusCode()).isEqualTo(200);
        assertThat(request("PUT", "/api/pacientes/" + b, "{\"nombre\":\"Cambiado\",\"documento\":\"200\"}", med1).statusCode()).isEqualTo(403);
        assertThat(request("PUT", "/api/pacientes/" + a, "{\"nombre\":\"Ana Pérez\",\"documento\":\"100\"}", med1).statusCode()).isEqualTo(200);
    }

    /** Varias peticiones de ingreso simultáneas para el mismo paciente: solo una se registra. */
    @Test
    void ingresosSimultaneosDejanUnSoloIngresoAbierto() throws Exception {
        String admin = login("superadmin");
        long medico = crear("/api/medicos", "{\"nombre\":\"Dra. Ruiz\",\"especialidad\":\"Medicina interna\"}", admin);
        long paciente = crear("/api/pacientes", "{\"nombre\":\"Ana Pérez\",\"documento\":\"500\"}", admin);
        String body = "{\"pacienteId\":" + paciente + ",\"medicoId\":" + medico + ",\"area\":\"UCI\",\"habitacion\":\"101\"}";

        int peticiones = 8;
        ExecutorService hilos = Executors.newFixedThreadPool(peticiones);
        CountDownLatch salida = new CountDownLatch(1); // todas las peticiones arrancan a la vez
        try {
            List<Future<Integer>> respuestas = new ArrayList<>();
            for (int i = 0; i < peticiones; i++) {
                respuestas.add(hilos.submit(() -> {
                    salida.await();
                    return request("POST", "/api/ingresos", body, admin).statusCode();
                }));
            }
            salida.countDown();
            List<Integer> codigos = new ArrayList<>();
            for (Future<Integer> r : respuestas) codigos.add(r.get(30, TimeUnit.SECONDS));

            assertThat(codigos).containsOnly(201, 409);
            assertThat(codigos).filteredOn(c -> c == 201).hasSize(1);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from ingreso where id_paciente = ?", Integer.class, paciente)).isEqualTo(1);
    }
}
