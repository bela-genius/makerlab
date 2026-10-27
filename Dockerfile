# ---------- Construcción ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# El pom original apunta a Java 26 (JavaFX); la versión web solo necesita 21.
# Las pruebas se corren aparte con "mvn test"; aquí solo se empaqueta.
RUN mvn -B -q -DskipTests -Dmaven.compiler.release=21 package \
 && mvn -B -q dependency:copy-dependencies -DincludeScope=runtime \
      -DexcludeGroupIds=org.openjfx -DoutputDirectory=target/lib

# ---------- Ejecución ----------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 makerlab
WORKDIR /app
COPY --from=build /app/target/makerlab-2.0.jar makerlab.jar
COPY --from=build /app/target/lib lib
COPY sql sql
USER makerlab
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=70 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8" \
    SQL_DIR=/app/sql
EXPOSE 8080
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -cp 'makerlab.jar:lib/*' co.edu.eia.makerlab.web.ServidorWeb"]
