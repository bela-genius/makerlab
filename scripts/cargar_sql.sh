#!/usr/bin/env bash
# Ejecuta los scripts de /sql en orden contra la base indicada en DATABASE_URL.
# Uso: DATABASE_URL='postgresql://usuario:clave@host/base?sslmode=require' ./scripts/cargar_sql.sh
# (Opcional: la app también crea las tablas sola la primera vez si la base está vacía.)
set -euo pipefail
: "${DATABASE_URL:?Defina DATABASE_URL}"
cd "$(dirname "$0")/.."
for f in sql/tablas.sql sql/relaciones.sql; do
  echo "== $f"
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -1 -f "$f"
done
echo "Listo."
