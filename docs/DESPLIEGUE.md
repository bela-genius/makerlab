# Despliegue de MakerLab EIA web (Neon + Render)

Resultado: una URL pública HTTPS (p. ej. `https://makerlab-eia.onrender.com`) para el QR de la expo.
El código de la entrega (modelo, servicio, patrón, excepción, repositorio, persistencia) no se modificó; la capa web está en `co.edu.eia.makerlab.web` y el frontend en `src/main/resources/public`.

## 1. Base de datos en Neon (gratis, sin tarjeta)
1. Crear cuenta en neon.tech → proyecto `makerlab`. **Región: AWS US East (Virginia/N. Virginia)**, la misma que Render "Virginia". `guardar()` hace ~100 sentencias por operación; con la base lejos cada escritura tarda segundos.
2. Copiar la *connection string* (`postgresql://usuario:clave@ep-xxx.../neondb?sslmode=require`). Es el secreto `DATABASE_URL`.
3. Tablas: dos opciones.
   - Automática: al primer arranque, si la base no tiene ninguna de las 18 tablas, la app ejecuta `sql/tablas.sql` y luego `sql/relaciones.sql`. Si hay tablas a medias, se detiene con un error (nunca borra nada).
   - Manual: `DATABASE_URL='...' ./scripts/cargar_sql.sh` (necesita `psql`) o pegar los scripts en el SQL Editor de Neon, en ese orden.
4. Neon duerme la base tras 5 min sin uso; despierta en ~1 s y la app reintenta la conexión sola.

## 2. Repositorio (sin secretos)
```
cd makerlab-web
git init && git add . && git status   # comprobar que NO aparece .env
git commit -m "MakerLab web" && git branch -M main
git remote add origin https://github.com/<usuario>/makerlab-web.git   # repositorio privado
git push -u origin main
```
`.gitignore` y `.dockerignore` excluyen `.env`. Las claves solo viven en el panel del proveedor.

## 3. Render
1. Render → New → Blueprint → elegir el repositorio (lee `render.yaml`, plan Free, Docker, región Virginia).
2. Cuando pida los secretos: `DATABASE_URL` (la de Neon) y `DEMO_TOKEN` (una clave que usted elija).
3. Esperar el build (~5 min). Comprobar `https://<servicio>.onrender.com/api/salud` → `{"estado":"ok",...}`.

## 4. QR
`pip install qrcode[pil]` y `python3 scripts/generar_qr.py https://<servicio>.onrender.com` → `qr.png`.

## 5. Lista para el día de la expo
- Render Free se duerme tras 15 min sin tráfico y tarda ~1 min en despertar: abra la URL desde su celular 5 min antes, y mientras dure la expo haga un ping cada ~10 min (UptimeRobot gratis sobre `/api/salud`). Ojo: el plan gratis tiene 750 h/mes y ancho de banda limitado.
- Antes de abrir: pulsar **Modo demostración** (pie de página, pide el `DEMO_TOKEN`) para volver a los datos de `DatosDemo`. Hágalo también si el público dejó la base desordenada. El botón borra todo lo escrito por los visitantes.
- Una sola instancia (no escalar): el estado vive en memoria y se guarda en la base; dos instancias se pisarían.

## 6. Cómo se resolvió `guardar()` (reescribe toda la BD por operación)
Todas las operaciones de escritura pasan por un único candado de lectura/escritura justo: una escritura a la vez, lecturas en paralelo entre escrituras. Cada escritura aplica el cambio en memoria con las reglas originales y luego llama a `guardar()` (transacción única: o queda todo o nada). Si el guardado falla, la app recarga desde la base y responde 503 sin dejar el cambio a medias. Se probó con 40 peticiones simultáneas a la misma franja: se crea una reserva y las demás reciben el mensaje exacto de cruce.
Alternativas si hubiera mucho tráfico: (a) guardado diferido por lotes (más rápido, pero un cierre del servidor puede perder los últimos cambios), (b) reemplazar `guardar()` por escrituras incrementales (obliga a tocar `persistencia`, que se pidió no modificar), (c) candado consultivo de PostgreSQL + recargar por petición para varias instancias (más lento). Para una expo, el candado único es lo más simple y seguro.

## 7. Otros proveedores (datos verificados al preparar esto)
- Render Free: sin tarjeta, se duerme a los 15 min, "no para producción".
- Neon Free: permanente, sin tarjeta, 1 GB, duerme a los 5 min.
- Fly.io: ya no tiene plan gratis permanente (prueba, probablemente con tarjeta).
- Railway: $5 de prueba única hasta 30 días, luego ~$1/mes de crédito.
Revise los planes vigentes antes de la expo: cambian seguido.

## 8. Problemas frecuentes
- 503 "Iniciando": la app aún conecta con la base (primer minuto).
- Arranque falla con "no tiene un formato válido": la clave tiene símbolos (`@ # / ?`); codifíquelos con `%` o regenere la clave.
- Acentos con "?" en logs: ya se fuerza UTF-8 en `JAVA_OPTS`.
- Variables opcionales: `ALLOW_MEMORY_FALLBACK=true` (sin BD, solo para pruebas), `DB_CONNECT_RETRIES`, `SQL_DIR`.
