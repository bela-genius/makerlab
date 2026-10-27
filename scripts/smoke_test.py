#!/usr/bin/env python3
"""
Prueba de humo de la API de MakerLab EIA contra un servidor en marcha.

    python3 scripts/smoke_test.py http://localhost:8080 [CODIGO_DEMO]

Si se entrega el código de demostración (DEMO_TOKEN), la prueba restaura los
datos de DatosDemo con el recorrido guiado y verifica los mismos resultados
del README (indicadores, rechazos con su mensaje exacto) y la concurrencia:
40 personas pidiendo a la vez la misma franja deben producir una sola reserva.
ATENCIÓN: restaura la base, así que no la corra contra datos que quiera conservar.
Solo usa la librería estándar de Python.
"""
import json
import sys
import threading
import urllib.error
import urllib.request
from collections import Counter

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
TOKEN = sys.argv[2] if len(sys.argv) > 2 else None

fallos = []
total = 0


def llamar(metodo, ruta, cuerpo=None, cabeceras=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
    if datos is not None:
        req.add_header("Content-Type", "application/json")
    for k, v in (cabeceras or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read().decode() or "null")
    except urllib.error.HTTPError as e:
        texto = e.read().decode()
        try:
            return e.code, json.loads(texto)
        except ValueError:
            return e.code, {"crudo": texto}


def revisar(nombre, condicion, detalle=""):
    global total
    total += 1
    if condicion:
        print("  ok   ", nombre)
    else:
        print("  FALLA", nombre, detalle)
        fallos.append(nombre)


def seccion(titulo):
    print("\n== " + titulo)


# ------------------------------------------------------------------ lecturas
seccion("Las 6 secciones responden")
estado, est = llamar("GET", "/api/estado")
revisar("estado 200 y modo PostgreSQL", estado == 200 and est.get("baseDeDatos") is True, est)
for ruta in ["/api/inicio", "/api/usuarios", "/api/equipos", "/api/reservas", "/api/mantenimiento",
             "/api/indicadores"]:
    s, _ = llamar("GET", ruta)
    revisar("GET " + ruta, s == 200)

if TOKEN:
    seccion("Modo demostración protegido")
    s, r = llamar("POST", "/api/demo/restaurar", {"escenario": True})
    revisar("sin token: 403", s == 403, r)
    s, r = llamar("POST", "/api/demo/restaurar", {"escenario": True}, {"X-Demo-Token": TOKEN + "x"})
    revisar("token incorrecto: 403", s == 403, r)
    s, r = llamar("POST", "/api/demo/restaurar", {"escenario": True}, {"X-Demo-Token": TOKEN})
    revisar("token correcto: 200", s == 200, r)

    seccion("Indicadores del README tras el recorrido guiado")
    _, ind = llamar("GET", "/api/indicadores")
    horas = {h["tipo"]: h["horas"] for h in ind["horasPorTipo"]}
    revisar("horas de uso: impresora 1.75, cortadora 0, estación 1.5",
            horas == {"IMPRESORA_3D": 1.75, "CORTADORA_LASER": 0, "ESTACION_ELECTRONICA": 1.5}, horas)
    revisar("incidentes = 1", ind["incidentes"] == 1, ind["incidentes"])
    revisar("tasa de cancelación = 33.3", ind["tasaCancelacion"] == 33.3, ind["tasaCancelacion"])
    revisar("1 ticket abierto sin asignar", ind["cargaMantenimiento"] == [{"tecnico": "Sin asignar", "cantidad": 1}],
            ind["cargaMantenimiento"])

    seccion("Rechazos con el mensaje exacto de las excepciones")
    reserva = lambda u, e, f, i, fin, p="Prueba": llamar(
        "POST", "/api/reservas", {"usuario": u, "equipo": e, "fecha": f, "inicio": i, "fin": fin, "proposito": p})
    s, r = reserva("U-010", "EQ-101", "2026-09-15", "10:00", "12:00")
    revisar("reserva válida: 201 y id R-239", s == 201 and r.get("id") == "R-239", r)
    s, r = reserva("U-011", "EQ-101", "2026-09-15", "10:00", "12:00")
    revisar("cruce de horario: 409 y lista de espera", s == 409 and r["tipo"] == "CruceHorarioException"
            and r["mensaje"] == "La franja ya esta reservada. Quedo en lista de espera.", r)
    s, r = reserva("U-011", "EQ-101", "2026-09-15", "10:00", "12:00")
    revisar("cruce repetido", s == 409 and r["mensaje"] == "La franja ya esta reservada. Ya estaba en lista de espera.", r)
    s, r = reserva("U-013", "EQ-101", "2026-09-16", "10:00", "12:00")
    revisar("certificación inválida: 422", s == 422 and r["tipo"] == "CertificacionInvalidaException"
            and r["mensaje"] == "Isabella Navarro no tiene certificacion vigente de IMPRESORA_3D.", r)
    s, r = reserva("U-012", "EQ-201", "2026-09-16", "10:00", "12:00")
    revisar("equipo no disponible: 422", s == 422 and r["tipo"] == "EquipoNoDisponibleException"
            and r["mensaje"] == "El equipo EQ-201 esta EN_MANTENIMIENTO.", r)  # TK-002 lo dejó en mantenimiento
    s, r = reserva("U-010", "EQ-101", "2026-09-16", "18:00", "20:00")
    revisar("fuera de horario: 422", s == 422 and "esta fuera del horario de LAB-01 (07:00-19:00)" in r["mensaje"], r)
    s, r = reserva("U-010", "EQ-101", "2026-09-16", "1000", "12:00")
    revisar("hora mal escrita: 400", s == 400 and r["tipo"] == "DatosInvalidos", r)
    s, r = reserva("U-999", "EQ-101", "2026-09-16", "10:00", "12:00")
    revisar("usuario inexistente: 404", s == 404 and r["mensaje"] == "No existe el usuario U-999.", r)

    seccion("Cancelación avisa a la lista de espera")
    llamar("PUT", "/api/espera/estrategia", {"estrategia": "FIFO"})
    s, r = llamar("POST", "/api/reservas/R-239/cancelar")
    revisar("cancelar R-239 avisa a Maria Camila", s == 200 and "Maria Camila Ricardo" in r["mensaje"], r)
    s, r = llamar("POST", "/api/reservas/R-239/cancelar")
    revisar("cancelar otra vez: mensaje del sistema", s == 422 and r["mensaje"] == "La reserva R-239 ya estaba cancelada.", r)

    seccion("Mantenimiento: reglas del sistema")
    s, r = llamar("POST", "/api/tickets/TK-001/cerrar", {"accion": "otra vez", "reparado": True})
    revisar("cerrar ticket ya cerrado", s == 422 and r["mensaje"] == "El ticket TK-001 ya esta cerrado.", r)
    s, r = llamar("POST", "/api/tickets/TK-002/cerrar", {"accion": "x", "reparado": True})
    revisar("cerrar sin técnico no deja la acción a medias", s == 422 and "falta el tecnico" in r["mensaje"], r)
    _, m = llamar("GET", "/api/mantenimiento")
    tk2 = [t for t in m["tickets"] if t["id"] == "TK-002"][0]
    revisar("TK-002 sigue sin acción", tk2["accion"] is None, tk2)

    seccion("Concurrencia: 40 personas piden la misma franja a la vez")
    pedir = lambda: reserva("U-010", "EQ-101", "2026-09-20", "14:00", "16:00", "Carrera")
    resultados = []
    barrera = threading.Barrier(40)

    def hilo(i):
        barrera.wait()
        usuario = ["U-010", "U-011", "U-020"][i % 3]
        resultados.append(reserva(usuario, "EQ-101", "2026-09-20", "14:00", "16:00", "Hilo %d" % i)[0])

    hilos = [threading.Thread(target=hilo, args=(i,)) for i in range(40)]
    [h.start() for h in hilos]
    [h.join() for h in hilos]
    conteo = Counter(resultados)
    revisar("exactamente 1 reserva creada (201)", conteo[201] == 1, conteo)
    revisar("las otras 39 recibieron cruce (409)", conteo[409] == 39, conteo)
    revisar("sin errores 5xx", not any(c >= 500 for c in conteo), conteo)
    _, rs = llamar("GET", "/api/reservas")
    ocupan = [x for x in rs["reservas"] if x["fecha"] == "2026-09-20" and x["estado"] == "CONFIRMADA"]
    revisar("la lista confirma 1 sola reserva en esa franja", len(ocupan) == 1, len(ocupan))
    espera = [e["usuarioId"] for e in rs["espera"] if e["fecha"] == "2026-09-20"]
    revisar("lista de espera sin duplicados (una solicitud por persona)",
            sorted(espera) == ["U-010", "U-011", "U-020"], espera)

    seccion("Concurrencia: 30 altas de usuario en paralelo")
    codigos = []
    barrera2 = threading.Barrier(30)

    def alta(i):
        barrera2.wait()
        s, r = llamar("POST", "/api/usuarios", {"tipo": "Estudiante", "nombre": "Visitante %d" % i,
                                                 "correo": "visita%d@eia.edu.co" % i, "detalle": "Expo", "semestre": 1})
        codigos.append((s, r.get("id")))

    hilos = [threading.Thread(target=alta, args=(i,)) for i in range(30)]
    [h.start() for h in hilos]
    [h.join() for h in hilos]
    revisar("30 altas con 201", all(s == 201 for s, _ in codigos), Counter(s for s, _ in codigos))
    revisar("30 ids distintos", len({c for _, c in codigos}) == 30)
    s, r = llamar("POST", "/api/usuarios", {"tipo": "Estudiante", "nombre": "Repetido", "correo": "VISITA1@eia.edu.co",
                                             "detalle": "Expo", "semestre": 1})
    revisar("correo repetido se rechaza con mensaje claro", s == 400 and "Ya existe un usuario con el correo" in r["mensaje"], r)

    seccion("Restaurar deja todo como al inicio")
    s, r = llamar("POST", "/api/demo/restaurar", {"escenario": False}, {"X-Demo-Token": TOKEN})
    revisar("restaurar solo datos base", s == 200, r)
    _, u = llamar("GET", "/api/usuarios")
    _, rs = llamar("GET", "/api/reservas")
    revisar("6 usuarios y 0 reservas", len(u["usuarios"]) == 6 and len(rs["reservas"]) == 0,
            (len(u["usuarios"]), len(rs["reservas"])))

print("\n%d comprobaciones, %d fallas" % (total, len(fallos)))
if fallos:
    print("Fallaron:", ", ".join(fallos))
    sys.exit(1)
