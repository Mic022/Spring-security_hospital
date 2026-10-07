# Imagen del backend. Se construye sola con: docker compose up --build

# Etapa 1: compila el jar con el Gradle Wrapper del proyecto (no hace falta tener Java instalado).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Descarga primero las dependencias: Docker las guarda en caché si solo cambia el código.
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# Etapa 2: imagen final, solo con el JRE y el jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/usermanagement-0.0.1-SNAPSHOT.jar app.jar
# La aplicación no se ejecuta como root: si alguien la compromete, tiene menos permisos en el contenedor.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
USER app
EXPOSE 8050
ENTRYPOINT ["java", "-jar", "app.jar"]
