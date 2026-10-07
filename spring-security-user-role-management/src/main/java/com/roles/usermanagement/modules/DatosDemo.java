package com.roles.usermanagement.modules;

import com.roles.usermanagement.domain.dto.UserDto;
import com.roles.usermanagement.modules.cita.CitaRequest;
import com.roles.usermanagement.modules.cita.CitaService;
import com.roles.usermanagement.modules.ingreso.Area;
import com.roles.usermanagement.modules.ingreso.EstadoIngreso;
import com.roles.usermanagement.modules.ingreso.IngresoRequest;
import com.roles.usermanagement.modules.ingreso.IngresoService;
import com.roles.usermanagement.modules.ingreso.IngresoUpdateRequest;
import com.roles.usermanagement.modules.medico.MedicoRequest;
import com.roles.usermanagement.modules.medico.MedicoService;
import com.roles.usermanagement.modules.paciente.PacienteRepository;
import com.roles.usermanagement.modules.paciente.PacienteRequest;
import com.roles.usermanagement.modules.paciente.PacienteService;
import com.roles.usermanagement.persistance.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Datos de demostración para probar el proyecto recién clonado (solo si app.demo-data=true, activo en dev).
 * Se cargan una sola vez: si ya hay pacientes, no hace nada. Usa los mismos servicios que la API,
 * así que también se generan el historial y las alertas.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE) // después del bootstrap de roles y permisos
public class DatosDemo implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);

    private final UserRepository usuarios;
    private final MedicoService medicos;
    private final PacienteService pacientes;
    private final PacienteRepository pacienteRepository;
    private final IngresoService ingresos;
    private final CitaService citas;
    private final Clock clock;

    public DatosDemo(UserRepository usuarios, MedicoService medicos, PacienteService pacientes,
                     PacienteRepository pacienteRepository, IngresoService ingresos, CitaService citas, Clock clock) {
        this.usuarios = usuarios;
        this.medicos = medicos;
        this.pacientes = pacientes;
        this.pacienteRepository = pacienteRepository;
        this.ingresos = ingresos;
        this.citas = citas;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (pacienteRepository.count() > 0) return; // ya hay datos: no se toca nada
        LocalDate hoy = LocalDate.now(clock);

        // Una cuenta por rol (contraseñas de demostración, documentadas en el README).
        cuenta("druiz", "Medico123!", "MEDICO");
        cuenta("dgomez", "Medico123!", "MEDICO");
        cuenta("enfermero", "Enfermero123!", "ENFERMERO");
        cuenta("recepcion", "Recepcion123!", "RECEPCION");

        // Médicos vinculados a sus cuentas.
        long ruiz = medicos.crear(new MedicoRequest("Dra. Laura Ruiz", "Medicina interna", "druiz")).id();
        long gomez = medicos.crear(new MedicoRequest("Dr. Andrés Gómez", "Pediatría", "dgomez")).id();

        // Pacientes.
        long ana = pacientes.crear(new PacienteRequest("Ana Pérez", "1001", LocalDate.of(1985, 3, 12), "3001112233")).id();
        long bruno = pacientes.crear(new PacienteRequest("Bruno Díaz", "1002", LocalDate.of(2015, 7, 2), "3002223344")).id();
        long carla = pacientes.crear(new PacienteRequest("Carla Mejía", "1003", LocalDate.of(1972, 11, 30), null)).id();
        long diego = pacientes.crear(new PacienteRequest("Diego Torres", "1004", LocalDate.of(1990, 1, 5), "3004445566")).id();

        // Ingresos en distintos estados (los cambios generan historial y alertas).
        long ingAna = ingresos.crear(new IngresoRequest(ana, ruiz, Area.UCI, "101", hoy.plusDays(5)), "superadmin").id();
        ingresos.actualizar(ingAna, new IngresoUpdateRequest(EstadoIngreso.EN_TRATAMIENTO, null, null, null, null), "druiz");
        ingresos.crear(new IngresoRequest(bruno, gomez, Area.PEDIATRIA, "7", hoy.plusDays(2)), "superadmin");
        long ingCarla = ingresos.crear(new IngresoRequest(carla, ruiz, Area.MEDICINA_GENERAL, "204", hoy.plusDays(10)), "superadmin").id();
        ingresos.actualizar(ingCarla, new IngresoUpdateRequest(EstadoIngreso.EN_RECUPERACION, null, null, "210", null), "druiz");

        // Citas (cada una genera una alerta CITA). Diego solo tiene cita, sin ingreso.
        citas.crear(new CitaRequest(ana, ruiz, hoy.plusDays(1).atTime(10, 0), "Control", null));
        citas.crear(new CitaRequest(diego, gomez, hoy.plusDays(3).atTime(8, 30), "Primera consulta", null));

        log.info("Datos de demostración cargados: 4 cuentas, 2 médicos, 4 pacientes, 3 ingresos y 2 citas");
    }

    /** Crea la cuenta si no existe. */
    private void cuenta(String username, String password, String rol) {
        if (usuarios.existsByUsername(username)) return;
        UserDto dto = new UserDto();
        dto.setUsername(username);
        dto.setEmail(username + "@hospital.local");
        dto.setPassword(password);
        dto.setRole(rol);
        usuarios.save(dto);
    }
}
