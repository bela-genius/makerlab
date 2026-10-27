/* MakerLab EIA web: una sola página, sin frameworks.
   Todo texto que viene del servidor entra al DOM con textContent (nunca innerHTML),
   porque cualquiera con el QR puede escribir nombres y propósitos que otros verán. */
(function () {
  'use strict';

  var SECCIONES = [
    ['inicio', 'Inicio'], ['usuarios', 'Usuarios'], ['equipos', 'Equipos'],
    ['reservas', 'Reservas'], ['mantenimiento', 'Mantenimiento'], ['indicadores', 'Indicadores']
  ];

  // Mensaje exacto de la excepción + una etiqueta corta para saber de cuál se trata.
  var ETIQUETAS = {
    CruceHorarioException: 'Cruce de horario',
    CertificacionInvalidaException: 'Certificación inválida',
    EquipoNoDisponibleException: 'Equipo no disponible',
    MakerLabException: 'Regla del sistema',
    DatosInvalidos: 'Revise los datos',
    NoExiste: 'No existe',
    BaseDeDatos: 'Base de datos',
    SinConexion: 'Sin conexión',
    TokenIncorrecto: 'Código incorrecto',
    DemoDeshabilitado: 'Modo demostración',
    DemasiadosIntentos: 'Demasiados intentos',
    Iniciando: 'Iniciando',
    ErrorInterno: 'Error del servidor'
  };

  var vista = document.getElementById('vista');
  var avisos = document.getElementById('avisos');
  var toasts = document.getElementById('toasts');
  var dlg = document.getElementById('dialogo');
  var estadoDatos = document.getElementById('estado-datos');

  var seccionActual = null;
  var cargandoSeccion = 0;
  var abiertos = {};       // data-id de los <details> abiertos, para que sobrevivan al refresco
  var borradores = {};     // lo que la persona ya escribió en formularios, para que sobreviva al refresco
  var ultimaDisponibilidad = null;
  var estadoServidor = null;

  // ------------------------------------------------------------------ DOM

  function h(tag, props) {
    var el = document.createElement(tag);
    var p = props || {};
    Object.keys(p).forEach(function (k) {
      var v = p[k];
      if (v === undefined || v === null || v === false) { return; }
      if (k === 'class') { el.className = v; }
      else if (k === 'text') { el.textContent = v; }
      else if (k.indexOf('on') === 0) { el.addEventListener(k.slice(2), v); }
      else if (k === 'value' || k === 'checked' || k === 'disabled' || k === 'open') { el[k] = v; }
      else { el.setAttribute(k, v === true ? '' : v); }
    });
    for (var i = 2; i < arguments.length; i++) { agregar(el, arguments[i]); }
    return el;
  }
  function agregar(el, hijo) {
    if (hijo === null || hijo === undefined || hijo === false) { return; }
    if (Array.isArray(hijo)) { hijo.forEach(function (x) { agregar(el, x); }); return; }
    el.append(hijo.nodeType ? hijo : document.createTextNode(String(hijo)));
  }
  function vaciar(el) { while (el.firstChild) { el.removeChild(el.firstChild); } }

  function pildora(texto, clase) {
    return h('span', { class: 'pildora pildora-' + clase, text: texto });
  }
  function pildoraEstado(codigo, nombre) {
    return pildora(nombre, String(codigo).toLowerCase().replace(/_/g, '-'));
  }
  function cabecera(titulo, descripcion) {
    return h('div', { class: 'cabecera' }, h('h1', { class: 'titulo', text: titulo }),
      h('p', { class: 'descripcion', text: descripcion }));
  }
  function panel(titulo) {
    var hijos = Array.prototype.slice.call(arguments, 1);
    return h('section', { class: 'panel' }, h('h2', { class: 'subtitulo', text: titulo }), hijos);
  }
  function desplegable(id, titulo, contenido, abiertoPorDefecto) {
    var d = h('details', { class: 'panel primero-movil', 'data-id': id },
      h('summary', { class: 'subtitulo', text: titulo }), contenido);
    d.open = abiertos[id] !== undefined ? abiertos[id] : !!abiertoPorDefecto;
    d.addEventListener('toggle', function () { abiertos[id] = d.open; });
    return d;
  }
  function vacio(texto) { return h('p', { class: 'vacio', text: texto }); }

  // ------------------------------------------------------------------ red

  function api(metodo, ruta, cuerpo, cabeceras) {
    var opciones = { method: metodo, headers: Object.assign({}, cabeceras || {}) };
    if (cuerpo !== undefined) {
      opciones.headers['Content-Type'] = 'application/json';
      opciones.body = JSON.stringify(cuerpo);
    }
    return fetch(ruta, opciones).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (data) {
        return { ok: r.ok, estado: r.status, data: data || {} };
      });
    }, function () {
      return { ok: false, estado: 0, data: { tipo: 'SinConexion',
        mensaje: 'No hay conexión con el servidor. Revise su internet e intente de nuevo.' } };
    });
  }
  function obtener(ruta) {
    return api('GET', ruta).then(function (r) {
      if (!r.ok) { var e = new Error(r.data.mensaje || 'Error'); e.respuesta = r; throw e; }
      return r.data;
    });
  }

  // -------------------------------------------------------------- mensajes

  function crearAlerta(tipo, mensaje, cerrar) {
    var clase = tipo === 'CruceHorarioException' ? 'alerta-cruce' : 'alerta-error';
    return h('div', { class: 'alerta ' + clase, role: 'alert' },
      h('div', { class: 'alerta-tipo', text: ETIQUETAS[tipo] || 'No se pudo completar' }),
      h('div', { class: 'alerta-mensaje', text: mensaje }),
      cerrar ? h('button', { type: 'button', class: 'alerta-cerrar', 'aria-label': 'Cerrar mensaje',
        text: '×', onclick: cerrar }) : null);
  }
  function limpiarAvisos() { vaciar(avisos); }

  function toast(texto) {
    var t = h('div', { class: 'toast', role: 'status', text: texto });
    toasts.append(t);
    setTimeout(function () { t.remove(); }, 4500);
  }

  // Muestra el resultado de una operación: éxito en un aviso pasajero; rechazo con el mensaje exacto.
  function resultado(resp) {
    limpiarAvisos();
    if (resp.ok) {
      toast(resp.data.mensaje || 'Listo.');
    } else {
      avisos.append(crearAlerta(resp.data.tipo, resp.data.mensaje || 'No se pudo completar la operación.', limpiarAvisos));
      avisos.scrollIntoView({ block: 'nearest' });
    }
  }

  // ------------------------------------------------------------ formularios

  function campoEl(c) {
    var id = 'c-' + Math.random().toString(36).slice(2, 8);
    var control;
    if (c.tipo === 'select') {
      control = h('select', { id: id, name: c.name });
      (c.opciones || []).forEach(function (o) { control.append(h('option', { value: o[0], text: o[1] })); });
      if (c.valor !== undefined) { control.value = c.valor; }
    } else if (c.tipo === 'checkbox') {
      control = h('input', { id: id, name: c.name, type: 'checkbox', checked: !!c.valor });
      return h('div', { class: 'casilla', 'data-campo': c.name }, control, h('label', { for: id, text: c.label }));
    } else {
      control = h('input', { id: id, name: c.name, type: c.tipo || 'text', value: c.valor === undefined ? '' : c.valor,
        placeholder: c.ph, maxlength: c.max, min: c.min, step: c.step, inputmode: c.inputmode,
        autocomplete: c.autocomplete || 'off', required: c.req === false ? null : '' });
      if (c.req === false) { control.removeAttribute('required'); }
    }
    return h('div', { class: 'campo', 'data-campo': c.name }, h('label', { for: id, text: c.label }), control);
  }
  function camposEl(lista) {
    return lista.map(function (c) {
      return Array.isArray(c) ? h('div', { class: 'fila-campos' }, c.map(campoEl)) : campoEl(c);
    });
  }
  function leerValores(form) {
    var v = {};
    Array.prototype.forEach.call(form.elements, function (el) {
      if (!el.name) { return; }
      v[el.name] = el.type === 'checkbox' ? el.checked : el.value;
    });
    return v;
  }

  // Formulario dentro de la página. alEnviar(valores) devuelve una promesa con la respuesta del servidor.
  function formulario(id, campos, textoBoton, alEnviar) {
    var boton = h('button', { type: 'submit', class: 'boton boton-ancho', text: textoBoton });
    var form = h('form', { 'data-id': id, novalidate: false }, camposEl(campos), boton);
    form.addEventListener('submit', function (ev) {
      ev.preventDefault();
      if (!form.reportValidity()) { return; }
      boton.disabled = true;
      var original = boton.textContent;
      boton.textContent = 'Enviando…';
      alEnviar(leerValores(form)).then(function (resp) {
        resultado(resp);
        if (resp.ok) { olvidarBorradores(id); }
        return refrescar();
      }).then(function () {
        boton.disabled = false; boton.textContent = original;
      }, function () {
        boton.disabled = false; boton.textContent = original;
      });
    });
    return form;
  }

  function olvidarBorradores(id) {
    Object.keys(borradores).forEach(function (k) { if (k.indexOf(id + '.') === 0) { delete borradores[k]; } });
  }
  vista.addEventListener('input', recordarCampo);
  vista.addEventListener('change', recordarCampo);
  function recordarCampo(ev) {
    var el = ev.target;
    var form = el.form;
    if (!form || !form.dataset.id || !el.name) { return; }
    borradores[form.dataset.id + '.' + el.name] = el.type === 'checkbox' ? el.checked : el.value;
  }
  function restaurarBorradores() {
    Array.prototype.forEach.call(vista.querySelectorAll('form[data-id]'), function (form) {
      Array.prototype.forEach.call(form.elements, function (el) {
        var k = form.dataset.id + '.' + el.name;
        if (!el.name || !(k in borradores)) { return; }
        if (el.type === 'checkbox') { el.checked = borradores[k]; }
        else if (el.tagName === 'SELECT') {
          if (Array.prototype.some.call(el.options, function (o) { return o.value === borradores[k]; })) { el.value = borradores[k]; }
        } else { el.value = borradores[k]; }
        el.dispatchEvent(new Event('change'));
      });
    });
  }

  // ------------------------------------------------------------- diálogos

  function abrirDialogo(contenido) {
    vaciar(dlg);
    dlg.append(h('div', { class: 'dialogo-cuerpo' }, contenido));
    if (!dlg.open) { dlg.showModal(); }
  }
  function cerrarDialogo() { if (dlg.open) { dlg.close(); } }
  dlg.addEventListener('click', function (ev) { if (ev.target === dlg) { cerrarDialogo(); } });

  // Diálogo con campos. Si el servidor rechaza, el mensaje exacto se queda dentro del diálogo.
  function dialogoFormulario(titulo, campos, textoBoton, alEnviar, opciones) {
    var o = opciones || {};
    var error = h('div', { class: 'dialogo-error' });
    var boton = h('button', { type: 'submit', class: 'boton' + (o.peligro ? ' boton-peligro' : ''), text: textoBoton });
    var form = h('form', null, o.texto ? h('p', { class: 'descripcion', text: o.texto }) : null,
      camposEl(campos), error,
      h('div', { class: 'dialogo-botones' },
        h('button', { type: 'button', class: 'boton boton-secundario', text: 'Volver', onclick: cerrarDialogo }), boton));
    form.addEventListener('submit', function (ev) {
      ev.preventDefault();
      if (!form.reportValidity()) { return; }
      boton.disabled = true;
      alEnviar(leerValores(form)).then(function (resp) {
        boton.disabled = false;
        vaciar(error);
        if (resp.ok) {
          cerrarDialogo();
          resultado(resp);
          if (o.alExito) { o.alExito(resp); }
          return refrescar();
        }
        error.append(crearAlerta(resp.data.tipo, resp.data.mensaje || 'No se pudo completar la operación.'));
        if (resp.data.tipo !== 'TokenIncorrecto') { return refrescar(); }
      });
    });
    abrirDialogo([h('h2', { text: titulo }), form]);
    var primero = form.querySelector('input,select');
    if (primero) { primero.focus(); }
  }

  function confirmar(titulo, texto, textoBoton, alConfirmar) {
    dialogoFormulario(titulo, [], textoBoton, alConfirmar, { texto: texto, peligro: true });
  }

  // ------------------------------------------------------------- utilidades

  function opcionesUsuario(lista) {
    return lista.map(function (u) { return [u.id, u.id + '  ' + u.nombre]; });
  }
  function opcionesEquipo(lista) {
    return lista.map(function (e) {
      return [e.codigo, e.codigo + '  ' + e.nombre + (e.estado === 'OPERATIVO' ? '' : ' (' + e.estadoNombre + ')')];
    });
  }
  function fechaMasAnios(iso, n) {
    var p = iso.split('-').map(Number);
    return new Date(Date.UTC(p[0] + n, p[1] - 1, p[2])).toISOString().slice(0, 10);
  }
  function horario(inicio, fin) { return inicio + ' a ' + fin; }
  function dato(etiqueta, valor) {
    return h('div', null, h('span', { class: 'etiqueta', text: etiqueta }), valor);
  }
  function enviar(metodo, ruta, cuerpo, cabeceras) {
    return function () { return api(metodo, ruta, cuerpo, cabeceras); };
  }

  // ================================================================ VISTAS

  function vistaInicio() {
    return obtener('/api/inicio').then(function (d) {
      var labs = d.laboratorios.map(function (lab) {
        return h('div', { class: 'bloque' },
          h('div', { class: 'item-titulo', text: lab.nombre }),
          h('div', { class: 'item-sub', text: lab.codigo + ' · ' + lab.ubicacion + ' · ' + horario(lab.apertura, lab.cierre) }),
          lab.equipos.length ? lab.equipos.map(filaEquipoInicio) : vacio('Sin equipos registrados.'));
      });
      var proximas = d.proximas.length ? d.proximas.map(function (r) {
        return h('div', { class: 'fila-lab' },
          h('div', null, h('div', { class: 'item-titulo', text: r.fecha + '  ' + horario(r.inicio, r.fin) }),
            h('div', { class: 'item-sub', text: r.equipoCodigo + ', ' + r.usuario })));
      }) : [vacio('No hay reservas desde hoy. Créelas en la sección Reservas.'),
        h('a', { class: 'boton boton-secundario boton-pequeno', href: '#reservas', text: 'Crear una reserva' })];
      return h('div', null,
        cabecera('Inicio', 'Hoy es ' + d.fechaTexto + '. Estado de los equipos y reservas que vienen.'),
        h('div', { class: 'rejilla dos' }, panel('Equipos por laboratorio', labs), panel('Próximas reservas', proximas)));
    });
  }
  function filaEquipoInicio(e) {
    return h('div', { class: 'fila-lab' },
      h('div', null, h('div', { class: 'item-titulo', text: e.codigo + '  ' + e.nombre }),
        h('div', { class: 'item-sub', text: e.caracteristicas || e.tipoNombre })),
      pildoraEstado(e.estado, e.estadoNombre));
  }

  function vistaUsuarios() {
    return obtener('/api/usuarios').then(function (d) {
      var tiposEquipo = d.tiposEquipo.map(function (t) { return [t.codigo, t.nombre]; });
      var tarjetas = d.usuarios.map(function (u) { return tarjetaUsuario(u, d, tiposEquipo); });

      var form = formulario('nuevo-usuario', [
        { name: 'tipo', label: 'Tipo', tipo: 'select', opciones: [['ESTUDIANTE', 'Estudiante'], ['PROFESOR', 'Profesor'], ['TECNICO', 'Técnico']] },
        { name: 'nombre', label: 'Nombre', max: 100, autocomplete: 'name' },
        { name: 'correo', label: 'Correo', tipo: 'email', ph: 'nombre@eia.edu.co', max: 100, inputmode: 'email' },
        { name: 'detalle', label: 'Carrera', max: 100 },
        { name: 'semestre', label: 'Semestre', tipo: 'number', min: 1, step: 1, ph: '1 a 12', inputmode: 'numeric' }
      ], 'Registrar usuario', function (v) {
        var cuerpo = { tipo: v.tipo, nombre: v.nombre, correo: v.correo, detalle: v.detalle };
        if (v.tipo === 'ESTUDIANTE') { cuerpo.semestre = v.semestre; }
        return api('POST', '/api/usuarios', cuerpo);
      });
      var ajustar = function () {
        var tipo = form.elements.tipo.value;
        form.querySelector('[data-campo="detalle"] label').textContent =
          tipo === 'ESTUDIANTE' ? 'Carrera' : tipo === 'PROFESOR' ? 'Departamento' : 'Especialidad';
        var cs = form.querySelector('[data-campo="semestre"]');
        cs.hidden = tipo !== 'ESTUDIANTE';
        form.elements.semestre.required = tipo === 'ESTUDIANTE';
      };
      form.elements.tipo.addEventListener('change', ajustar);
      ajustar();

      return h('div', null,
        cabecera('Usuarios', 'Estudiantes, profesores y técnicos, con sus certificaciones y avisos.'),
        h('div', { class: 'rejilla dos' },
          h('div', { class: 'pila' }, h('h2', { class: 'subtitulo', text: 'Registrados (' + d.usuarios.length + ')' }), tarjetas),
          desplegable('f-usuario', 'Nuevo usuario', form, false)));
    });
  }
  function tarjetaUsuario(u, d, tiposEquipo) {
    var certs = u.certificaciones.length ? h('ul', { class: 'detalle-lista' }, u.certificaciones.map(function (c) {
      return h('li', null, c.id + '  ' + c.tipoEquipoNombre + ', hasta ' + c.vencimiento + ' ',
        pildoraEstado(c.vigente ? 'vigente' : 'no-vigente', c.vigente ? 'vigente' : 'no vigente'));
    })) : vacio('Sin certificaciones.');
    var notis = u.notificaciones.length ? h('ul', { class: 'detalle-lista' },
      u.notificaciones.map(function (n) { return h('li', { text: n }); })) : vacio('Sin notificaciones.');
    var idd = 'u-' + u.id;
    var el = h('details', { class: 'item', 'data-id': idd },
      h('summary', null, h('div', { class: 'item-cab' },
        h('div', null, h('div', { class: 'item-titulo', text: u.nombre }), h('div', { class: 'item-sub', text: u.id + ' · ' + u.correo })),
        pildora(u.tipoNombre, 'tipo'))),
      h('div', { class: 'item-datos' },
        dato(u.detalleEtiqueta + ': ', u.detalle), u.semestre ? dato('Semestre: ', String(u.semestre)) : null),
      h('div', { class: 'bloque' }, h('h3', { text: 'Certificaciones' }), certs),
      h('div', { class: 'bloque' }, h('h3', { text: 'Notificaciones recibidas' }), notis),
      h('div', { class: 'acciones' }, h('button', { type: 'button', class: 'boton boton-secundario boton-pequeno',
        text: 'Certificar a este usuario', onclick: function () {
          dialogoFormulario('Certificar a ' + u.nombre, [
            { name: 'tipoEquipo', label: 'Tipo de equipo', tipo: 'select', opciones: tiposEquipo },
            [{ name: 'emision', label: 'Emisión', tipo: 'date', valor: d.fechaSistema },
              { name: 'vencimiento', label: 'Vencimiento', tipo: 'date', valor: fechaMasAnios(d.fechaSistema, 1) }]
          ], 'Certificar', function (v) {
            return api('POST', '/api/usuarios/' + encodeURIComponent(u.id) + '/certificaciones', v);
          });
        } })));
    el.open = !!abiertos[idd];
    el.addEventListener('toggle', function () { abiertos[idd] = el.open; });
    return el;
  }

  function vistaEquipos() {
    return obtener('/api/equipos').then(function (d) {
      var tarjetas = d.equipos.map(function (e) {
        return h('div', { class: 'item' },
          h('div', { class: 'item-cab' },
            h('div', null, h('div', { class: 'item-titulo', text: e.codigo + '  ' + e.nombre }),
              h('div', { class: 'item-sub', text: e.tipoNombre + ' · ' + e.laboratorio })),
            pildoraEstado(e.estado, e.estadoNombre)),
          h('div', { class: 'item-datos' }, dato('Riesgo: ', e.riesgo.toLowerCase()),
            e.caracteristicas ? dato('Características: ', e.caracteristicas) : null),
          h('div', { class: 'acciones' }, h('button', { type: 'button', class: 'boton boton-peligro boton-pequeno',
            text: 'Marcar fuera de servicio', onclick: function () {
              confirmar('¿Marcar ' + e.codigo + ' fuera de servicio?',
                'Se avisará a quienes tengan reservas futuras de este equipo.', 'Marcar fuera de servicio',
                enviar('POST', '/api/equipos/' + encodeURIComponent(e.codigo) + '/fuera-de-servicio'));
            } })));
      });
      var form = formulario('nuevo-equipo', [
        { name: 'tipo', label: 'Tipo', tipo: 'select', opciones: d.tiposEquipo.map(function (t) { return [t.codigo, t.nombre]; }) },
        { name: 'nombre', label: 'Nombre', max: 100 },
        [{ name: 'riesgo', label: 'Riesgo', tipo: 'select', valor: 'MEDIO', opciones: [['BAJO', 'Bajo'], ['MEDIO', 'Medio'], ['ALTO', 'Alto']] },
          { name: 'laboratorio', label: 'Laboratorio', tipo: 'select', opciones: d.laboratorios.map(function (l) { return [l.codigo, l.codigo]; }) }],
        { name: 'dato1', label: 'Tecnología (FDM, SLA)', max: 100 },
        { name: 'dato2', label: 'Material soportado', max: 200 }
      ], 'Registrar equipo', function (v) { return api('POST', '/api/equipos', v); });
      var ayudas = {
        IMPRESORA_3D: ['Tecnología (FDM, SLA)', 'Material soportado', 'text', null],
        CORTADORA_LASER: ['Potencia en watts (número entero)', 'Área de trabajo', 'number', '1'],
        ESTACION_ELECTRONICA: ['Voltaje máximo (por ejemplo 24.0)', 'Herramientas', 'text', null]
      };
      var ajustar = function () {
        var a = ayudas[form.elements.tipo.value];
        form.querySelector('[data-campo="dato1"] label').textContent = a[0];
        form.querySelector('[data-campo="dato2"] label').textContent = a[1];
        form.elements.dato1.type = a[2];
        form.elements.dato1.inputMode = a[2] === 'number' ? 'numeric' : (form.elements.tipo.value === 'ESTACION_ELECTRONICA' ? 'decimal' : 'text');
        if (a[3]) { form.elements.dato1.min = a[3]; form.elements.dato1.step = '1'; } else { form.elements.dato1.removeAttribute('min'); form.elements.dato1.removeAttribute('step'); }
      };
      form.elements.tipo.addEventListener('change', ajustar);
      ajustar();
      return h('div', null,
        cabecera('Equipos', 'Impresoras 3D, cortadoras láser y estaciones de electrónica.'),
        h('div', { class: 'rejilla dos' },
          h('div', { class: 'pila' }, h('h2', { class: 'subtitulo', text: 'Inventario (' + d.equipos.length + ')' }), tarjetas),
          desplegable('f-equipo', 'Nuevo equipo', form, false)));
    });
  }

  function vistaReservas() {
    return obtener('/api/reservas').then(function (d) {
      var form = formulario('nueva-reserva', [
        { name: 'usuario', label: 'Usuario', tipo: 'select', opciones: opcionesUsuario(d.usuarios) },
        { name: 'equipo', label: 'Equipo', tipo: 'select', opciones: opcionesEquipo(d.equipos) },
        { name: 'fecha', label: 'Fecha', tipo: 'date', valor: d.fechaSugerida },
        [{ name: 'inicio', label: 'Desde', tipo: 'time', valor: '10:00' }, { name: 'fin', label: 'Hasta', tipo: 'time', valor: '12:00' }],
        { name: 'proposito', label: 'Propósito', max: 200, ph: 'Para qué usará el equipo' }
      ], 'Crear reserva', function (v) { return api('POST', '/api/reservas', v); });

      var tarjetas = d.reservas.length ? d.reservas.map(tarjetaReserva) : [vacio('Todavía no hay reservas.')];

      var criterio = h('select', { id: 'criterio', 'aria-label': 'Criterio de la lista de espera' },
        h('option', { value: 'FIFO', text: 'Orden de llegada' }), h('option', { value: 'PROFESORES_PRIMERO', text: 'Profesores primero' }));
      criterio.value = d.estrategia;
      criterio.addEventListener('change', function () {
        api('PUT', '/api/espera/estrategia', { estrategia: criterio.value }).then(function (r) { if (!r.ok) { resultado(r); } else { toast('Criterio actualizado.'); } });
      });
      var espera = d.espera.length ? d.espera.map(function (s) {
        return h('div', { class: 'fila-lab' }, h('div', null,
          h('div', { class: 'item-titulo', text: s.id + '  ' + s.usuario }),
          h('div', { class: 'item-sub', text: s.equipoCodigo + ' · ' + s.fecha + '  ' + horario(s.inicio, s.fin) })), pildora(s.tipoUsuario, 'tipo'));
      }) : [vacio('Nadie está esperando una franja.')];

      return h('div', null,
        cabecera('Reservas', 'Crear y cancelar reservas, registrar el uso del equipo y ver la lista de espera.'),
        h('div', { class: 'rejilla dos' },
          h('div', { class: 'pila' },
            h('h2', { class: 'subtitulo', text: 'Reservas registradas (' + d.reservas.length + ')' }), tarjetas,
            panel('Lista de espera', h('p', { class: 'nota', text: 'Al liberarse una franja se avisa según el criterio elegido.' }),
              h('div', { class: 'campo' }, criterio), espera)),
          desplegable('f-reserva', 'Nueva reserva', form, true)));
    });
  }
  function tarjetaReserva(r) {
    var sesion = r.sesion;
    var acciones = h('div', { class: 'acciones' },
      h('button', { type: 'button', class: 'boton boton-peligro boton-pequeno', text: 'Cancelar', onclick: function () {
        confirmar('¿Cancelar la reserva ' + r.id + '?', 'Si alguien espera esa franja, se le avisará.', 'Cancelar reserva',
          enviar('POST', '/api/reservas/' + encodeURIComponent(r.id) + '/cancelar'));
      } }),
      h('button', { type: 'button', class: 'boton boton-secundario boton-pequeno', text: 'Abrir sesión', onclick: function () {
        dialogoFormulario('Abrir sesión de uso de ' + r.id, [{ name: 'horaInicio', label: 'Hora real de inicio', tipo: 'time', valor: r.inicio }],
          'Abrir sesión', function (v) { return api('POST', '/api/reservas/' + encodeURIComponent(r.id) + '/sesion/abrir', v); });
      } }),
      h('button', { type: 'button', class: 'boton boton-secundario boton-pequeno', text: 'Cerrar sesión', onclick: function () {
        dialogoFormulario('Cerrar sesión de uso de ' + r.id, [
          { name: 'horaFin', label: 'Hora real de fin', tipo: 'time', valor: r.fin },
          { name: 'consumible', label: 'Consumible usado (opcional)', max: 200, req: false },
          { name: 'incidente', label: 'Incidente (opcional)', max: 200, req: false },
          { name: 'observaciones', label: 'Observaciones', max: 200, req: false }
        ], 'Cerrar sesión', function (v) { return api('POST', '/api/reservas/' + encodeURIComponent(r.id) + '/sesion/cerrar', v); });
      } }));
    return h('div', { class: 'item' },
      h('div', { class: 'item-cab' },
        h('div', null, h('div', { class: 'item-titulo', text: r.id + '  ·  ' + r.equipoCodigo }),
          h('div', { class: 'item-sub', text: r.usuario })),
        pildoraEstado(r.estado, r.estado === 'CONFIRMADA' ? 'Confirmada' : 'Cancelada')),
      h('div', { class: 'item-datos' },
        dato('Fecha: ', r.fecha + '  ' + horario(r.inicio, r.fin)),
        dato('Propósito: ', r.proposito),
        dato('Uso: ', r.uso),
        sesion && sesion.consumibles.length ? dato('Consumibles: ', sesion.consumibles.join(', ')) : null,
        sesion && sesion.incidentes.length ? dato('Incidentes: ', sesion.incidentes.join('; ')) : null),
      acciones);
  }

  function vistaMantenimiento() {
    return obtener('/api/mantenimiento').then(function (d) {
      var form = formulario('nuevo-ticket', [
        { name: 'equipo', label: 'Equipo', tipo: 'select', opciones: opcionesEquipo(d.equipos) },
        { name: 'prioridad', label: 'Prioridad', tipo: 'select', valor: 'MEDIA', opciones: [['BAJA', 'Baja'], ['MEDIA', 'Media'], ['ALTA', 'Alta']] },
        { name: 'descripcion', label: 'Descripción de la falla', max: 200 },
        [{ name: 'fecha', label: 'Fecha', tipo: 'date', valor: d.fechaSistema }, { name: 'hora', label: 'Hora', tipo: 'time', valor: '08:00' }]
      ], 'Abrir ticket', function (v) { return api('POST', '/api/tickets', v); });
      var tarjetas = d.tickets.length ? d.tickets.map(function (t) { return tarjetaTicket(t, d); }) : [vacio('No hay tickets de mantenimiento.')];
      return h('div', null,
        cabecera('Mantenimiento', 'Al abrir un ticket el equipo queda bloqueado y se avisa a quienes lo tenían reservado.'),
        h('div', { class: 'rejilla dos' },
          h('div', { class: 'pila' }, h('h2', { class: 'subtitulo', text: 'Tickets (' + d.tickets.length + ')' }), tarjetas),
          desplegable('f-ticket', 'Nuevo ticket', form, true)));
    });
  }
  function tarjetaTicket(t, d) {
    var idd = 'tk-' + t.id;
    var historial = h('ol', { class: 'detalle-lista' }, t.historial.map(function (c) { return h('li', { text: c.descripcion }); }));
    var det = h('details', { 'data-id': idd }, h('summary', { class: 'etiqueta', text: 'Historial del ticket' }), historial);
    det.open = !!abiertos[idd];
    det.addEventListener('toggle', function () { abiertos[idd] = det.open; });
    var selTec = h('select', { 'aria-label': 'Técnico' }, d.tecnicos.map(function (x) { return h('option', { value: x.id, text: x.id + '  ' + x.nombre }); }));
    return h('div', { class: 'item' },
      h('div', { class: 'item-cab' },
        h('div', null, h('div', { class: 'item-titulo', text: t.id + '  ·  ' + t.equipoCodigo }), h('div', { class: 'item-sub', text: t.descripcion })),
        h('div', null, pildoraEstado(t.prioridad, t.prioridad.charAt(0) + t.prioridad.slice(1).toLowerCase()), ' ',
          pildoraEstado(t.cerrado ? 'cerrado' : 'abierto', t.cerrado ? 'Cerrado' : 'Abierto'))),
      h('div', { class: 'item-datos' },
        dato('Técnico: ', t.tecnico || 'Sin asignar'), dato('Programado: ', t.fecha + ' ' + t.hora),
        t.accion ? dato('Acción: ', t.accion) : null),
      det,
      h('div', { class: 'acciones' },
        selTec,
        h('button', { type: 'button', class: 'boton boton-secundario boton-pequeno', text: 'Asignar', onclick: function () {
          api('POST', '/api/tickets/' + encodeURIComponent(t.id) + '/asignar', { tecnico: selTec.value }).then(function (r) { resultado(r); return refrescar(); });
        } }),
        h('button', { type: 'button', class: 'boton boton-secundario boton-pequeno', text: 'Cerrar ticket', onclick: function () {
          dialogoFormulario('Cerrar el ticket ' + t.id, [
            { name: 'accion', label: 'Acción realizada', max: 280 },
            { name: 'reparado', label: '¿Quedó reparado?', tipo: 'checkbox', valor: true }
          ], 'Cerrar ticket', function (v) { return api('POST', '/api/tickets/' + encodeURIComponent(t.id) + '/cerrar', v); });
        } })));
  }

  function vistaIndicadores() {
    return obtener('/api/indicadores').then(function (d) {
      var max = Math.max.apply(null, d.horasPorTipo.map(function (x) { return x.horas; }).concat([1]));
      var barras = h('div', { class: 'barras' }, d.horasPorTipo.map(function (x) {
        var relleno = h('div', { class: 'barra-relleno' });
        relleno.style.width = (x.horas / max * 100) + '%';
        return h('div', { class: 'barra-dato' },
          h('div', { class: 'barra-dato-cab' }, h('span', { text: x.nombre }), h('strong', { text: x.horas + ' h' })),
          h('div', { class: 'barra-pista', role: 'img', 'aria-label': x.nombre + ': ' + x.horas + ' horas' }, relleno));
      }));
      var carga = d.cargaMantenimiento.length ? d.cargaMantenimiento.map(function (c) { return h('div', { text: c.tecnico + ': ' + c.cantidad }); }) : [vacio('Ninguno abierto')];
      var resumen = h('div', { class: 'pila' },
        h('div', { class: 'cifras' },
          h('div', { class: 'cifra' }, h('span', { class: 'etiqueta', text: 'Incidentes reportados' }), h('span', { class: 'cifra-valor', text: String(d.incidentes) })),
          h('div', { class: 'cifra' }, h('span', { class: 'etiqueta', text: 'Reservas canceladas' }), h('span', { class: 'cifra-valor', text: d.tasaCancelacion + ' %' }))),
        h('div', null, h('span', { class: 'etiqueta', text: 'Tickets abiertos por técnico' }), carga));
      var porVencer = d.porVencer.length ? d.porVencer.map(function (c) {
        return h('div', { class: 'fila-lab' }, h('div', null, h('div', { class: 'item-titulo', text: c.id + '  ' + c.usuario }),
          h('div', { class: 'item-sub', text: c.tipoEquipoNombre + ', vence ' + c.vencimiento })));
      }) : [vacio('Ninguna certificación vence en los próximos 30 días.')];
      var fuera = d.fueraDeServicio.length ? d.fueraDeServicio.map(function (e) {
        return h('div', { class: 'fila-lab' }, h('div', null, h('div', { class: 'item-titulo', text: e.codigo + '  ' + e.nombre })), pildoraEstado(e.estado, e.estadoNombre));
      }) : [vacio('Todos los equipos están operativos.')];

      var resultadoDisp = h('div', { id: 'res-disp', 'aria-live': 'polite' });
      var pintarDisp = function () {
        vaciar(resultadoDisp);
        if (!ultimaDisponibilidad) { return; }
        var r = ultimaDisponibilidad;
        resultadoDisp.append(h('p', { class: 'nota', text: 'Equipos libres en ' + r.laboratorio + ':' }),
          r.libres.length ? r.libres.map(function (e) { return h('div', { class: 'fila-lab' }, h('div', { class: 'item-titulo', text: e.codigo + '  ' + e.nombre })); })
            : vacio('Ningún equipo libre en esa franja.'));
      };
      var formDisp = h('form', { 'data-id': 'disponibilidad' }, camposEl([
        { name: 'laboratorio', label: 'Laboratorio', tipo: 'select', opciones: d.laboratorios.map(function (l) { return [l.codigo, l.codigo + '  ' + l.nombre]; }) },
        { name: 'fecha', label: 'Fecha', tipo: 'date', valor: d.fechaSistema },
        [{ name: 'desde', label: 'Desde', tipo: 'time', valor: '10:00' }, { name: 'hasta', label: 'Hasta', tipo: 'time', valor: '12:00' }]
      ]), h('button', { type: 'submit', class: 'boton boton-secundario boton-ancho', text: 'Consultar' }));
      formDisp.addEventListener('submit', function (ev) {
        ev.preventDefault();
        if (!formDisp.reportValidity()) { return; }
        var v = leerValores(formDisp);
        var q = 'laboratorio=' + encodeURIComponent(v.laboratorio) + '&fecha=' + encodeURIComponent(v.fecha) +
          '&desde=' + encodeURIComponent(v.desde) + '&hasta=' + encodeURIComponent(v.hasta);
        api('GET', '/api/disponibilidad?' + q).then(function (r) {
          if (!r.ok) { resultado(r); return; }
          limpiarAvisos(); ultimaDisponibilidad = r.data; pintarDisp();
        });
      });
      pintarDisp();

      return h('div', null,
        cabecera('Indicadores', 'Uso de los equipos, incidentes, cancelaciones y consultas rápidas.'),
        h('div', { class: 'pila' },
          h('div', { class: 'rejilla dos' }, panel('Horas de uso por tipo de equipo', barras), panel('Resumen', resumen)),
          h('div', { class: 'rejilla dos' }, panel('Certificaciones que vencen en 30 días', porVencer), panel('Equipos fuera de servicio', fuera)),
          panel('Disponibilidad por laboratorio y franja', formDisp, resultadoDisp)));
    });
  }

  var VISTAS = { inicio: vistaInicio, usuarios: vistaUsuarios, equipos: vistaEquipos, reservas: vistaReservas,
    mantenimiento: vistaMantenimiento, indicadores: vistaIndicadores };

  // ============================================================ navegación

  function pintarTabs() {
    var nav = document.getElementById('tabs');
    vaciar(nav);
    SECCIONES.forEach(function (s) {
      var a = h('a', { class: 'tab', href: '#' + s[0], text: s[1] });
      if (s[0] === seccionActual) { a.setAttribute('aria-current', 'page'); }
      nav.append(a);
    });
    var activa = nav.querySelector('[aria-current]');
    if (activa && activa.scrollIntoView) { activa.scrollIntoView({ inline: 'center', block: 'nearest' }); }
  }

  function pantallaError(err) {
    var r = err && err.respuesta;
    if (r && r.estado === 503 && r.data.tipo === 'Iniciando') {
      return h('div', { class: 'conectando' }, h('div', { class: 'giro' }), h('h1', { class: 'titulo', text: 'Conectando con la base de datos…' }),
        h('p', { class: 'descripcion', text: 'El servidor se está despertando. Esto tarda unos segundos la primera vez.' }));
    }
    return h('div', { class: 'conectando' }, h('h1', { class: 'titulo', text: 'No se pudo cargar' }),
      crearAlerta(r ? r.data.tipo : 'SinConexion', r ? (r.data.mensaje || 'Error') : 'No hay conexión con el servidor.'),
      h('button', { type: 'button', class: 'boton', text: 'Reintentar', onclick: function () { refrescar(); } }));
  }

  // Vuelve a pedir los datos de la sección actual y la dibuja sin perder lo escrito ni la posición.
  function refrescar(silencioso) {
    var token = ++cargandoSeccion;
    var seccion = seccionActual;
    var y = window.scrollY;
    return Promise.all([VISTAS[seccion]().catch(function (e) { return pantallaError(e); }), cargarEstado()]).then(function (res) {
      if (token !== cargandoSeccion || seccion !== seccionActual) { return; }
      vaciar(vista);
      vista.append(res[0]);
      restaurarBorradores();
      if (silencioso) { window.scrollTo(0, y); }
      var r = res[0].classList && res[0].classList.contains('conectando');
      if (r && !reintentoPendiente) {
        reintentoPendiente = setTimeout(function () { reintentoPendiente = null; refrescar(true); }, 3000);
      }
    });
  }
  var reintentoPendiente = null;

  function mostrar() {
    var hash = (location.hash || '#inicio').slice(1);
    seccionActual = VISTAS[hash] ? hash : 'inicio';
    pintarTabs();
    limpiarAvisos();
    document.title = 'MakerLab EIA · ' + SECCIONES.filter(function (s) { return s[0] === seccionActual; })[0][1];
    vaciar(vista);
    vista.append(h('div', { class: 'conectando' }, h('div', { class: 'giro' })));
    refrescar().then(function () { window.scrollTo(0, 0); vista.focus({ preventScroll: true }); });
  }
  window.addEventListener('hashchange', mostrar);

  // Estado del servidor (modo de datos, fecha del sistema).
  function cargarEstado() {
    return api('GET', '/api/estado').then(function (r) {
      if (r.ok) {
        estadoServidor = r.data;
        estadoDatos.textContent = r.data.baseDeDatos ? 'PostgreSQL' : 'Solo memoria';
        estadoDatos.className = 'estado-datos ' + (r.data.baseDeDatos ? 'ok' : 'mal');
        estadoDatos.title = r.data.mensaje || '';
        document.getElementById('pie-fecha').textContent = 'Fecha del sistema fija en ' + r.data.fechaSistema +
          ' para que la demostración dé siempre el mismo resultado.';
        document.getElementById('btn-demo').hidden = !r.data.demoHabilitado;
      } else {
        estadoDatos.textContent = r.estado === 503 ? 'Conectando…' : 'Sin conexión';
        estadoDatos.className = 'estado-datos' + (r.estado === 503 ? '' : ' mal');
      }
    });
  }

  // ======================================================== modo demostración

  function leerToken() { try { return sessionStorage.getItem('makerlab-demo') || ''; } catch (e) { return ''; } }
  function guardarToken(t) { try { sessionStorage.setItem('makerlab-demo', t); } catch (e) { /* sin almacenamiento */ } }

  document.getElementById('btn-demo').addEventListener('click', function () {
    dialogoFormulario('Modo demostración', [
      { name: 'codigo', label: 'Código de demostración', tipo: 'password', valor: leerToken(), autocomplete: 'off' },
      { name: 'escenario', label: 'Incluir el recorrido guiado (reservas, rechazos, tickets e indicadores ya llenos)', tipo: 'checkbox', valor: true }
    ], 'Restaurar datos', function (v) {
      guardarToken(v.codigo);
      return api('POST', '/api/demo/restaurar', { escenario: v.escenario }, { 'X-Demo-Token': v.codigo });
    }, { peligro: true, texto: 'Borra todo lo que se haya creado y vuelve a los datos de demostración de MakerLab. Es para dejar la app lista entre visitantes.' });
  });

  // ================================================================ arranque

  setInterval(function () {
    var a = document.activeElement;
    var escribiendo = a && /^(INPUT|SELECT|TEXTAREA)$/.test(a.tagName) && vista.contains(a);
    if (!document.hidden && !dlg.open && !escribiendo) { refrescar(true); }
  }, 20000);
  document.addEventListener('visibilitychange', function () { if (!document.hidden && !dlg.open) { refrescar(true); } });

  mostrar();
})();
