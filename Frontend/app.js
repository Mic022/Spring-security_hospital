'use strict';

const $ = s => document.querySelector(s);
// Escapa HTML antes de insertarlo con innerHTML: evita que un dato (p. ej. un nombre) ejecute código (XSS).
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
}[c]));

// Fecha sin hora (AAAA-MM-DD → formato local).
// Se añade T00:00:00 para leerla en hora local; sin eso se toma como UTC y en Colombia mostraría el día anterior.
const day = v => v ? new Date(v + 'T00:00:00').toLocaleDateString('es-CO') : '—';

const date = v => v ? new Date(v).toLocaleString('es-CO') : '—';
const path = v => encodeURIComponent(v);

// sessionStorage se borra al cerrar la pestaña (el token no queda guardado); localStorage sí persiste.
const state = {
    token: sessionStorage.getItem('nexo.token'),
    base: localStorage.getItem('nexo.base') || 'http://localhost:8050',
    me: null,
    view: 'home',
    page: 0,
    rows: [],
    cache: {},
    filters: {},
    busy: false
};

const sections = {
    home: { title: 'Inicio', icon: '⌂' },
    pacientes: { title: 'Pacientes', icon: '♙', endpoint: '/api/pacientes', perms: ['PACIENTE_READ', 'PACIENTE_MANAGE', 'INGRESO_MANAGE'] },
    citas: { title: 'Citas', icon: '▦', endpoint: '/api/citas', perms: ['CITA_READ', 'CITA_MANAGE'] },
    alertas: { title: 'Alertas', icon: '⚑', endpoint: '/api/alertas', perms: ['ALERTA_READ'] },
    medicos: { title: 'Médicos', icon: '✚', endpoint: '/api/medicos', perms: ['MEDICO_READ', 'MEDICO_MANAGE'] },
    users: { title: 'Usuarios', icon: '♧' },
    roles: { title: 'Roles', icon: '◈' },
    permissions: { title: 'Permisos', icon: '⚿' }
};

// Valores permitidos por el backend
const ESTADOS = ['INGRESADO', 'EN_TRATAMIENTO', 'EN_RECUPERACION', 'RECUPERADO'];
const AREAS = ['URGENCIAS', 'UCI', 'PEDIATRIA', 'CIRUGIA', 'MEDICINA_GENERAL', 'GINECOLOGIA'];
const ESTADOS_CITA = ['PROGRAMADA', 'REALIZADA', 'CANCELADA'];
const HOSPITAL = ['pacientes', 'citas', 'alertas', 'medicos'];
const PAGED = ['pacientes', 'citas', 'alertas'];

// Solo deciden qué se muestra; la seguridad real la aplica el backend en cada petición.
const can = p => state.me?.effectivePermissions?.includes(p);
const any = ps => ps.some(can);

function visible(view) {
    if (view === 'home') return true;
    if (view === 'users') {
        return any(['USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE', 'ROLE_ASSIGN', 'PERMISSION_ASSIGN']);
    }
    if (view === 'roles') return can('ROLE_MANAGE');
    if (view === 'permissions') return can('PERMISSION_MANAGE');

    return any(sections[view].perms);
}

function toast(message, bad = false) {
    $('#toast').textContent = message;
    $('#toast').className = 'visible' + (bad ? ' bad' : '');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => {
        $('#toast').className = '';
    }, 5500);
}

function logout() {
    state.token = null;
    state.me = null;
    state.cache = {};
    sessionStorage.removeItem('nexo.token');
    $('#app').hidden = true;
    $('#login-screen').hidden = false;
    if ($('#modal').open) $('#modal').close();
    $('#login-form').password.value = '';
}

// Cliente REST: añade el token, convierte la respuesta en JSON (o texto) y traduce los errores a mensajes.
async function api(url, { method = 'GET', body, auth = true } = {}) {
    const headers = { Accept: 'application/json, text/plain' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (auth && state.token) headers.Authorization = 'Bearer ' + state.token;

    let response;
    try {
        response = await fetch(state.base + url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    } catch {
        throw new Error('No se pudo conectar con el servidor. Revisa la URL, Spring Boot y PostgreSQL.');
    }

    // El backend responde a veces JSON y a veces texto (p. ej. el token del login): si no es JSON, se deja como texto.
    const text = await response.text();
    let data = text;
    try {
        data = text ? JSON.parse(text) : null;
    } catch {}

    if (!response.ok) {
        // 401 = token ausente, vencido o invalidado: se cierra la sesión. Un 403 en /me indica lo mismo.
        if (auth && (response.status === 401 || (response.status === 403 && url === '/api/auth/me'))) {
            logout();
        }
        // Mensaje de error: el texto tal cual, o "campo: mensaje" de cada error de validación,
        // descartando los campos genéricos de Spring (timestamp, status...).
        let error = typeof data === 'string'
            ? data
            : (data?.detail || data?.message || Object.entries(data || {})
                .filter(([k]) => !['timestamp', 'status', 'error', 'path'].includes(k))
                .map(([k, v]) => k + ': ' + v)
                .join('\n'));

        if (response.status === 403) {
            error = 'Tu cuenta no tiene permiso para esta operación, o la sesión ya no está disponible.';
        }
        throw new Error(error || `La operación no se completó (${response.status}).`);
    }

    return data;
}

async function refreshMe() {
    state.me = await api('/api/auth/me');
    $('#session-name').textContent = state.me.username;
    $('#session-role').textContent = state.me.role || 'Sin rol';
    $('#avatar').textContent = state.me.username.slice(0, 2).toUpperCase();
    $('#server-label').textContent = state.base;
    $('#navigation').innerHTML = Object.entries(sections)
        .filter(([k]) => visible(k))
        .map(([k, v]) => `
            <button data-nav="${k}" class="${state.view === k ? 'active' : ''}">
                <span class="nav-icon">${v.icon}</span>${v.title}
            </button>
        `).join('');
}

async function enter() {
    await refreshMe();
    $('#login-screen').hidden = true;
    $('#app').hidden = false;
    await navigate('home');
}

$('#login-form').base.value = state.base;

$('#login-form').addEventListener('submit', async e => {
    e.preventDefault();
    const f = e.currentTarget;
    const b = f.querySelector('button[type=submit]');
    b.disabled = true;
    $('#login-error').textContent = '';

    try {
        // Quita las barras finales ("http://x:8050/" → "http://x:8050"); new URL() falla si la dirección no es válida.
        const base = f.base.value.trim().replace(/\/+$/, '');
        const u = new URL(base);
        if (!['http:', 'https:'].includes(u.protocol)) {
            throw new Error('Utiliza una URL HTTP o HTTPS.');
        }

        state.base = base;
        localStorage.setItem('nexo.base', base);
        state.token = await api('/api/auth/login', {
            method: 'POST',
            auth: false,
            body: {
                username: f.username.value.trim(),
                password: f.password.value
            }
        });
        sessionStorage.setItem('nexo.token', state.token);
        await enter();
        f.password.value = '';
    } catch (error) {
        logout();
        $('#login-error').textContent = error.message;
    } finally {
        b.disabled = false;
    }
});

$('#logout').onclick = logout;
$('#menu-toggle').onclick = () => $('#sidebar').classList.toggle('open');
// Un solo listener para todo el menú: closest() encuentra el botón aunque se pulse su icono.
$('#navigation').onclick = e => {
    const b = e.target.closest('[data-nav]');
    if (b) navigate(b.dataset.nav);
};

function heading(title, subtitle, actions = '') {
    return `
        <div class="page-heading">
            <div>
                <span class="eyebrow">ESPACIO DE TRABAJO</span>
                <h1>${esc(title)}</h1>
                <p>${esc(subtitle)}</p>
            </div>
            <div class="toolbar">${actions}</div>
        </div>
    `;
}

function button(action, label, data = '', className = 'primary') {
    return `<button type="button" class="${className}" data-action="${action}" ${data}>${label}</button>`;
}

function badge(active) {
    return `<span class="badge ${active ? '' : 'off'}">${active ? 'Activo' : 'Inactivo'}</span>`;
}

function chips(values) {
    return `
        <div class="chips">
            ${(values || []).map(v => `<span class="chip">${esc(v)}</span>`).join('') || '<span class="muted">Sin permisos</span>'}
        </div>
    `;
}

async function navigate(view, page = 0) {
    if (!visible(view)) view = 'home';
    state.view = view;
    state.page = page;
    $('#sidebar').classList.remove('open');
    $('#crumb').textContent = sections[view].title;
    $('#navigation').querySelectorAll('button').forEach(b => {
        b.classList.toggle('active', b.dataset.nav === view);
    });

    $('#content').innerHTML = '<div class="empty">Cargando tu espacio…</div>';

    try {
        await render();
    } catch (error) {
        $('#content').innerHTML = heading(sections[view].title, 'No se pudo cargar la información') + `
            <div class="panel empty">
                ${esc(error.message)}<br><br>
                ${button('reload', 'Reintentar')}
            </div>
        `;
    }
}

async function render() {
    if (state.view === 'home') return dashboard();

    const view = state.view;
    const conf = sections[view];
    let actions = button('reload', '↻ Actualizar', '', 'secondary');
    let rows = [];
    let read = false;

    if (HOSPITAL.includes(view)) {
        // Permiso de consulta de cada pantalla
        read = can({ pacientes: 'PACIENTE_READ', citas: 'CITA_READ', alertas: 'ALERTA_READ', medicos: 'MEDICO_READ' }[view]);

        if (view === 'pacientes' && can('PACIENTE_MANAGE')) actions = button('create', '＋ Nuevo paciente') + actions;
        if (view === 'pacientes' && can('INGRESO_MANAGE')) actions += button('ingreso-new', 'Nuevo ingreso', '', 'secondary');
        if (view === 'pacientes' && !read && can('PACIENTE_MANAGE')) actions += button('find-document', 'Buscar por documento', '', 'secondary');
        if (view === 'citas' && can('CITA_MANAGE')) actions = button('create', '＋ Nueva cita') + actions;
        if (view === 'medicos' && can('MEDICO_MANAGE')) actions = button('create', '＋ Nuevo médico') + actions;

        if (read && view === 'medicos') {
            rows = await api(conf.endpoint);
        } else if (read) {
            // Los filtros guardados se envían como parámetros opcionales de la URL.
            // URLSearchParams arma "?estado=...&page=0&size=20" y codifica los caracteres especiales.
            const query = new URLSearchParams({ ...clean(state.filters[view]), page: state.page, size: 20 });
            const result = await api(conf.endpoint + '?' + query);
            rows = result.content || [];
            state.total = result.totalElements || 0;
            state.pages = result.totalPages || 0;
        }
    } else if (view === 'users') {
        read = can('USER_READ');

        if (can('USER_CREATE')) actions = button('create', '＋ Nuevo usuario') + actions;
        if (can('ROLE_ASSIGN')) actions += button('user-role', 'Asignar rol', '', 'secondary');
        if (can('PERMISSION_ASSIGN')) {
            actions += button('user-permission', 'Dar permiso', '', 'secondary') + button('revoke-permission-id', 'Retirar permiso', '', 'secondary');
        }
        if (!read && can('USER_UPDATE')) actions += button('edit-named', 'Actualizar cuenta', '', 'secondary');
        if (!read && can('USER_DELETE')) actions += button('delete-named', 'Eliminar cuenta', '', 'secondary');

        if (read) rows = await api('/api/user/all');
    } else if (view === 'roles') {
        read = true;
        rows = await api('/api/roles');
        actions = button('create', '＋ Nuevo rol') + actions;
    } else {
        read = true;
        rows = (await api('/api/permissions')).map(name => ({ name }));
        actions = button('create', '＋ Nuevo permiso') + actions;
    }

    state.rows = rows;

    const intro = {
        pacientes: 'Busca pacientes con cualquier combinación de filtros y consulta su reporte.',
        citas: 'Agenda del hospital por fecha y médico.',
        alertas: 'Avisos automáticos por cambios de estado, citas y recuperaciones.',
        medicos: 'Directorio de médicos y su cuenta de acceso.',
        users: 'Cuentas, estados y accesos de tu equipo.',
        roles: 'Un rol por usuario. Capacidades compartidas por equipo.',
        permissions: 'El catálogo de capacidades de tu aplicación.'
    };

    $('#content').innerHTML = heading(conf.title, intro[view], actions) + (
        !read
            ? '<div class="panel empty">Puedes realizar las acciones habilitadas arriba. Tu cuenta no tiene permiso para consultar este listado.</div>'
            : (await filtersPanel(view)) + table(view, rows)
    );
}

// Quita los filtros vacíos antes de enviarlos
const clean = o => Object.fromEntries(Object.entries(o || {}).filter(([, v]) => v !== '' && v != null));

// Lista de médicos para los selectores (se guarda en caché hasta el siguiente cambio)
async function medicoList() {
    if (!can('MEDICO_READ')) return [];
    state.cache.medicos = state.cache.medicos || await api('/api/medicos');
    return state.cache.medicos;
}

// Selector con opción vacía; values puede ser una lista de textos o de [valor, etiqueta]
function select(name, label, values, current = '', empty = 'Todos', extra = '') {
    return `
        <label>
            ${label}
            <select name="${name}" ${extra}>
                ${empty === null ? '' : `<option value="">${empty}</option>`}
                ${values.map(v => {
                    const [value, text] = Array.isArray(v) ? v : [v, v];
                    return `<option value="${esc(value)}" ${String(current ?? '') === String(value) ? 'selected' : ''}>${esc(text)}</option>`;
                }).join('')}
            </select>
        </label>
    `;
}

// Panel del módulo de filtros: cada campo es un parámetro opcional del backend
async function filtersPanel(view) {
    const f = state.filters[view] || {};
    const medicos = ['pacientes', 'citas'].includes(view) ? (await medicoList()).map(m => [m.id, m.nombre]) : [];
    // Si la cuenta no puede listar médicos, se pide el ID a mano en lugar del desplegable.
    const medico = medicos.length ? select('medico', 'Médico', medicos, f.medico) : field('ID del médico', 'medico', f.medico, 'number', 'min="1"');
    let body = '';

    if (view === 'pacientes') {
        body = field('Nombre', 'nombre', f.nombre) +
            select('estado', 'Estado', ESTADOS, f.estado) +
            select('area', 'Área', AREAS, f.area) + medico +
            field('Ingreso desde', 'ingresoDesde', f.ingresoDesde, 'date') +
            field('Ingreso hasta', 'ingresoHasta', f.ingresoHasta, 'date') +
            field('Recuperación hasta', 'recuperacionHasta', f.recuperacionHasta, 'date');
    }
    if (view === 'citas') body = field('Fecha', 'fecha', f.fecha, 'date') + medico;
    if (view === 'alertas') body = select('estado', 'Estado', ['PENDIENTE', 'ATENDIDA'], f.estado);
    if (!body) return '';

    return `
        <form id="filters" class="panel filters">
            ${body}
            <div class="toolbar">
                ${button('apply-filters', 'Filtrar')}
                ${button('clear-filters', 'Limpiar', '', 'secondary')}
            </div>
        </form>
    `;
}

function table(view, rows) {
    // cells(fila, índice) devuelve el HTML de cada columna. Los botones guardan el índice de la fila
    // (data-index) para que el manejador de acciones sepa sobre qué registro actuar.
    let headers;
    let cells;
    const action = (name, label, i, cls = 'link-button') => button(name, label, `data-index="${i}"`, cls);

    if (view === 'pacientes') {
        headers = ['Paciente', 'Ingreso actual', 'Estado', 'Acciones'];
        cells = (r, i) => {
            const ing = r.ultimoIngreso;
            const abierto = ing && ing.estado !== 'RECUPERADO'; // con ingreso abierto se actualiza; si no, se puede ingresar
            return [
                `<span class="cell-title">${esc(r.nombre)}</span><small class="cell-sub">Doc. ${esc(r.documento)} · #${r.id}</small>`,
                ing ? `${esc(ing.area)} · Hab. ${esc(ing.habitacion)}<small class="cell-sub">${esc(ing.medicoNombre)} · ${date(ing.fechaIngreso)}</small>` : '<span class="muted">Sin ingresos</span>',
                ing ? `<span class="badge ${abierto ? '' : 'off'}">${esc(ing.estado)}</span><small class="cell-sub">Recuperación: ${day(ing.fechaEstimadaRecuperacion)}</small>` : '—',
                (can('REPORTE_READ') ? action('reporte', 'Reporte', i) : '') +
                (abierto && can('INGRESO_MANAGE') ? action('ingreso-edit', 'Actualizar ingreso', i) : '') +
                (!abierto && can('INGRESO_MANAGE') ? action('ingreso-new', 'Ingresar', i) : '') +
                (can('PACIENTE_MANAGE') ? action('edit', 'Editar', i) : '')
            ];
        };
    }
    if (view === 'citas') {
        headers = ['Paciente', 'Médico', 'Fecha', 'Estado', 'Acciones'];
        cells = (r, i) => [
            `<span class="cell-title">${esc(r.pacienteNombre)}</span><small class="cell-sub">${esc(r.motivo || 'Sin motivo')}</small>`,
            esc(r.medicoNombre),
            date(r.fechaHora),
            `<span class="badge ${r.estado === 'PROGRAMADA' ? '' : 'off'}">${esc(r.estado)}</span>`,
            can('CITA_MANAGE') ? action('edit', 'Editar', i) : '—'
        ];
    }
    if (view === 'alertas') {
        headers = ['Alerta', 'Fecha', 'Estado', 'Acciones'];
        cells = (r, i) => [
            `<span class="cell-title">${esc(r.tipo)} · ${esc(r.pacienteNombre)}</span><small class="cell-sub">${esc(r.mensaje)}</small>`,
            date(r.fecha),
            `<span class="badge ${r.estado === 'PENDIENTE' ? '' : 'off'}">${esc(r.estado)}</span>` + (r.usernameAtiende ? `<small class="cell-sub">${esc(r.usernameAtiende)}</small>` : ''),
            r.estado === 'PENDIENTE' && can('ALERTA_ATENDER') ? action('atender', 'Marcar atendida', i) : '—'
        ];
    }
    if (view === 'medicos') {
        headers = ['Médico', 'Especialidad', 'Cuenta', 'Acciones'];
        cells = (r, i) => [
            `<span class="cell-title">${esc(r.nombre)}</span><small class="cell-sub">#${r.id}</small>`,
            esc(r.especialidad),
            r.username ? esc(r.username) : '<span class="muted">Sin cuenta</span>',
            can('MEDICO_MANAGE') ? action('edit', 'Editar', i) : '—'
        ];
    }
    if (view === 'users') {
        headers = ['Usuario', 'Rol', 'Estado', 'Acciones'];
        cells = (r, i) => [
            `<span class="cell-title">${esc(r.username)}</span><small class="cell-sub">${esc(r.email)}</small>`,
            `<span class="badge">${esc(r.role || 'Sin rol')}</span>`,
            `<span class="badge ${r.locked || r.disabled ? 'off' : ''}">${r.disabled ? 'Deshabilitado' : r.locked ? 'Bloqueado' : 'Habilitado'}</span>`,
            (can('USER_UPDATE') ? action('edit', 'Editar', i) : '') +
            action('user-detail', 'Permisos', i) +
            (can('ROLE_ASSIGN') ? action('user-role', 'Rol', i) : '') +
            (can('PERMISSION_ASSIGN') ? action('user-permission', '＋ Permiso', i) : '') +
            (can('USER_DELETE') ? action('delete-user', 'Eliminar', i) : '')
        ];
    }
    if (view === 'roles') {
        headers = ['Rol', 'Permisos heredados', 'Acciones'];
        cells = (r, i) => [
            `<span class="cell-title">${esc(r.name)}</span>`,
            chips(r.permissions),
            can('PERMISSION_MANAGE') ? action('role-permission', 'Gestionar permisos', i) : '—'
        ];
    }
    if (view === 'permissions') {
        headers = ['Permiso', 'Tipo'];
        cells = r => [
            `<span class="cell-title">${esc(r.name)}</span>`,
            'Capacidad disponible para roles y usuarios'
        ];
    }

    const paging = PAGED.includes(view);

    return `
        <section class="panel">
            <div class="panel-title">
                <h3>${esc(sections[view].title)} <span class="muted">· ${paging ? state.total : rows.length}</span></h3>
                <div class="toolbar">
                    <input id="filter" aria-label="Buscar en los resultados visibles" placeholder="Buscar en esta página…">
                </div>
            </div>
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>${headers.map(h => `<th>${h}</th>`).join('')}</tr>
                    </thead>
                    <tbody>
                        ${rows.map((r, i) => `
                            <tr>
                                ${cells(r, i).map((v, k) => `
                                    <td ${k === headers.length - 1 ? 'class="actions"' : ''}>${v}</td>
                                `).join('')}
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
                <div id="empty-table" class="empty" ${rows.length ? 'hidden' : ''}>
                    Aún no hay registros. Empieza con una nueva creación.
                </div>
            </div>
            ${paging ? `
                <div class="pagination">
                    <span>Página ${state.page + 1} de ${Math.max(state.pages, 1)} ·${state.total} registros</span>
                    <div>
                        <button data-action="previous" ${state.page === 0 ? 'disabled' : ''}>← Anterior</button>
                        <button data-action="next" ${state.page + 1 >= state.pages ? 'disabled' : ''}>Siguiente →</button>
                    </div>
                </div>
            ` : ''}
        </section>
    `;
}

async function dashboard() {
    const first = state.me.username;
    // Totales de cada módulo; en alertas se cuentan solo las pendientes
    // size=1: solo interesa totalElements de la página, no los registros. Las tres peticiones van en paralelo (Promise.all).
    const metricUrls = { pacientes: ['PACIENTE_READ', '/api/pacientes?size=1'], citas: ['CITA_READ', '/api/citas?size=1'], alertas: ['ALERTA_READ', '/api/alertas?estado=PENDIENTE&size=1'] };
    const metrics = await Promise.all(Object.entries(metricUrls).map(async ([key, [perm, url]]) => ({
        key,
        total: can(perm) ? (await api(url)).totalElements : null
    })));

    const links = Object.entries(sections).filter(([key]) => key !== 'home' && visible(key));

    $('#content').innerHTML = heading(
        `Hola, ${first}.`,
        'Tu operación, de un vistazo.',
        `<span class="date-label">${esc(new Date().toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' }))}</span>`
    ) + `
        <div class="hero">
            <div>
                <span class="eyebrow">UN ESPACIO PARA TU EQUIPO</span>
                <h2>Pacientes, citas y alertas en un solo lugar.</h2>
                <p>Consulta el estado de los pacientes, atiende las alertas o administra los accesos. Todo desde tu espacio de trabajo.</p>
            </div>
            <span class="hero-mark">◈</span>
        </div>
        <div class="cards">
            ${metrics.map(({ key, total }) => `
                <div class="card">
                    <span class="card-icon">${sections[key].icon}</span>
                    <div>
                        <h3>${sections[key].title}</h3>
                        <p>${total === null ? 'Sin acceso al listado' : key === 'alertas' ? 'Alertas pendientes' : 'Registros visibles para ti'}</p>
                    </div>
                    <div class="metric">${total ?? '—'}</div>
                    ${visible(key) ? button('go', 'Abrir módulo →', `data-view="${key}"`, 'link-button') : ''}
                </div>
            `).join('')}
        </div>
        <section class="panel">
            <div class="panel-title">
                <h3>Accesos rápidos</h3>
                <span class="badge">${esc(state.me.role)}</span>
            </div>
            <div class="quick-links">
                ${links.map(([key, v]) => `
                    <button class="quick-link" data-action="go" data-view="${key}">
                        <span>${v.icon} &nbsp; ${v.title}<small>Ir a${v.title.toLowerCase()}</small></span>
                        <span>→</span>
                    </button>
                `).join('') || '<div class="empty">Tu cuenta todavía no tiene permisos para estos módulos.</div>'}
            </div>
        </section>
    `;
}

let modalSubmit = null;
// Si el submit devuelve KEEP, el modal sigue abierto (p. ej. buscar y luego editar)
const KEEP = Symbol('keep');

function modal(title, body, onSubmit, label = 'Guardar') {
    const f = $('#modal-form');
    f.reset();
    $('#modal-title').textContent = title;
    $('#modal-body').innerHTML = body;
    $('#modal-error').textContent = '';
    $('#modal-save').textContent = label;
    $('#modal-save').hidden = !onSubmit;
    $('#modal-cancel').textContent = onSubmit ? 'Cancelar' : 'Cerrar';
    modalSubmit = onSubmit;
    if (!$('#modal').open) $('#modal').showModal();
}

$('#modal-close').onclick = $('#modal-cancel').onclick = () => $('#modal').close();

// Cierra el modal al hacer clic fuera del cuadro: el fondo oscuro también es el <dialog>,
// así que se comprueba si el clic cayó fuera de su rectángulo visible.
$('#modal').addEventListener('click', e => {
    if (e.target === $('#modal')) {
        const rect = e.target.getBoundingClientRect();
        if (e.clientX < rect.left || e.clientX > rect.right || e.clientY < rect.top || e.clientY > rect.bottom) {
            e.target.close();
        }
    }
});

$('#modal-form').onsubmit = async e => {
    e.preventDefault();
    if (!modalSubmit) return;

    const submit = modalSubmit;
    const b = $('#modal-save');
    b.disabled = true;
    $('#modal-error').textContent = '';

    try {
        if (await submit(new FormData(e.currentTarget)) === KEEP) return;
        $('#modal').close();
        // Tras guardar se vacía la caché y se recargan los permisos propios (el cambio pudo afectar a esta cuenta).
        state.cache = {};
        await refreshMe();
        await navigate(state.view, state.page);
        toast('Cambios guardados correctamente.');
    } catch (error) {
        $('#modal-error').textContent = error.message;
    } finally {
        b.disabled = false;
    }
};

const field = (label, name, value = '', type = 'text', extra = '') => `
    <label>
        ${label}
        <input name="${name}" type="${type}" value="${esc(value)}" ${extra}>
    </label>
`;

function check(label, name, checked) {
    return `
        <label class="check-label">
            <input type="checkbox" name="${name}" ${checked ? 'checked' : ''}>
            ${label}
        </label>
    `;
}

async function options(url) {
    return await api(url);
}

// Desplegable de roles o permisos si la cuenta puede consultar el catálogo; si no, campo de texto libre.
async function picker(name, label, kind, current = '') {
    let values = [];
    if (kind === 'role' && can('ROLE_MANAGE')) {
        values = (await options('/api/roles')).map(r => r.name);
    }
    if (kind === 'permission' && can('PERMISSION_MANAGE')) {
        values = await options('/api/permissions');
    } else if (kind === 'permission' && can('PERMISSION_ASSIGN')) {
        values = await options('/api/user/permissions');
    }

    if (values.length) {
        return `
            <label>
                ${label}
                <select name="${name}" required>
                    ${values.map(v => `<option ${v === current ? 'selected' : ''} value="${esc(v)}">${esc(v)}</option>`).join('')}
                </select>
            </label>
        `;
    }

    return field(label, name, current || '', 'text', 'required maxlength="50"');
}

async function entityForm(row, askId = false) {
    const view = state.view;
    const edit = !!row;
    row = row || {};
    let body = '';

    if (view === 'pacientes') {
        body = field('Nombre completo', 'nombre', row.nombre, 'text', 'required maxlength="150"') + `
            <div class="fields">
                ${field('Documento', 'documento', row.documento, 'text', 'required maxlength="30"')}
                ${field('Teléfono', 'telefono', row.telefono, 'tel', 'maxlength="30"')}
            </div>
        ` + field('Fecha de nacimiento', 'fechaNacimiento', row.fechaNacimiento, 'date');
    }

    if (view === 'medicos') {
        body = field('Nombre', 'nombre', row.nombre, 'text', 'required maxlength="150"') +
            field('Especialidad', 'especialidad', row.especialidad, 'text', 'required maxlength="100"') +
            field('Cuenta de acceso (opcional)', 'username', row.username, 'text', 'maxlength="50"') +
            '<p class="note">La cuenta debe tener el rol MEDICO. Con ella el médico verá solo a sus pacientes.</p>';
    }

    if (view === 'citas') {
        const medicos = (await medicoList()).map(m => [m.id, m.nombre]);
        body = (edit
            ? `<p class="note">Paciente: <b>${esc(row.pacienteNombre)}</b></p><input type="hidden" name="pacienteId" value="${row.pacienteId}">`
            : field('Documento del paciente', 'documento', '', 'text', 'required maxlength="30"')) +
            (medicos.length
                ? select('medicoId', 'Médico', medicos, row.medicoId, 'Selecciona un médico', 'required')
                : field('ID del médico', 'medicoId', row.medicoId, 'number', 'required min="1"')) +
            // datetime-local solo acepta "AAAA-MM-DDTHH:mm": se recortan segundos y fracciones.
            field('Fecha y hora', 'fechaHora', (row.fechaHora || '').slice(0, 16), 'datetime-local', 'required') +
            field('Motivo', 'motivo', row.motivo, 'text', 'maxlength="255"') +
            (edit ? select('estado', 'Estado', ESTADOS_CITA, row.estado, null) : '') +
            '<p class="note">Agendar o modificar una cita genera una alerta.</p>';
    }

    if (view === 'users') {
        body = field('Usuario', 'username', row.username, 'text', `required maxlength="50" ${edit ? 'readonly' : ''}`) +
            field('Correo', 'email', row.email, 'email', 'required maxlength="200"') +
            field(edit ? 'Nueva contraseña (opcional)' : 'Contraseña', 'password', '', 'password', `${edit ? '' : 'required'} autocomplete="new-password"`) + `
            <div class="fields">
                ${check('Cuenta bloqueada', 'locked', row.locked)}
                ${check('Cuenta deshabilitada', 'disabled', row.disabled)}
            </div>
        `;

        if (!edit && can('ROLE_ASSIGN')) {
            body += await picker('role', 'Rol de la cuenta', 'role');
        }
        body += '<p class="note">Los permisos adicionales se gestionan desde la acción «Dar permiso». Editar datos no cambia el rol.</p>';
    }

    if (view === 'roles') {
        body = field('Nombre del rol', 'name', '', 'text', 'required maxlength="50" pattern="[A-Za-z][A-Za-z0-9_]{0,49}"');
        if (can('PERMISSION_MANAGE')) {
            body += `
                <label>Permisos iniciales</label>
                <div class="checks">
                    ${(await api('/api/permissions')).map(v => `
                        <label>
                            <input type="checkbox" name="permissions" value="${esc(v)}">
                            ${esc(v)}
                        </label>
                    `).join('')}
                </div>
            `;
        }
        body += '<p class="note">El rol puede comenzar sin permisos. Después podrás asignarlo a un usuario.</p>';
    }

    if (view === 'permissions') {
        body = field('Nombre del permiso', 'name', '', 'text', 'required maxlength="50" pattern="[A-Za-z][A-Za-z0-9_]{0,49}"') +
            '<p class="note">Crear un permiso lo registra en el catálogo. La operación correspondiente debe comprobarlo en el servidor.</p>';
    }

    if (askId) {
        body = field('Identificador del registro', 'recordId', '', 'number', 'required min="1" step="1"') + body;
    }

    modal((edit ? 'Editar ' : 'Nuevo ') + ({
        pacientes: 'paciente',
        medicos: 'médico',
        citas: 'cita',
        users: 'usuario',
        roles: 'rol',
        permissions: 'permiso'
    }[view]), body, async f => {
        // Object.fromEntries convierte el FormData en un objeto { nombreDelCampo: valor } listo para enviar como JSON.
        let data = Object.fromEntries(f);
        const recordId = askId ? data.recordId : row.id;
        delete data.recordId;

        if (HOSPITAL.includes(view)) {
            // Los campos vacíos se envían como null
            for (const k of Object.keys(data)) if (data[k] === '') data[k] = null;
        }
        if (view === 'citas') {
            // El formulario pide el documento; el backend espera el id del paciente.
            if (!edit) data.pacienteId = await pacienteId(data.documento);
            delete data.documento;
            data.pacienteId = Number(data.pacienteId);
            data.medicoId = Number(data.medicoId);
        }
        if (view === 'users') {
            // Un checkbox sin marcar no aparece en el FormData: has() lo convierte en true/false.
            data.locked = f.has('locked');
            data.disabled = f.has('disabled');
            if (edit && !data.password) delete data.password;
        }
        if (view === 'roles' && can('PERMISSION_MANAGE')) {
            data.permissions = f.getAll('permissions');
        }

        // Usuarios, roles y permisos tienen rutas propias; los módulos del hospital usan endpoint y endpoint/{id}.
        const url = view === 'users'
            ? (edit ? '/api/user/update' : '/api/user/add')
            : view === 'roles'
                ? '/api/roles'
                : view === 'permissions'
                    ? '/api/permissions'
                    : sections[view].endpoint + (edit ? '/' + recordId : '');

        await api(url, { method: edit ? 'PUT' : 'POST', body: data });
    });
}

function confirmAction(title, message, run) {
    modal(title, `<p>${esc(message)}</p>`, run, 'Confirmar');
}

async function userRole(row) {
    modal(
        'Asignar rol',
        field('Usuario', 'username', row?.username || '', 'text', 'required') +
        await picker('role', 'Rol único', 'role', row?.role) +
        '<p class="note">Reemplaza el rol actual y conserva los permisos individuales.</p>',
        f => api('/api/user/assignRole', { method: 'POST', body: Object.fromEntries(f) })
    );
}

async function userPermission(row) {
    modal(
        'Dar permiso individual',
        field('Usuario', 'username', row?.username || '', 'text', 'required') +
        await picker('permission', 'Permiso adicional', 'permission') +
        '<p class="note">Se suma a los permisos del rol sin cambiarlo.</p>',
        f => api('/api/user/assignPermission', { method: 'POST', body: Object.fromEntries(f) })
    );
}

async function userDetails(row) {
    const d = await api(`/api/user/${path(row.username)}/permissions`);
    modal(
        'Permisos de ' + d.username,
        `
            <p class="note">Rol: <b>${esc(d.role)}</b></p>
            <div class="permission-group">
                <h3>Heredados del rol</h3>
                ${chips(d.rolePermissions)}
            </div>
            <div class="permission-group">
                <h3>Permisos individuales</h3>
                ${(d.additionalPermissions || []).map(p => `
                    <div class="info-row">
                        <span>${esc(p)}</span>${can('PERMISSION_ASSIGN') ? button('revoke-user-permission', 'Retirar', `data-username="${esc(d.username)}" data-permission="${esc(p)}"`, 'link-button') : ''}
                    </div>
                `).join('') || '<small>No tiene permisos adicionales.</small>'}
            </div>
            <div class="permission-group">
                <h3>Acceso efectivo</h3>
                ${chips(d.effectivePermissions)}
            </div>
        `,
        null
    );
}

async function rolePermissions(row) {
    const p = await picker('permission', 'Permiso', 'permission');
    modal(
        'Permisos de ' + row.name,
        `
            <p class="note">Estos permisos se aplican a todos los usuarios con el rol ${esc(row.name)}.</p>
            ${p}
            <label>
                Operación
                <select name="operation">
                    <option value="PUT">Agregar al rol</option>
                    <option value="DELETE">Retirar del rol</option>
                </select>
            </label>
            <div class="permission-group">
                <h3>Permisos actuales</h3>
                ${chips(row.permissions)}
            </div>
        `,
        // La operación elegida es el método HTTP: PUT agrega el permiso al rol y DELETE lo retira.
        f => api(`/api/roles/${path(row.name)}/permissions/${path(f.get('permission'))}`, { method: f.get('operation') })
    );
}

// Id del paciente a partir de su documento
const pacienteId = async documento => (await api('/api/pacientes/documento/' + path(String(documento).trim()))).id;

// Selector de médico, o campo de ID si la cuenta no puede listar médicos
async function medicoField(name, label, current = '') {
    const medicos = (await medicoList()).map(m => [m.id, m.nombre]);
    return medicos.length
        ? select(name, label, medicos, current, current ? null : 'Selecciona un médico', 'required')
        : field('ID del ' + label.toLowerCase(), name, current, 'number', 'required min="1"');
}

// Nuevo ingreso: desde la fila del paciente o indicando su documento
async function ingresoForm(row) {
    modal(
        'Nuevo ingreso',
        (row ? `<p class="note">Paciente: <b>${esc(row.nombre)}</b></p>` : field('Documento del paciente', 'documento', '', 'text', 'required maxlength="30"')) +
        await medicoField('medicoId', 'Médico responsable') + `
        <div class="fields">
            ${select('area', 'Área', AREAS, '', 'Selecciona un área', 'required')}
            ${field('Habitación', 'habitacion', '', 'text', 'required maxlength="20"')}
        </div>
        ` + field('Fecha estimada de recuperación', 'fechaEstimadaRecuperacion', '', 'date') +
        '<p class="note">El ingreso empieza en estado INGRESADO. Un paciente solo puede tener un ingreso abierto.</p>',
        async f => api('/api/ingresos', {
            method: 'POST',
            body: {
                pacienteId: row ? row.id : await pacienteId(f.get('documento')),
                medicoId: Number(f.get('medicoId')),
                area: f.get('area'),
                habitacion: f.get('habitacion'),
                fechaEstimadaRecuperacion: f.get('fechaEstimadaRecuperacion') || null
            }
        }),
        'Registrar ingreso'
    );
}

// Cambiar estado, médico, área, habitación o fecha estimada del ingreso abierto
async function ingresoUpdate(row) {
    const ing = row.ultimoIngreso;
    modal(
        'Actualizar ingreso de ' + row.nombre,
        select('estado', 'Estado', ESTADOS, ing.estado, null) +
        await medicoField('medicoId', 'Médico responsable', ing.medicoId) + `
        <div class="fields">
            ${select('area', 'Área', AREAS, ing.area, null)}
            ${field('Habitación', 'habitacion', ing.habitacion, 'text', 'required maxlength="20"')}
        </div>
        ` + field('Fecha estimada de recuperación', 'fechaEstimadaRecuperacion', ing.fechaEstimadaRecuperacion, 'date') +
        '<p class="note">Los cambios quedan en el historial y generan una alerta. RECUPERADO cierra el ingreso.</p>',
        f => api('/api/ingresos/' + ing.id, {
            method: 'PUT',
            body: {
                estado: f.get('estado'),
                medicoId: Number(f.get('medicoId')),
                area: f.get('area'),
                habitacion: f.get('habitacion'),
                fechaEstimadaRecuperacion: f.get('fechaEstimadaRecuperacion') || null
            }
        })
    );
}

// Reporte del paciente (módulo de reportes): se calcula en el backend al pedirlo
async function reporte(row) {
    const r = await api('/api/reportes/pacientes/' + row.id);
    const info = (label, value) => `<div class="info-row"><span>${label}</span><b>${esc(value ?? '—')}</b></div>`;
    modal(
        'Reporte de ' + r.nombre,
        info('Documento', r.documento) +
        (r.fechaIngreso
            ? info('Fecha de ingreso', date(r.fechaIngreso)) +
              info('Médico responsable', r.medicoResponsable) +
              info('Área y habitación', r.area + ' · ' + r.habitacion) +
              info('Estado', r.estado) +
              info('Recuperación estimada', day(r.fechaEstimadaRecuperacion)) +
              info('Días transcurridos', r.diasTranscurridos) +
              info('Días restantes', r.diasRestantes)
            : '<p class="note">El paciente no tiene ingresos.</p>') + `
        <div class="permission-group">
            <h3>Citas</h3>
            ${r.citas.map(c => `<div class="info-row"><span>${esc(date(c.fechaHora))} · ${esc(c.medicoNombre)}</span><b>${esc(c.estado)}</b></div>`).join('') || '<small>Sin citas.</small>'}
        </div>
        <div class="permission-group">
            <h3>Historial de estados</h3>
            ${r.historial.map(h => `<div class="info-row"><span>${esc(date(h.fecha))} · ${esc(h.detalle)}</span><b>${esc(h.username)}</b></div>`).join('') || '<small>Sin cambios.</small>'}
        </div>
        `,
        null
    );
}

// Ejecuta la acción del botón pulsado (atributo data-action). row es el registro de la fila,
// recuperado por su data-index; en botones fuera de la tabla queda undefined.
async function handle(action, element) {
    const row = state.rows[Number(element.dataset.index)];

    switch (action) {
        case 'go':
            return navigate(element.dataset.view);

        case 'reload':
            await refreshMe();
            return navigate(state.view, state.page);

        case 'previous':
            return navigate(state.view, state.page - 1);

        case 'next':
            return navigate(state.view, state.page + 1);

        case 'create':
            return entityForm();

        case 'edit':
            return entityForm(row);

        case 'revoke-permission-id':
            modal(
                'Retirar permiso individual',
                field('Usuario', 'username', '', 'text', 'required') +
                await picker('permission', 'Permiso individual', 'permission') +
                '<p class="note">Los permisos heredados del rol permanecerán activos.</p>',
                f => api(`/api/user/${path(f.get('username'))}/permissions/${path(f.get('permission'))}`, { method: 'DELETE' }),
                'Retirar permiso'
            );
            return;

        case 'edit-named':
            modal(
                'Actualizar cuenta',
                field('Usuario', 'username', '', 'text', 'required') +
                field('Nuevo correo (opcional)', 'email', '', 'email') +
                field('Nueva contraseña (opcional)', 'password', '', 'password') +
                `
                    <label>
                        Estado
                        <select name="status">
                            <option value="">Conservar estado</option>
                            <option value="enabled">Habilitar y desbloquear</option>
                            <option value="disabled">Deshabilitar</option>
                            <option value="locked">Bloquear</option>
                        </select>
                    </label>
                `,
                f => {
                    const data = { username: f.get('username') };
                    if (f.get('email')) data.email = f.get('email');
                    if (f.get('password')) data.password = f.get('password');
                    if (f.get('status') === 'enabled') {
                        data.locked = false;
                        data.disabled = false;
                    }
                    if (f.get('status') === 'disabled') data.disabled = true;
                    if (f.get('status') === 'locked') data.locked = true;
                    return api('/api/user/update', { method: 'PUT', body: data });
                }
            );
            return;

        case 'delete-named':
            modal(
                'Eliminar cuenta',
                field('Usuario', 'username', '', 'text', 'required') +
                '<p class="note">La cuenta se eliminará permanentemente.</p>',
                f => api('/api/user/delete/' + path(f.get('username')), { method: 'DELETE' }),
                'Eliminar'
            );
            return;

        case 'delete-user':
            return confirmAction(
                'Eliminar usuario',
                `¿Eliminar permanentemente la cuenta ${row.username}?`,
                () => api('/api/user/delete/' + path(row.username), { method: 'DELETE' })
            );

        case 'user-role':
            return userRole(row);

        case 'user-permission':
            return userPermission(row);

        case 'user-detail':
            return userDetails(row);

        case 'role-permission':
            return rolePermissions(row);

        case 'apply-filters':
            state.filters[state.view] = Object.fromEntries(new FormData($('#filters')));
            return navigate(state.view, 0);

        case 'clear-filters':
            state.filters[state.view] = {};
            return navigate(state.view, 0);

        case 'reporte':
            return reporte(row);

        case 'ingreso-new':
            return ingresoForm(row);

        case 'ingreso-edit':
            return ingresoUpdate(row);

        case 'atender':
            return confirmAction(
                'Marcar alerta como atendida',
                `${row.tipo} · ${row.pacienteNombre}: ${row.mensaje}`,
                () => api('/api/alertas/' + row.id, { method: 'PUT' })
            );

        case 'find-document':
            // Recepción no consulta el listado: busca por documento y edita
            modal(
                'Buscar paciente',
                field('Documento', 'documento', '', 'text', 'required maxlength="30"'),
                async f => {
                    await entityForm(await api('/api/pacientes/documento/' + path(f.get('documento').trim())));
                    return KEEP;
                },
                'Buscar'
            );
            return;

        case 'change-password':
            // Tras cambiarla, los tokens anteriores dejan de servir: se cierra la sesión
            modal(
                'Cambiar mi contraseña',
                field('Contraseña actual', 'passwordActual', '', 'password', 'required autocomplete="current-password"') +
                field('Nueva contraseña', 'passwordNueva', '', 'password', 'required minlength="8" maxlength="72" autocomplete="new-password"') +
                '<p class="note">Mínimo 8 caracteres, con letras y números. Después tendrás que iniciar sesión de nuevo.</p>',
                async f => {
                    await api('/api/auth/password', { method: 'PUT', body: Object.fromEntries(f) });
                    logout();
                    toast('Contraseña cambiada. Inicia sesión con la nueva contraseña.');
                    return KEEP;
                },
                'Cambiar contraseña'
            );
            return;

        case 'revoke-user-permission':
            return confirmAction(
                'Retirar permiso individual',
                `¿Retirar ${element.dataset.permission} de ${element.dataset.username}? Los permisos heredados se conservan.`,
                () => api(`/api/user/${path(element.dataset.username)}/permissions/${path(element.dataset.permission)}`, { method: 'DELETE' })
            );
    }
}

// Delegación de eventos: un listener para todos los botones, también los que se crean después con innerHTML.
async function onAction(e) {
    const b = e.target.closest('[data-action]');
    if (!b || b.disabled) return;
    b.disabled = true; // evita doble clic mientras la petición está en curso

    try {
        await handle(b.dataset.action, b);
    } catch (error) {
        toast(error.message, true);
    } finally {
        b.disabled = false;
    }
}

$('#content').addEventListener('click', onAction);
$('#modal-body').addEventListener('click', onAction);
// Enter en el panel de filtros aplica los filtros en vez de recargar la página
$('#content').addEventListener('submit', e => {
    if (e.target.id !== 'filters') return;
    e.preventDefault();
    handle('apply-filters', e.target);
});

// Búsqueda rápida: oculta las filas de la página actual que no contienen el texto (no consulta al servidor).
$('#content').addEventListener('input', e => {
    if (e.target.id !== 'filter') return;
    const term = e.target.value.toLocaleLowerCase();
    let shown = 0;

    $('#content').querySelectorAll('tbody tr').forEach(row => {
        row.hidden = !row.textContent.toLocaleLowerCase().includes(term);
        if (!row.hidden) shown++;
    });

    $('#empty-table').hidden = shown > 0;
    $('#empty-table').textContent = term ? 'No hay coincidencias en esta página.' : 'Aún no hay registros.';
});

$('#session-button').onclick = async () => {
    try {
        await refreshMe();
        modal(
            'Mi espacio',
            `
                <div class="info-row"><span>Usuario</span><b>${esc(state.me.username)}</b></div>
                <div class="info-row"><span>Rol</span><b>${esc(state.me.role)}</b></div>
                <div class="permission-group">
                    <h3>Permisos de mi sesión</h3>
                    ${chips(state.me.effectivePermissions)}
                </div>
                ${button('change-password', 'Cambiar mi contraseña', '', 'secondary')}
            `,
            null
        );
    } catch (error) {
        toast(error.message, true);
        logout();
    }
};

// Al recargar la página con un token guardado se intenta entrar directamente; si ya no sirve, vuelve al login.
if (state.token) {
    enter().catch(() => {
        logout();
        toast('Inicia sesión nuevamente para continuar.', true);
    });
}