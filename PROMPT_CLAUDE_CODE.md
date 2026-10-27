# Prompt para pegar en Claude Code (abrir Claude Code dentro de esta carpeta)

Lee CLAUDE.md y docs/DESPLIEGUE.md. Quiero desplegar esta app para mostrarla en una expo con un QR. Hazlo paso a paso, avisándome cuando necesites que yo haga algo en el navegador (inicios de sesión, pegar secretos).

1. Verificación local: corre `mvn test` y confirma que pasan las 38 pruebas. Si falla algo, dime qué antes de cambiar nada.
2. Neon: instala la CLI (`npm i -g neon@latest`, o `npx neon@latest`). Si abre un navegador para iniciar sesión, dime que lo termine yo; si falla, pídeme `neon login` y reintenta. Luego, en esta carpeta:
   `neon init -y --org-id org-billowing-salad-51266577 --project-id shy-paper-39149869 --branch production --services postgres`
   y `neon deploy` y `neon env pull`. Esto es una app Java: borra `package.json`, `package-lock.json`, `node_modules` y `neon.ts` si quedan, y no cambies código para Neon (la app ya lee `DATABASE_URL`).
3. Prueba contra Neon: arranca la app en local con la `DATABASE_URL` que dejó Neon (sin imprimirla) usando `scripts/run_local.sh`, y corre `python scripts/smoke_test.py http://localhost:8080 <DEMO_TOKEN que inventes para la prueba>`. Todas las comprobaciones deben pasar. Muéstrame la salida (sin secretos).
4. Git: `git init`, confirma con `git status` que NO aparece `.env` ni ningún secreto, haz commit y crea un repositorio PRIVADO en GitHub (con `gh` si está disponible; si no, dame los comandos) y súbelo.
5. Render: guíame para crear el Blueprint desde ese repositorio (`render.yaml`). Dime exactamente qué pegar en `DATABASE_URL` y `DEMO_TOKEN` en el panel de Render (yo los pego, tú no los escribas en archivos).
6. Verificación final: cuando tenga la URL pública, comprueba `/api/salud`, corre el smoke test contra la URL pública y genera el QR con `python scripts/generar_qr.py <URL>`.
7. Reporte final: qué cambió, la salida que prueba cada paso, qué falló y la URL pública. No me digas que algo funciona si no lo probaste.
