# MakerLab EIA web — reglas para Claude Code

- Proyecto Java (Maven). Paquetes `modelo`, `servicio`, `patron`, `excepcion`, `repositorio`, `persistencia` y `vista`: NO modificarlos. La capa web está en `co.edu.eia.makerlab.web` y el frontend en `src/main/resources/public`.
- Las 38 pruebas originales deben pasar con `mvn test`. No tocar `pom.xml` salvo que sea imprescindible (el Dockerfile ya compila con `-Dmaven.compiler.release=21`).
- Nunca imprimir, commitear ni subir contraseñas, `DATABASE_URL`, `DEMO_TOKEN` ni `.env`. Los secretos solo van en el panel de Render/Neon. Dejar `.env.example`.
- La app lee `DATABASE_URL` (o `DB_URL/DB_USER/DB_PASSWORD`, o `.env`). Neon ya entrega `DATABASE_URL`, así que no hace falta cambiar código para conectarse.
- Esta app NO usa Node: no agregar `package.json` ni `node_modules`. Si `neon init` los crea, borrarlos.
- Región de Neon: AWS US East (Virginia), igual que Render "virginia".
- Guía completa: `docs/DESPLIEGUE.md`.
