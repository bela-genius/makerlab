#!/usr/bin/env bash
# Compila y corre la versión web en el portátil (lee .env). Requiere JDK 21+ y Maven.
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B -q -DskipTests -Dmaven.compiler.release=21 package
mvn -B -q dependency:copy-dependencies -DincludeScope=runtime -DexcludeGroupIds=org.openjfx -DoutputDirectory=target/lib
exec java -Dfile.encoding=UTF-8 -cp 'target/makerlab-2.0.jar:target/lib/*' co.edu.eia.makerlab.web.ServidorWeb
