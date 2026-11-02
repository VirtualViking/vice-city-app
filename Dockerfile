# Imagen del backend Ktor para Render.
# Etapa 1: compila el servidor con Gradle
FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app
COPY . .
# Memoria acotada para Gradle dentro del contenedor de compilación
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1536m -Dorg.gradle.daemon=false"
RUN chmod +x gradlew && ./gradlew :server:installDist --no-daemon --console=plain

# Etapa 2: solo el JRE y el servidor compilado
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construccion /app/server/build/install/server /app
# El plan gratuito de Render da 512 MB de RAM
ENV JAVA_OPTS="-Xmx320m -XX:+UseSerialGC"
EXPOSE 8081
CMD ["/app/bin/server"]
