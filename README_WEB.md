# MakerLab EIA — versión web móvil
Misma lógica de la entrega (paquetes modelo, servicio, patrón, excepción, repositorio, persistencia sin cambios) con API REST y frontend móvil.
- Local: copie `.env.example` a `.env`, ejecute `scripts/run_local.sh` y abra http://localhost:8080
- Nube: ver `docs/DESPLIEGUE.md`
- Pruebas de API: `python3 scripts/smoke_test.py http://localhost:8080 <DEMO_TOKEN>` (restaura los datos demo)
- Pruebas unitarias originales: `mvn test` (no se tocaron)
